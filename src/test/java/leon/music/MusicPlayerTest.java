package leon.music;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.BlockingQueue;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MusicPlayer Playback Queue and Threading Tests")
class MusicPlayerTest {

    @BeforeAll
    static void setUpHeadless() {
        System.setProperty("java.awt.headless", "true");
    }

    @Test
    @DisplayName("Audio queue is bounded to 16 chunks (~250ms of audio)")
    void testBoundedAudioQueueConfiguration() {
        MusicPlayer player = new MusicPlayer();
        BlockingQueue<byte[]> queue = player.getAudioQueue();

        assertThat(queue).isNotNull();
        assertThat(queue.remainingCapacity()).isEqualTo(16);
    }

    @Test
    @DisplayName("queueAudioChunk offers non-blocking and drops oldest frame when full")
    void testQueueAudioChunkNonBlockingAndDropOldest() {
        MusicPlayer player = new MusicPlayer();
        BlockingQueue<byte[]> queue = player.getAudioQueue();

        // Fill queue to its capacity of 16
        for (int i = 0; i < 16; i++) {
            player.queueAudioChunk(new byte[]{(byte) i});
        }
        assertThat(queue).hasSize(16);
        assertThat(queue.peek()).containsExactly(0);

        // Push 17th chunk: should drop chunk 0 and accept chunk 16 without blocking
        player.queueAudioChunk(new byte[]{16});
        assertThat(queue).hasSize(16);
        assertThat(queue.peek()).containsExactly(1);

        // Push 18th chunk: should drop chunk 1 and accept chunk 17
        player.queueAudioChunk(new byte[]{17});
        assertThat(queue).hasSize(16);
        assertThat(queue.peek()).containsExactly(2);

        // Null and empty chunks are safely ignored
        player.queueAudioChunk(null);
        player.queueAudioChunk(new byte[0]);
        assertThat(queue).hasSize(16);
    }

    @Test
    @DisplayName("seekToMs clears audioQueue so pre-seek audio is dropped")
    void testSeekToMsFlushesQueue() {
        MusicPlayer player = new MusicPlayer();
        for (int i = 0; i < 8; i++) {
            player.queueAudioChunk(new byte[]{(byte) i});
        }
        assertThat(player.getAudioQueue()).hasSize(8);

        player.seekToMs(5000L);

        assertThat(player.getAudioQueue()).isEmpty();
    }

    @Test
    @DisplayName("stop clears audioQueue")
    void testStopFlushesQueue() {
        MusicPlayer player = new MusicPlayer();
        for (int i = 0; i < 10; i++) {
            player.queueAudioChunk(new byte[]{(byte) i});
        }
        assertThat(player.getAudioQueue()).hasSize(10);

        player.stop();

        assertThat(player.getAudioQueue()).isEmpty();
    }

    @Test
    @DisplayName("Playback daemon thread manages lifecycle and cleanly terminates on release")
    void testPlaybackThreadLifecycle() throws InterruptedException {
        MusicPlayer player = new MusicPlayer();

        // Start playback thread
        player.startPlaybackThread();

        Thread thread = player.getPlaybackThread();
        assertThat(thread).isNotNull();
        assertThat(thread.isAlive()).isTrue();
        assertThat(thread.isDaemon()).isTrue();
        assertThat(thread.getName()).isEqualTo("audio-playback-thread");
        assertThat(player.isPlaybackRunning()).isTrue();

        // Enqueue some chunks
        player.queueAudioChunk(new byte[]{1, 2, 3});
        player.queueAudioChunk(new byte[]{4, 5, 6});

        // Release player
        player.release();

        assertThat(player.isPlaybackRunning()).isFalse();
        assertThat(player.getAudioQueue()).isEmpty();
        assertThat(player.getPlaybackThread()).isNull();
        assertThat(thread.isAlive()).isFalse();
    }

    @Test
    @DisplayName("applyVolume performs zero-allocation in-place scaling with high-precision math")
    void testApplyVolumeZeroAllocationInPlaceScaling() {
        MusicPlayer player = new MusicPlayer();

        // Construct 16-bit signed PCM samples in little-endian format
        // Sample 0: 1000 (0x03E8 -> low: 0xE8, high: 0x03)
        // Sample 1: -2000 (0xF830 -> low: 0x30, high: 0xF8)
        // Sample 2: 30000 (0x7530 -> low: 0x30, high: 0x75)
        // Sample 3: -30000 (0x8AD0 -> low: 0xD0, high: 0x8A)
        short[] originalSamples = new short[]{1000, -2000, 30000, -30000};
        byte[] pcm = new byte[originalSamples.length * 2];
        for (int i = 0; i < originalSamples.length; i++) {
            short s = originalSamples[i];
            pcm[i * 2] = (byte) (s & 0xff);
            pcm[i * 2 + 1] = (byte) ((s >> 8) & 0xff);
        }

        // Apply 50% volume (0.5f)
        byte[] result = player.applyVolume(pcm, 0.5f);

        // Verify zero allocation: must be the exact same array instance
        assertThat(result).isSameAs(pcm);

        // Verify scaled values: 1000 -> 500, -2000 -> -1000, 30000 -> 15000, -30000 -> -15000
        short[] expectedSamples = new short[]{500, -1000, 15000, -15000};
        for (int i = 0; i < expectedSamples.length; i++) {
            short actual = (short) ((pcm[i * 2 + 1] << 8) | (pcm[i * 2] & 0xff));
            assertThat(actual).isEqualTo(expectedSamples[i]);
        }

        // Volume >= 0.995f returns pcm without scaling
        byte[] fullVolResult = player.applyVolume(pcm, 1.0f);
        assertThat(fullVolResult).isSameAs(pcm);

        // Volume <= 0.001f zeros out the buffer
        byte[] zeroVolResult = player.applyVolume(pcm, 0.0f);
        assertThat(zeroVolResult).isSameAs(pcm);
        assertThat(pcm).containsOnly((byte) 0);
    }

    @Test
    @DisplayName("Atomic userRequestedStop flag tracks stopByUser and play calls")
    void testAtomicStopStateFlag() {
        MusicPlayer player = new MusicPlayer();

        // Initially false
        assertThat(player.getUserRequestedStop().get()).isFalse();

        // Calling stopByUser sets userRequestedStop to true
        player.stopByUser();
        assertThat(player.getUserRequestedStop().get()).isTrue();

        // finished callback logic: compareAndSet(true, false) returns true when stopped by user
        boolean wasStoppedByUser = player.getUserRequestedStop().compareAndSet(true, false);
        assertThat(wasStoppedByUser).isTrue();
        assertThat(player.getUserRequestedStop().get()).isFalse();

        // Natural completion: compareAndSet(true, false) returns false
        boolean wasNatural = player.getUserRequestedStop().compareAndSet(true, false);
        assertThat(wasNatural).isFalse();

        // play() resets userRequestedStop to false
        player.stopByUser();
        assertThat(player.getUserRequestedStop().get()).isTrue();
        player.play("dummy_path.mp3");
        assertThat(player.getUserRequestedStop().get()).isFalse();
    }
}
