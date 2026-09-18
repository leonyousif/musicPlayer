package leon.music.service;

import leon.music.model.RepeatMode;
import leon.music.model.Track;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PlaylistService Unit Tests")
class PlaylistServiceTest {

    @TempDir
    Path tempDir;

    private PlaylistService playlistService;

    @BeforeEach
    void setUp() {
        playlistService = new PlaylistService(new Random(42)); // Fixed seed for reproducible shuffle tests
    }

    private Track createTrack(String name) throws IOException {
        Path path = Files.createFile(tempDir.resolve(name));
        return Track.fromPath(path);
    }

    @Nested
    @DisplayName("Queue Operations")
    class QueueOperationTests {

        @Test
        void shouldStartEmpty() {
            assertThat(playlistService.isEmpty()).isTrue();
            assertThat(playlistService.size()).isEqualTo(0);
            assertThat(playlistService.getCurrent()).isEmpty();
            assertThat(playlistService.getCurrentIndex()).isEqualTo(-1);
        }

        @Test
        void shouldAddTrackAndSetCurrentIndexIfFirst() throws IOException {
            Track track = createTrack("song.mp3");

            playlistService.addTrack(track);

            assertThat(playlistService.size()).isEqualTo(1);
            assertThat(playlistService.getCurrentIndex()).isEqualTo(0);
            assertThat(playlistService.getCurrent()).contains(track);
        }

        @Test
        void shouldAddMultipleTracks() throws IOException {
            Track t1 = createTrack("t1.mp3");
            Track t2 = createTrack("t2.flac");

            playlistService.addTracks(List.of(t1, t2));

            assertThat(playlistService.size()).isEqualTo(2);
            assertThat(playlistService.getTracks()).containsExactly(t1, t2);
        }

        @Test
        void shouldRemoveTrackAndAdjustCurrentIndex() throws IOException {
            Track t1 = createTrack("1.mp3");
            Track t2 = createTrack("2.mp3");
            Track t3 = createTrack("3.mp3");
            playlistService.addTracks(List.of(t1, t2, t3));

            playlistService.setCurrentIndex(1); // playing t2

            Optional<Track> removed = playlistService.removeTrack(0); // remove t1 before current
            assertThat(removed).contains(t1);
            assertThat(playlistService.size()).isEqualTo(2);
            assertThat(playlistService.getCurrentIndex()).isEqualTo(0);
            assertThat(playlistService.getCurrent()).contains(t2);
        }

        @Test
        void shouldMoveTrack() throws IOException {
            Track t1 = createTrack("1.mp3");
            Track t2 = createTrack("2.mp3");
            Track t3 = createTrack("3.mp3");
            playlistService.addTracks(List.of(t1, t2, t3));

            playlistService.setCurrentIndex(0); // t1
            playlistService.moveTrack(0, 2);

            assertThat(playlistService.getTracks()).containsExactly(t2, t3, t1);
            assertThat(playlistService.getCurrentIndex()).isEqualTo(2);
            assertThat(playlistService.getCurrent()).contains(t1);
        }

        @Test
        void shouldClearPlaylist() throws IOException {
            playlistService.addTrack(createTrack("test.mp3"));
            playlistService.clear();

            assertThat(playlistService.isEmpty()).isTrue();
            assertThat(playlistService.getCurrentIndex()).isEqualTo(-1);
            assertThat(playlistService.getCurrent()).isEmpty();
        }

        @Test
        void shouldLoadFolderAlphabetically() throws IOException {
            createTrack("z_last.mp3");
            createTrack("a_first.flac");
            createTrack("m_mid.wav");
            Files.createFile(tempDir.resolve("ignore.txt"));

            List<Track> loaded = playlistService.loadFolder(tempDir.toFile());

            assertThat(loaded).extracting(Track::getFileName)
                    .containsExactly("a_first.flac", "m_mid.wav", "z_last.mp3");
            assertThat(playlistService.getCurrentIndex()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("Sequential Playback Navigation")
    class SequentialNavigationTests {

        private Track t1;
        private Track t2;
        private Track t3;

        @BeforeEach
        void initTracks() throws IOException {
            t1 = createTrack("1.mp3");
            t2 = createTrack("2.mp3");
            t3 = createTrack("3.mp3");
            playlistService.addTracks(List.of(t1, t2, t3));
        }

        @Test
        void shouldStopAtEndOfPlaylistWhenRepeatOff() {
            playlistService.setRepeatMode(RepeatMode.OFF);
            playlistService.setCurrentIndex(0);

            assertThat(playlistService.next()).contains(t2);
            assertThat(playlistService.next()).contains(t3);
            assertThat(playlistService.next()).isEmpty(); // Stop at end
            assertThat(playlistService.getCurrent()).contains(t3);
        }

        @Test
        void shouldLoopPlaylistWhenRepeatAll() {
            playlistService.setRepeatMode(RepeatMode.ALL);
            playlistService.setCurrentIndex(2); // at t3

            assertThat(playlistService.next()).contains(t1); // Loops to 0
            assertThat(playlistService.previous()).contains(t3); // Loops back to 2
        }

        @Test
        void shouldRepeatCurrentTrackWhenRepeatOne() {
            playlistService.setRepeatMode(RepeatMode.ONE);
            playlistService.setCurrentIndex(1);

            assertThat(playlistService.next()).contains(t2);
            assertThat(playlistService.previous()).contains(t2);
        }
    }

    @Nested
    @DisplayName("Fisher-Yates Shuffle Navigation")
    class ShuffleNavigationTests {

        private List<Track> tracks;

        @BeforeEach
        void initTracks() throws IOException {
            tracks = new ArrayList<>();
            for (int i = 1; i <= 5; i++) {
                tracks.add(createTrack("track_" + i + ".mp3"));
            }
            playlistService.addTracks(tracks);
            playlistService.setShuffleEnabled(true);
        }

        @Test
        void shouldTraverseEntirePlaylistWithoutDuplicatesInOneCycle() {
            playlistService.setRepeatMode(RepeatMode.OFF);

            Set<Track> visited = new HashSet<>();
            visited.add(playlistService.getCurrent().orElseThrow());

            while (playlistService.hasAutomaticNext()) {
                Optional<Track> next = playlistService.next();
                assertThat(next).isPresent();
                visited.add(next.get());
            }

            // All 5 distinct tracks must have been visited exactly once
            assertThat(visited).hasSize(5);
            assertThat(visited).containsAll(tracks);

            // Reaching end of cycle stops when RepeatMode.OFF
            assertThat(playlistService.next()).isEmpty();
        }

        @Test
        void shouldTraverseBackwardInShuffleOrderOnPrevious() {
            playlistService.setRepeatMode(RepeatMode.OFF);

            Track first = playlistService.getCurrent().orElseThrow();
            Track second = playlistService.next().orElseThrow();
            Track third = playlistService.next().orElseThrow();

            assertThat(third).isNotEqualTo(second);
            assertThat(second).isNotEqualTo(first);

            // Stepping backward returns the previously heard tracks in reverse
            assertThat(playlistService.previous()).contains(second);
            assertThat(playlistService.previous()).contains(first);
        }

        @Test
        void shouldLoopShuffleWithRepeatAll() {
            playlistService.setRepeatMode(RepeatMode.ALL);

            // Step through entire 5-track cycle
            for (int i = 0; i < 4; i++) {
                assertThat(playlistService.next()).isPresent();
            }

            // Next step wraps into a new shuffled cycle without throwing
            Optional<Track> newCycleStart = playlistService.next();
            assertThat(newCycleStart).isPresent();
            assertThat(tracks).contains(newCycleStart.get());
        }
    }

    @Nested
    @DisplayName("Automatic Next Track and Repeat Cycle")
    class AutomaticNextTests {

        @Test
        void shouldDetermineAutomaticNextCorrectly() throws IOException {
            Track t1 = createTrack("1.mp3");
            Track t2 = createTrack("2.mp3");
            playlistService.addTracks(List.of(t1, t2));

            // Sequential OFF: true at index 0, false at index 1
            playlistService.setRepeatMode(RepeatMode.OFF);
            playlistService.setShuffleEnabled(false);
            playlistService.setCurrentIndex(0);
            assertThat(playlistService.hasAutomaticNext()).isTrue();

            playlistService.setCurrentIndex(1);
            assertThat(playlistService.hasAutomaticNext()).isFalse();

            // ALL or ONE: always true
            playlistService.setRepeatMode(RepeatMode.ALL);
            assertThat(playlistService.hasAutomaticNext()).isTrue();

            playlistService.setRepeatMode(RepeatMode.ONE);
            assertThat(playlistService.hasAutomaticNext()).isTrue();
        }

        @Test
        void shouldCycleRepeatModes() {
            assertThat(playlistService.getRepeatMode()).isEqualTo(RepeatMode.OFF);

            assertThat(playlistService.cycleRepeatMode()).isEqualTo(RepeatMode.ALL);
            assertThat(playlistService.cycleRepeatMode()).isEqualTo(RepeatMode.ONE);
            assertThat(playlistService.cycleRepeatMode()).isEqualTo(RepeatMode.OFF);
        }
    }

    @Nested
    @DisplayName("Audio Extension Detection")
    class ExtensionTests {

        @Test
        void shouldRecognizeValidAudioExtensions() throws IOException {
            File f1 = Files.createFile(tempDir.resolve("song.mp3")).toFile();
            File f2 = Files.createFile(tempDir.resolve("SONG.FLAC")).toFile();
            File f3 = Files.createFile(tempDir.resolve("song.wav")).toFile();
            File f4 = Files.createFile(tempDir.resolve("song.m4a")).toFile();
            File doc = Files.createFile(tempDir.resolve("doc.pdf")).toFile();

            assertThat(PlaylistService.isAudioFile(f1)).isTrue();
            assertThat(PlaylistService.isAudioFile(f2)).isTrue();
            assertThat(PlaylistService.isAudioFile(f3)).isTrue();
            assertThat(PlaylistService.isAudioFile(f4)).isTrue();

            assertThat(PlaylistService.isAudioFile(doc)).isFalse();
            assertThat(PlaylistService.isAudioFile(null)).isFalse();

            // isAudioPath tests by path name without requiring file on disk
            assertThat(PlaylistService.isAudioPath(Path.of("virtual/tune.ogg"))).isTrue();
            assertThat(PlaylistService.isAudioPath(Path.of("virtual/tune.aac"))).isTrue();
            assertThat(PlaylistService.isAudioPath(Path.of("virtual/image.png"))).isFalse();
            assertThat(PlaylistService.isAudioPath(null)).isFalse();
        }
    }
}
