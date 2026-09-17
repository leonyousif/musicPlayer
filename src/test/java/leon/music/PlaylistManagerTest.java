package leon.music;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PlaylistManager Unit Tests")
class PlaylistManagerTest {

    private PlaylistManager playlistManager;

    @BeforeEach
    void setUp() {
        playlistManager = new PlaylistManager();
    }

    @Nested
    @DisplayName("Audio File Detection")
    class AudioFileDetectionTests {

        @TempDir
        Path tempDir;

        @ParameterizedTest(name = "Valid audio extension: {0}")
        @ValueSource(strings = {"track.mp3", "track.wav", "track.flac", "track.ogg", "track.aac", "track.m4a",
                                "TRACK.MP3", "Track.Flac", "song.with.multiple.dots.wav"})
        void shouldRecognizeValidAudioExtensions(String fileName) throws IOException {
            Path file = Files.createFile(tempDir.resolve(fileName));
            assertThat(PlaylistManager.isAudioFile(file.toFile())).isTrue();
        }

        @ParameterizedTest(name = "Invalid extension: {0}")
        @ValueSource(strings = {"readme.txt", "photo.jpg", "video.mp4", "movie.mkv", "song.", "noextension"})
        void shouldRejectNonAudioExtensions(String fileName) throws IOException {
            Path file = Files.createFile(tempDir.resolve(fileName));
            assertThat(PlaylistManager.isAudioFile(file.toFile())).isFalse();
        }

        @Test
        void shouldReturnFalseForNullOrNonExistentFile() {
            assertThat(PlaylistManager.isAudioFile(null)).isFalse();
            assertThat(PlaylistManager.isAudioFile(new File(tempDir.toFile(), "ghost.mp3"))).isFalse();
        }

        @Test
        void shouldReturnFalseForDirectory() {
            assertThat(PlaylistManager.isAudioFile(tempDir.toFile())).isFalse();
        }
    }

    @Nested
    @DisplayName("File and Folder Loading")
    class LoadingTests {

        @Test
        void shouldLoadSingleAudioFile(@TempDir Path tempDir) throws IOException {
            File audioFile = Files.createFile(tempDir.resolve("song.mp3")).toFile();

            List<File> loaded = playlistManager.loadFile(audioFile);

            assertThat(loaded).containsExactly(audioFile);
            assertThat(playlistManager.size()).isEqualTo(1);
            assertThat(playlistManager.getCurrentIndex()).isEqualTo(0);
            assertThat(playlistManager.getCurrent()).isEqualTo(audioFile);
        }

        @Test
        void shouldNotLoadNonAudioFile(@TempDir Path tempDir) throws IOException {
            File textFile = Files.createFile(tempDir.resolve("notes.txt")).toFile();

            List<File> loaded = playlistManager.loadFile(textFile);

            assertThat(loaded).isEmpty();
            assertThat(playlistManager.size()).isEqualTo(0);
            assertThat(playlistManager.getCurrentIndex()).isEqualTo(-1);
            assertThat(playlistManager.getCurrent()).isNull();
        }

        @Test
        void shouldLoadFolderAndSortAlphabetically(@TempDir Path tempDir) throws IOException {
            File fileC = Files.createFile(tempDir.resolve("c_song.mp3")).toFile();
            File fileA = Files.createFile(tempDir.resolve("a_song.flac")).toFile();
            File fileB = Files.createFile(tempDir.resolve("b_song.wav")).toFile();
            Files.createFile(tempDir.resolve("ignore.txt")); // Non-audio file
            Files.createDirectory(tempDir.resolve("sub_folder")); // Directory

            List<File> loaded = playlistManager.loadFolder(tempDir.toFile());

            assertThat(loaded).containsExactly(fileA, fileB, fileC);
            assertThat(playlistManager.size()).isEqualTo(3);
            assertThat(playlistManager.getCurrentIndex()).isEqualTo(0);
            assertThat(playlistManager.getCurrent()).isEqualTo(fileA);
        }

        @Test
        void shouldHandleNullOrNonDirectoryInLoadFolder() {
            assertThat(playlistManager.loadFolder(null)).isEmpty();
            assertThat(playlistManager.size()).isEqualTo(0);

            File nonExistent = new File("non_existent_folder_12345");
            assertThat(playlistManager.loadFolder(nonExistent)).isEmpty();
            assertThat(playlistManager.size()).isEqualTo(0);
        }

        @Test
        void shouldClearPreviousPlaylistWhenLoadingNewContent(@TempDir Path tempDir) throws IOException {
            File file1 = Files.createFile(tempDir.resolve("song1.mp3")).toFile();
            File file2 = Files.createFile(tempDir.resolve("song2.mp3")).toFile();

            playlistManager.loadFile(file1);
            assertThat(playlistManager.size()).isEqualTo(1);

            playlistManager.loadFile(file2);
            assertThat(playlistManager.size()).isEqualTo(1);
            assertThat(playlistManager.getCurrent()).isEqualTo(file2);
        }
    }

    @Nested
    @DisplayName("Sequential Playback Navigation")
    class SequentialNavigationTests {

        @Test
        void shouldReturnNullWhenNavigatingEmptyPlaylist() {
            assertThat(playlistManager.next()).isNull();
            assertThat(playlistManager.previous()).isNull();
            assertThat(playlistManager.getCurrent()).isNull();
            assertThat(playlistManager.getCurrentIndex()).isEqualTo(-1);
        }

        @Test
        void shouldLoopOnSingleItemPlaylist(@TempDir Path tempDir) throws IOException {
            File song = Files.createFile(tempDir.resolve("solo.mp3")).toFile();
            playlistManager.loadFile(song);

            assertThat(playlistManager.next()).isEqualTo(song);
            assertThat(playlistManager.getCurrentIndex()).isEqualTo(0);
            assertThat(playlistManager.previous()).isEqualTo(song);
            assertThat(playlistManager.getCurrentIndex()).isEqualTo(0);
        }

        @Test
        void shouldCycleNextAcrossMultipleTracks(@TempDir Path tempDir) throws IOException {
            File song1 = Files.createFile(tempDir.resolve("1.mp3")).toFile();
            File song2 = Files.createFile(tempDir.resolve("2.mp3")).toFile();
            File song3 = Files.createFile(tempDir.resolve("3.mp3")).toFile();
            playlistManager.loadFolder(tempDir.toFile());

            assertThat(playlistManager.getCurrentIndex()).isEqualTo(0);
            assertThat(playlistManager.getCurrent()).isEqualTo(song1);

            assertThat(playlistManager.next()).isEqualTo(song2);
            assertThat(playlistManager.getCurrentIndex()).isEqualTo(1);

            assertThat(playlistManager.next()).isEqualTo(song3);
            assertThat(playlistManager.getCurrentIndex()).isEqualTo(2);

            // Wrap around to beginning
            assertThat(playlistManager.next()).isEqualTo(song1);
            assertThat(playlistManager.getCurrentIndex()).isEqualTo(0);
        }

        @Test
        void shouldCyclePreviousAcrossMultipleTracks(@TempDir Path tempDir) throws IOException {
            File song1 = Files.createFile(tempDir.resolve("1.mp3")).toFile();
            File song2 = Files.createFile(tempDir.resolve("2.mp3")).toFile();
            File song3 = Files.createFile(tempDir.resolve("3.mp3")).toFile();
            playlistManager.loadFolder(tempDir.toFile());

            // Wrap around backward from index 0 to last item
            assertThat(playlistManager.previous()).isEqualTo(song3);
            assertThat(playlistManager.getCurrentIndex()).isEqualTo(2);

            assertThat(playlistManager.previous()).isEqualTo(song2);
            assertThat(playlistManager.getCurrentIndex()).isEqualTo(1);

            assertThat(playlistManager.previous()).isEqualTo(song1);
            assertThat(playlistManager.getCurrentIndex()).isEqualTo(0);
        }

        @Test
        void shouldHandleNavigationWhenNoTrackSelected(@TempDir Path tempDir) throws IOException {
            Files.createFile(tempDir.resolve("1.mp3"));
            Files.createFile(tempDir.resolve("2.mp3"));
            playlistManager.loadFolder(tempDir.toFile());

            playlistManager.setCurrentIndex(-1);

            // next() should start at index 0
            File nextTrack = playlistManager.next();
            assertThat(playlistManager.getCurrentIndex()).isEqualTo(0);
            assertThat(nextTrack).isNotNull();

            playlistManager.setCurrentIndex(-1);

            // previous() should jump to last track
            File prevTrack = playlistManager.previous();
            assertThat(playlistManager.getCurrentIndex()).isEqualTo(1);
            assertThat(prevTrack).isNotNull();
        }
    }

    @Nested
    @DisplayName("Shuffle Playback Navigation")
    class ShuffleNavigationTests {

        @Test
        void shouldToggleShuffleMode() {
            assertThat(playlistManager.isShuffleEnabled()).isFalse();

            playlistManager.setShuffleEnabled(true);
            assertThat(playlistManager.isShuffleEnabled()).isTrue();

            playlistManager.setShuffleEnabled(false);
            assertThat(playlistManager.isShuffleEnabled()).isFalse();
        }

        @Test
        void shouldPickDifferentTrackOnNextWhenShuffleEnabled(@TempDir Path tempDir) throws IOException {
            for (int i = 1; i <= 5; i++) {
                Files.createFile(tempDir.resolve("track" + i + ".mp3"));
            }
            playlistManager.loadFolder(tempDir.toFile());
            playlistManager.setShuffleEnabled(true);

            int initialIndex = playlistManager.getCurrentIndex();
            File nextTrack = playlistManager.next();

            assertThat(nextTrack).isNotNull();
            assertThat(playlistManager.getCurrentIndex()).isNotEqualTo(initialIndex);
        }

        @Test
        void shouldPickDifferentTrackOnPreviousWhenShuffleEnabled(@TempDir Path tempDir) throws IOException {
            for (int i = 1; i <= 5; i++) {
                Files.createFile(tempDir.resolve("track" + i + ".mp3"));
            }
            playlistManager.loadFolder(tempDir.toFile());
            playlistManager.setShuffleEnabled(true);

            int initialIndex = playlistManager.getCurrentIndex();
            File prevTrack = playlistManager.previous();

            assertThat(prevTrack).isNotNull();
            assertThat(playlistManager.getCurrentIndex()).isNotEqualTo(initialIndex);
        }

        @Test
        void shouldStayOnSameTrackWhenShufflingSingleItem(@TempDir Path tempDir) throws IOException {
            File solo = Files.createFile(tempDir.resolve("solo.mp3")).toFile();
            playlistManager.loadFile(solo);
            playlistManager.setShuffleEnabled(true);

            assertThat(playlistManager.next()).isEqualTo(solo);
            assertThat(playlistManager.getCurrentIndex()).isEqualTo(0);
            assertThat(playlistManager.previous()).isEqualTo(solo);
            assertThat(playlistManager.getCurrentIndex()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("Automatic Next Track Detection")
    class AutomaticNextTests {

        @Test
        void shouldDetermineAutomaticNextInSequentialMode(@TempDir Path tempDir) throws IOException {
            Files.createFile(tempDir.resolve("1.mp3"));
            Files.createFile(tempDir.resolve("2.mp3"));
            Files.createFile(tempDir.resolve("3.mp3"));
            playlistManager.loadFolder(tempDir.toFile());
            playlistManager.setShuffleEnabled(false);

            playlistManager.setCurrentIndex(0);
            assertThat(playlistManager.hasAutomaticNext()).isTrue();

            playlistManager.setCurrentIndex(1);
            assertThat(playlistManager.hasAutomaticNext()).isTrue();

            playlistManager.setCurrentIndex(2);
            assertThat(playlistManager.hasAutomaticNext()).isFalse();
        }

        @Test
        void shouldDetermineAutomaticNextInShuffleMode(@TempDir Path tempDir) throws IOException {
            Files.createFile(tempDir.resolve("1.mp3"));
            Files.createFile(tempDir.resolve("2.mp3"));
            playlistManager.loadFolder(tempDir.toFile());
            playlistManager.setShuffleEnabled(true);

            assertThat(playlistManager.hasAutomaticNext()).isTrue();
        }

        @Test
        void shouldReturnFalseForEmptyOrSingleTrackPlaylist(@TempDir Path tempDir) throws IOException {
            assertThat(playlistManager.hasAutomaticNext()).isFalse();

            File solo = Files.createFile(tempDir.resolve("solo.mp3")).toFile();
            playlistManager.loadFile(solo);

            playlistManager.setShuffleEnabled(false);
            assertThat(playlistManager.hasAutomaticNext()).isFalse();

            playlistManager.setShuffleEnabled(true);
            assertThat(playlistManager.hasAutomaticNext()).isFalse();
        }
    }

    @Nested
    @DisplayName("Playlist State and Mutability")
    class StateAndMutabilityTests {

        @Test
        void shouldResetOnClear(@TempDir Path tempDir) throws IOException {
            File file = Files.createFile(tempDir.resolve("song.mp3")).toFile();
            playlistManager.loadFile(file);

            assertThat(playlistManager.size()).isEqualTo(1);
            playlistManager.clear();

            assertThat(playlistManager.size()).isEqualTo(0);
            assertThat(playlistManager.getCurrentIndex()).isEqualTo(-1);
            assertThat(playlistManager.getCurrent()).isNull();
            assertThat(playlistManager.getAll()).isEmpty();
        }

        @Test
        void shouldHandleInvalidIndexSet(@TempDir Path tempDir) throws IOException {
            Files.createFile(tempDir.resolve("1.mp3"));
            Files.createFile(tempDir.resolve("2.mp3"));
            playlistManager.loadFolder(tempDir.toFile());

            playlistManager.setCurrentIndex(5);
            assertThat(playlistManager.getCurrentIndex()).isEqualTo(-1);
            assertThat(playlistManager.getCurrent()).isNull();

            playlistManager.setCurrentIndex(-3);
            assertThat(playlistManager.getCurrentIndex()).isEqualTo(-1);
            assertThat(playlistManager.getCurrent()).isNull();

            playlistManager.setCurrentIndex(1);
            assertThat(playlistManager.getCurrentIndex()).isEqualTo(1);
            assertThat(playlistManager.getCurrent()).isNotNull();
        }

        @Test
        void shouldProvideDefensiveCopyFromGetAll(@TempDir Path tempDir) throws IOException {
            File file = Files.createFile(tempDir.resolve("song.mp3")).toFile();
            playlistManager.loadFile(file);

            List<File> tracks = playlistManager.getAll();
            tracks.clear(); // Mutate external list

            assertThat(playlistManager.size()).isEqualTo(1);
            assertThat(playlistManager.getAll()).hasSize(1);
        }
    }
}
