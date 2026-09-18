package leon.music.controller;

import leon.music.WaveVisualizer;
import leon.music.model.PlaybackState;
import leon.music.model.RepeatMode;
import leon.music.model.Track;
import leon.music.service.AudioPlayer;
import leon.music.service.MetadataService;
import leon.music.service.PlaylistService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;

class PlayerControllerTest {

    private StubAudioPlayer audioPlayer;
    private PlaylistService playlistService;
    private MetadataService metadataService;
    private PlayerController controller;

    private Track track1;
    private Track track2;
    private Track track3;

    @BeforeEach
    void setUp() {
        audioPlayer = new StubAudioPlayer();
        playlistService = new PlaylistService();
        // Deterministic synchronous executor for testing
        Executor directExecutor = Runnable::run;
        metadataService = new MetadataService(directExecutor);

        controller = new PlayerController(audioPlayer, playlistService, metadataService);

        track1 = new Track(Path.of("test_media/song1.mp3"), "Song 1", "Artist 1", "Album 1", 180000L, 1024L, "mp3");
        track2 = new Track(Path.of("test_media/song2.mp3"), "Song 2", "Artist 2", "Album 2", 210000L, 2048L, "mp3");
        track3 = new Track(Path.of("test_media/song3.mp3"), "Song 3", "Artist 3", "Album 3", 240000L, 4096L, "mp3");
    }

    @Test
    @DisplayName("playOrPause with empty playlist maintains IDLE state")
    void testPlayOrPauseWithEmptyPlaylist() {
        controller.playOrPause();

        assertThat(controller.getPlaybackState()).isEqualTo(PlaybackState.IDLE);
        assertThat(audioPlayer.lastPlayedPath).isNull();
    }

    @Test
    @DisplayName("playOrPause starts playback and cycles through PAUSED and PLAYING")
    void testPlayPauseCycle() {
        playlistService.setTracks(List.of(track1, track2));

        // Initial play
        controller.playOrPause();
        assertThat(controller.getPlaybackState()).isEqualTo(PlaybackState.PLAYING);
        assertThat(audioPlayer.playing).isTrue();
        assertThat(audioPlayer.lastPlayedPath).isEqualTo(track1.path().toAbsolutePath().toString());

        // Pause
        controller.playOrPause();
        assertThat(controller.getPlaybackState()).isEqualTo(PlaybackState.PAUSED);
        assertThat(audioPlayer.paused).isTrue();

        // Resume
        controller.playOrPause();
        assertThat(controller.getPlaybackState()).isEqualTo(PlaybackState.PLAYING);
        assertThat(audioPlayer.paused).isFalse();
    }

    @Test
    @DisplayName("stop resets player state and invokes stopByUser")
    void testStop() {
        playlistService.setTracks(List.of(track1));
        controller.playOrPause();
        assertThat(controller.getPlaybackState()).isEqualTo(PlaybackState.PLAYING);

        controller.stop();
        assertThat(controller.getPlaybackState()).isEqualTo(PlaybackState.STOPPED);
        assertThat(audioPlayer.stoppedByUser).isTrue();
    }

    @Test
    @DisplayName("next and previous navigate through tracks properly")
    void testNextAndPreviousNavigation() {
        playlistService.setTracks(List.of(track1, track2, track3));
        controller.playOrPause();
        assertThat(playlistService.getCurrentIndex()).isEqualTo(0);

        controller.next();
        assertThat(playlistService.getCurrentIndex()).isEqualTo(1);
        assertThat(audioPlayer.lastPlayedPath).isEqualTo(track2.path().toAbsolutePath().toString());

        controller.next();
        assertThat(playlistService.getCurrentIndex()).isEqualTo(2);
        assertThat(audioPlayer.lastPlayedPath).isEqualTo(track3.path().toAbsolutePath().toString());

        controller.previous();
        assertThat(playlistService.getCurrentIndex()).isEqualTo(1);
        assertThat(audioPlayer.lastPlayedPath).isEqualTo(track2.path().toAbsolutePath().toString());
    }

    @Test
    @DisplayName("playTrackAtIndex starts specified track")
    void testPlayTrackAtIndex() {
        playlistService.setTracks(List.of(track1, track2, track3));

        controller.playTrackAtIndex(2);
        assertThat(playlistService.getCurrentIndex()).isEqualTo(2);
        assertThat(audioPlayer.lastPlayedPath).isEqualTo(track3.path().toAbsolutePath().toString());
        assertThat(controller.getPlaybackState()).isEqualTo(PlaybackState.PLAYING);
    }

    @Test
    @DisplayName("volume adjustments delegate to AudioPlayer")
    void testVolumeControl() {
        controller.setVolume(65);
        assertThat(audioPlayer.volume).isEqualTo(65);
    }

    @Test
    @DisplayName("seeking updates target timestamp correctly")
    void testSeeking() {
        audioPlayer.lengthMs = 200_000L;
        controller.setSeeking(true);
        assertThat(controller.isSeeking()).isTrue();

        controller.seekToFraction(0.5);
        assertThat(audioPlayer.seekTimeMs).isEqualTo(100_000L);
        assertThat(controller.isSeeking()).isFalse();
    }

    @Test
    @DisplayName("shuffle toggle updates PlaylistService")
    void testShuffleToggle() {
        controller.setShuffle(true);
        assertThat(playlistService.isShuffleEnabled()).isTrue();

        controller.setShuffle(false);
        assertThat(playlistService.isShuffleEnabled()).isFalse();
    }

    @Test
    @DisplayName("repeat toggle and cycle updates RepeatMode")
    void testRepeatModes() {
        controller.setRepeat(true);
        assertThat(playlistService.getRepeatMode()).isEqualTo(RepeatMode.ALL);

        controller.setRepeat(false);
        assertThat(playlistService.getRepeatMode()).isEqualTo(RepeatMode.OFF);

        controller.toggleRepeat(); // OFF -> ALL
        assertThat(playlistService.getRepeatMode()).isEqualTo(RepeatMode.ALL);

        controller.toggleRepeat(); // ALL -> ONE
        assertThat(playlistService.getRepeatMode()).isEqualTo(RepeatMode.ONE);

        controller.toggleRepeat(); // ONE -> OFF
        assertThat(playlistService.getRepeatMode()).isEqualTo(RepeatMode.OFF);
    }

    @Test
    @DisplayName("handlePlaybackFinished advances to next track or replays according to mode")
    void testPlaybackFinishedHandling() {
        playlistService.setTracks(List.of(track1, track2));
        controller.playOrPause();
        assertThat(playlistService.getCurrentIndex()).isEqualTo(0);

        // Sequential: advances to next
        controller.handlePlaybackFinished();
        assertThat(playlistService.getCurrentIndex()).isEqualTo(1);
        assertThat(audioPlayer.lastPlayedPath).isEqualTo(track2.path().toAbsolutePath().toString());

        // At end of playlist with Repeat OFF: stops
        controller.handlePlaybackFinished();
        assertThat(controller.getPlaybackState()).isEqualTo(PlaybackState.STOPPED);

        // With Repeat ONE: replays current track
        controller.playOrPause();
        controller.toggleRepeat(); // ALL
        controller.toggleRepeat(); // ONE
        assertThat(playlistService.getRepeatMode()).isEqualTo(RepeatMode.ONE);

        audioPlayer.lastPlayedPath = null;
        controller.handlePlaybackFinished();
        assertThat(audioPlayer.lastPlayedPath).isEqualTo(track2.path().toAbsolutePath().toString());
        assertThat(controller.getPlaybackState()).isEqualTo(PlaybackState.PLAYING);
    }

    // Stub AudioPlayer for unit testing
    private static class StubAudioPlayer implements AudioPlayer {
        boolean ready = true;
        boolean playing = false;
        boolean paused = false;
        boolean stoppedByUser = false;
        String lastPlayedPath = null;
        int volume = 100;
        long lengthMs = 180000L;
        long timeMs = 0L;
        long seekTimeMs = -1L;
        Runnable onFinished;

        @Override
        public void init() {}

        @Override
        public boolean isReady() {
            return ready;
        }

        @Override
        public boolean isPlaying() {
            return playing;
        }

        @Override
        public boolean isPaused() {
            return paused;
        }

        @Override
        public boolean play(String mediaPath) {
            this.lastPlayedPath = mediaPath;
            this.playing = true;
            this.paused = false;
            return true;
        }

        @Override
        public void pause() {
            if (playing) {
                playing = false;
                paused = true;
            } else if (paused) {
                playing = true;
                paused = false;
            }
        }

        @Override
        public void stop() {
            playing = false;
            paused = false;
        }

        @Override
        public void stopByUser() {
            stoppedByUser = true;
            stop();
        }

        @Override
        public void setVolume(int volume) {
            this.volume = volume;
        }

        @Override
        public long getLengthMs() {
            return lengthMs;
        }

        @Override
        public long getTimeMs() {
            return timeMs;
        }

        @Override
        public void seekToMs(long newTimeMs) {
            this.seekTimeMs = newTimeMs;
            this.timeMs = newTimeMs;
        }

        @Override
        public void setOnFinished(Runnable onFinished) {
            this.onFinished = onFinished;
        }

        @Override
        public void setVisualizer(WaveVisualizer visualizer) {}

        @Override
        public void release() {
            playing = false;
            paused = false;
        }
    }
}

