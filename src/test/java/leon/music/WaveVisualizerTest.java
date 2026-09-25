package leon.music;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("WaveVisualizer Unit Tests")
class WaveVisualizerTest {

    @Test
    @DisplayName("Repaint timer starts on active playback and halts when playback is inactive")
    void shouldPauseAndResumeRepaintTimerBasedOnPlaybackState() {
        WaveVisualizer visualizer = new WaveVisualizer(1024);

        // Initially inactive and timer not running
        assertThat(visualizer.isPlaybackActive()).isFalse();
        assertThat(visualizer.isRepaintTimerRunning()).isFalse();

        // Resume / start playback
        visualizer.setPlaybackActive(true);
        assertThat(visualizer.isPlaybackActive()).isTrue();
        assertThat(visualizer.isRepaintTimerRunning()).isTrue();

        // Pause playback
        visualizer.setPlaybackActive(false);
        assertThat(visualizer.isPlaybackActive()).isFalse();
        assertThat(visualizer.isRepaintTimerRunning()).isFalse();

        // Resume again
        visualizer.setPlaybackActive(true);
        assertThat(visualizer.isRepaintTimerRunning()).isTrue();

        // Dispose halts timer
        visualizer.dispose();
        assertThat(visualizer.isPlaybackActive()).isFalse();
        assertThat(visualizer.isRepaintTimerRunning()).isFalse();
    }

    @Test
    @DisplayName("pushSamples updates snapshotRef with chronological audio samples")
    void shouldUpdateSnapshotRefChronologicallyOnPushSamples() {
        WaveVisualizer visualizer = new WaveVisualizer(512);

        float[] sampleBlock1 = new float[256];
        for (int i = 0; i < 256; i++) {
            sampleBlock1[i] = 0.5f;
        }
        visualizer.pushSamples(sampleBlock1);

        float[] snapshot1 = visualizer.getSnapshotRef().get();
        assertThat(snapshot1).hasSize(512);

        float[] sampleBlock2 = new float[256];
        for (int i = 0; i < 256; i++) {
            sampleBlock2[i] = -0.5f;
        }
        visualizer.pushSamples(sampleBlock2);

        float[] snapshot2 = visualizer.getSnapshotRef().get();
        assertThat(snapshot2).hasSize(512);
        // First 256 samples are older (0.5f), next 256 are newer (-0.5f)
        assertThat(snapshot2[0]).isEqualTo(0.5f);
        assertThat(snapshot2[255]).isEqualTo(0.5f);
        assertThat(snapshot2[256]).isEqualTo(-0.5f);
        assertThat(snapshot2[511]).isEqualTo(-0.5f);
    }

    @Test
    @DisplayName("Cycles through WAVEFORM, BARS, and CIRCLE visualizer modes")
    void shouldCycleModesProperly() {
        WaveVisualizer visualizer = new WaveVisualizer();
        assertThat(visualizer.getModeLabel()).isEqualTo("Waveform");

        assertThat(visualizer.nextMode()).isEqualTo("Bars");
        assertThat(visualizer.getModeLabel()).isEqualTo("Bars");

        assertThat(visualizer.nextMode()).isEqualTo("Circle");
        assertThat(visualizer.getModeLabel()).isEqualTo("Circle");

        assertThat(visualizer.nextMode()).isEqualTo("Waveform");
        assertThat(visualizer.getModeLabel()).isEqualTo("Waveform");
    }

    @Test
    @DisplayName("Renders all visualizer modes (Waveform, Bars, Circle) with FFT audio samples")
    void shouldPaintAllModesWithoutErrors() {
        WaveVisualizer visualizer = new WaveVisualizer(1024);
        visualizer.setSize(400, 200);

        // Feed a pure tone
        float[] tone = new float[1024];
        for (int i = 0; i < 1024; i++) {
            tone[i] = (float) Math.sin(2.0 * Math.PI * 440.0 * i / 44100.0);
        }
        visualizer.pushSamples(tone);
        visualizer.setPlaybackActive(true);

        BufferedImage img = new BufferedImage(400, 200, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();

        try {
            // 1. Paint Waveform
            visualizer.paint(g2);

            // 2. Switch to Bars and paint
            visualizer.nextMode();
            visualizer.paint(g2);

            // 3. Switch to Circle and paint
            visualizer.nextMode();
            visualizer.paint(g2);
        } finally {
            g2.dispose();
            visualizer.dispose();
        }
    }
}
