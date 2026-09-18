package leon.music.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Track Model Unit Tests")
class TrackTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldCreateTrackFromFileWithDefaultTitleAndExtension() throws IOException {
        Path filePath = Files.createFile(tempDir.resolve("my_song.mp3"));
        File file = filePath.toFile();

        Track track = Track.fromFile(file);

        assertThat(track.path()).isEqualTo(filePath);
        assertThat(track.title()).isEqualTo("my_song");
        assertThat(track.artist()).isEmpty();
        assertThat(track.album()).isEmpty();
        assertThat(track.durationMs()).isEqualTo(0L);
        assertThat(track.format()).isEqualTo("mp3");
        assertThat(track.getFileName()).isEqualTo("my_song.mp3");
        assertThat(track.toFile()).isEqualTo(file);
    }

    @Test
    void shouldThrowWhenCreatingTrackWithNullPath() {
        assertThatThrownBy(() -> Track.fromFile(null))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> new Track(null, "Title", "Artist", "Album", 1000L, 500L, "mp3"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldFormatDurationsProperly() throws IOException {
        Path filePath = Files.createFile(tempDir.resolve("sample.wav"));
        Track track = Track.fromPath(filePath);

        assertThat(track.getFormattedDuration()).isEqualTo("--:--");

        Track trackShort = track.withMetadata("Sample", "Artist", "Album", 65_000L); // 1m 5s
        assertThat(trackShort.getFormattedDuration()).isEqualTo("01:05");

        Track trackLong = track.withMetadata("Sample", "Artist", "Album", 3_665_000L); // 1h 1m 5s
        assertThat(trackLong.getFormattedDuration()).isEqualTo("01:01:05");
    }

    @Test
    void shouldGenerateFullHeaderCorrectly() throws IOException {
        Path filePath = Files.createFile(tempDir.resolve("track.flac"));
        Track track = Track.fromPath(filePath);

        // Only title
        assertThat(track.getFullHeader()).isEqualTo("track");

        // Title + Artist
        Track withArtist = track.withMetadata("Song Title", "Artist Name", "", 120_000L);
        assertThat(withArtist.getFullHeader()).isEqualTo("Song Title - Artist Name");

        // Title + Artist + Album
        Track withAll = track.withMetadata("Song Title", "Artist Name", "Greatest Hits", 120_000L);
        assertThat(withAll.getFullHeader()).isEqualTo("Song Title - Artist Name [Greatest Hits]");
    }

    @Test
    void shouldBeEqualBasedOnNormalizedAbsolutePath() throws IOException {
        Path filePath = Files.createFile(tempDir.resolve("identical.mp3"));

        Track track1 = Track.fromPath(filePath);
        Track track2 = track1.withMetadata("Different Title", "Different Artist", "Album", 99_000L);

        assertThat(track1).isEqualTo(track2);
        assertThat(track1.hashCode()).isEqualTo(track2.hashCode());
    }

    @Test
    void shouldNotBeEqualForDifferentPaths() throws IOException {
        Path file1 = Files.createFile(tempDir.resolve("file1.mp3"));
        Path file2 = Files.createFile(tempDir.resolve("file2.mp3"));

        Track track1 = Track.fromPath(file1);
        Track track2 = Track.fromPath(file2);

        assertThat(track1).isNotEqualTo(track2);
    }
}
