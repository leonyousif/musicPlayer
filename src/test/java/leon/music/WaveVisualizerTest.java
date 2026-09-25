package leon.music;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@DisplayName("WaveVisualizer Unit and Concurrency Tests")
class WaveVisualizerTest {

    @BeforeAll
    static void setUpHeadless() {
        System.setProperty("java.awt.headless", "true");
    }

    @Test
    @DisplayName("pushSamples correctly stores audio samples and normalizes values to [-1.0, 1.0]")
    void testPushSamplesStorageAndNormalization() {
        int bufferSize = 512;
        WaveVisualizer visualizer = new WaveVisualizer(bufferSize);

        assertThat(visualizer.getBufferSize()).isEqualTo(bufferSize);

        // Initial snapshot should be clean zeros
        float[] initialSnapshot = visualizer.getSnapshot();
        assertThat(initialSnapshot).hasSize(bufferSize);
        assertThat(initialSnapshot).containsOnly(0f);

        // Push standard valid samples within [-1.0, 1.0]
        float[] validSamples = new float[]{0.15f, -0.45f, 0.85f, -0.95f, 0.0f};
        visualizer.pushSamples(validSamples);

        float[] snapshotAfterValid = visualizer.getSnapshot();
        assertThat(snapshotAfterValid).hasSize(bufferSize);
        assertThat(snapshotAfterValid[0]).isEqualTo(0.15f);
        assertThat(snapshotAfterValid[1]).isEqualTo(-0.45f);
        assertThat(snapshotAfterValid[2]).isEqualTo(0.85f);
        assertThat(snapshotAfterValid[3]).isEqualTo(-0.95f);
        assertThat(snapshotAfterValid[4]).isEqualTo(0.0f);
        assertThat(snapshotAfterValid[5]).isEqualTo(0.0f);

        // Push samples outside [-1.0, 1.0] to verify clamping / normalization
        float[] outOfRangeSamples = new float[]{1.5f, 100.0f, -2.5f, -999.0f};
        visualizer.pushSamples(outOfRangeSamples);

        float[] snapshotAfterClamp = visualizer.getSnapshot();
        assertThat(snapshotAfterClamp[5]).isEqualTo(1.0f);
        assertThat(snapshotAfterClamp[6]).isEqualTo(1.0f);
        assertThat(snapshotAfterClamp[7]).isEqualTo(-1.0f);
        assertThat(snapshotAfterClamp[8]).isEqualTo(-1.0f);

        // Push edge case floating point values (NaN, +Infinity, -Infinity)
        float[] edgeCaseSamples = new float[]{Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY};
        visualizer.pushSamples(edgeCaseSamples);

        float[] snapshotAfterEdgeCases = visualizer.getSnapshot();
        assertThat(snapshotAfterEdgeCases[9]).isEqualTo(0.0f);
        assertThat(snapshotAfterEdgeCases[10]).isEqualTo(1.0f);
        assertThat(snapshotAfterEdgeCases[11]).isEqualTo(-1.0f);

        // Verify null and empty array push calls are safe no-ops
        assertThatCode(() -> visualizer.pushSamples(null)).doesNotThrowAnyException();
        assertThatCode(() -> visualizer.pushSamples(new float[0])).doesNotThrowAnyException();

        // Verify every sample currently in the snapshot buffer is strictly bounded in [-1.0, 1.0]
        float[] current = visualizer.getSnapshot();
        for (float s : current) {
            assertThat(s).isBetween(-1.0f, 1.0f);
            assertThat(Float.isNaN(s)).isFalse();
        }

        // Test circular buffer wrap-around
        WaveVisualizer smallVis = new WaveVisualizer(512);
        float[] batch1 = new float[500];
        for (int i = 0; i < batch1.length; i++) {
            batch1[i] = 0.5f;
        }
        smallVis.pushSamples(batch1);

        float[] batch2 = new float[20]; // 500 + 20 = 520, wraps around 8 samples past 512
        for (int i = 0; i < batch2.length; i++) {
            batch2[i] = -0.75f;
        }
        smallVis.pushSamples(batch2);

        float[] wrappedSnapshot = smallVis.getSnapshot();
        assertThat(wrappedSnapshot).hasSize(512);
        // The first 8 samples should now be overwritten with -0.75f
        for (int i = 0; i < 8; i++) {
            assertThat(wrappedSnapshot[i]).isEqualTo(-0.75f);
        }
        // Samples 8 to 499 should still be 0.5f
        for (int i = 8; i < 500; i++) {
            assertThat(wrappedSnapshot[i]).isEqualTo(0.5f);
        }
        // Samples 500 to 511 were written by the first 12 elements of batch2 (-0.75f)
        for (int i = 500; i < 512; i++) {
            assertThat(wrappedSnapshot[i]).isEqualTo(-0.75f);
        }
    }

    @Test
    @DisplayName("Multi-threaded concurrency stress test: 44.1kHz writer pushing 100,000 samples and 60Hz Swing EDT reader")
    void testConcurrencyAudioWriterAndEdtReader() throws Exception {
        int bufferSize = 2048;
        WaveVisualizer visualizer = new WaveVisualizer(bufferSize);
        visualizer.setSize(520, 180);
        visualizer.setActive(true);

        int totalSamples = 100_000;
        int chunkSize = 1024;

        AtomicBoolean writerFinished = new AtomicBoolean(false);
        AtomicReference<Throwable> writerError = new AtomicReference<>();
        AtomicReference<Throwable> readerError = new AtomicReference<>();
        AtomicInteger framesRead = new AtomicInteger(0);

        CountDownLatch startLatch = new CountDownLatch(1);

        // Writer thread simulating 44.1kHz audio stream in chunks
        Thread audioWriterThread = new Thread(() -> {
            try {
                startLatch.await();
                int pushed = 0;
                double phase = 0.0;
                while (pushed < totalSamples) {
                    int thisChunk = Math.min(chunkSize, totalSamples - pushed);
                    float[] samples = new float[thisChunk];
                    for (int i = 0; i < thisChunk; i++) {
                        // Generate audio signal with occasional values exceeding [-1, 1] to test concurrent normalization
                        float raw = (float) (Math.sin(phase) * 1.25);
                        samples[i] = raw;
                        phase += 0.05;
                    }

                    visualizer.pushSamples(samples);
                    pushed += thisChunk;

                    // Pace writer to simulate audio buffer stream
                    Thread.sleep(8);
                }
            } catch (Throwable t) {
                writerError.set(t);
            } finally {
                writerFinished.set(true);
            }
        }, "AudioWriter-44.1kHz");

        // Reader thread simulating Swing EDT running at ~60Hz (every 16ms)
        Thread swingEdtReaderThread = new Thread(() -> {
            try {
                startLatch.await();
                BufferedImage image = new BufferedImage(520, 180, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g2 = image.createGraphics();

                try {
                    while (!writerFinished.get()) {
                        // 1. Read snapshot and verify zero data corruption
                        float[] snapshot = visualizer.getSnapshot();
                        assertThat(snapshot).isNotNull();
                        assertThat(snapshot.length).isEqualTo(bufferSize);

                        for (float s : snapshot) {
                            if (Float.isNaN(s) || Float.isInfinite(s) || s < -1.0f || s > 1.0f) {
                                throw new IllegalStateException("Corrupted sample value detected in snapshot: " + s);
                            }
                        }

                        // 2. Simulate Swing EDT repaint/paintComponent
                        visualizer.paint(g2);

                        // Periodically cycle visualizer modes (WAVEFORM -> BARS -> CIRCLE)
                        if (framesRead.get() % 10 == 0) {
                            visualizer.nextMode();
                        }

                        framesRead.incrementAndGet();

                        // ~60Hz refresh rate (16ms)
                        Thread.sleep(16);
                    }

                    // One final render pass after writer completed
                    float[] finalSnapshot = visualizer.getSnapshot();
                    assertThat(finalSnapshot).isNotNull();
                    assertThat(finalSnapshot.length).isEqualTo(bufferSize);
                    for (float s : finalSnapshot) {
                        if (Float.isNaN(s) || Float.isInfinite(s) || s < -1.0f || s > 1.0f) {
                            throw new IllegalStateException("Corrupted sample in final snapshot: " + s);
                        }
                    }
                    visualizer.paint(g2);
                    framesRead.incrementAndGet();

                } finally {
                    g2.dispose();
                }
            } catch (Throwable t) {
                readerError.set(t);
            }
        }, "SwingEDT-Reader-60Hz");

        audioWriterThread.start();
        swingEdtReaderThread.start();

        // Release latch to begin concurrent execution
        startLatch.countDown();

        // Wait for writer to push all 100,000 samples
        audioWriterThread.join(10_000);
        assertThat(audioWriterThread.isAlive()).isFalse();

        // Wait for reader to complete cleanly
        swingEdtReaderThread.join(5_000);
        assertThat(swingEdtReaderThread.isAlive()).isFalse();

        // Clean up visualizer timer
        visualizer.dispose();

        // Verify zero exceptions occurred in writer and reader threads
        assertThat(writerError.get())
                .as("Audio writer thread completed cleanly without errors")
                .isNull();

        assertThat(readerError.get())
                .as("Swing EDT reader thread completed cleanly without errors or ArrayIndexOutOfBoundsException")
                .isNull();

        // Verify reader ran multiple frames concurrently during the audio stream
        assertThat(framesRead.get())
                .as("Swing EDT reader should have rendered multiple frames at 60Hz")
                .isGreaterThanOrEqualTo(10);

        // Verify final snapshot state
        float[] finalSnapshot = visualizer.getSnapshot();
        assertThat(finalSnapshot).hasSize(bufferSize);
        for (float sample : finalSnapshot) {
            assertThat(sample).isBetween(-1.0f, 1.0f);
        }
    }

    @Test
    @DisplayName("Visualizer modes and gain controls operate correctly")
    void testModesAndGain() {
        WaveVisualizer visualizer = new WaveVisualizer();
        assertThat(visualizer.getModeLabel()).isEqualTo("Waveform");

        assertThat(visualizer.nextMode()).isEqualTo("Bars");
        assertThat(visualizer.getModeLabel()).isEqualTo("Bars");

        assertThat(visualizer.nextMode()).isEqualTo("Circle");
        assertThat(visualizer.getModeLabel()).isEqualTo("Circle");

        assertThat(visualizer.nextMode()).isEqualTo("Waveform");
        assertThat(visualizer.getModeLabel()).isEqualTo("Waveform");

        visualizer.setGain(2.5f);
        visualizer.setActive(true);
        visualizer.dispose();
    }
}
