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
}
