package leon.music.service;

import leon.music.model.Track;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("MetadataService Unit Tests")
class MetadataServiceTest {

    @TempDir
    Path tempDir;

    private MetadataService metadataService;

    @BeforeEach
    void setUp() {
        // Use direct caller-thread executor for deterministic tests
        metadataService = new MetadataService(Runnable::run);
    }

    @Test
    void shouldExtractFallbackMetadataForPlainFile() throws IOException {
        Path plainFile = Files.createFile(tempDir.resolve("plain_song.mp3"));
        Track track = Track.fromPath(plainFile);

        Track result = metadataService.extractMetadata(track);

        assertThat(result).isNotNull();
        assertThat(result.title()).isEqualTo("plain_song");
        assertThat(result.path()).isEqualTo(plainFile);
        assertThat(metadataService.getCacheSize()).isEqualTo(1);
    }

    @Test
    void shouldCacheMetadataAfterFirstExtraction() throws IOException {
        Path plainFile = Files.createFile(tempDir.resolve("cached_song.mp3"));
        Track track = Track.fromPath(plainFile);

        Track firstCall = metadataService.extractMetadata(track);
        Track secondCall = metadataService.extractMetadata(track);

        assertThat(firstCall).isSameAs(secondCall);
        assertThat(metadataService.getCacheSize()).isEqualTo(1);
    }

    @Test
    void shouldRetrieveCachedTrackWithoutDiskRead() throws IOException {
        Path plainFile = Files.createFile(tempDir.resolve("lookup.mp3"));
        Track track = Track.fromPath(plainFile);

        assertThat(metadataService.getCached(plainFile)).isEmpty();

        metadataService.extractMetadata(track);

        Optional<Track> cached = metadataService.getCached(plainFile);
        assertThat(cached).isPresent();
        assertThat(cached.get().title()).isEqualTo("lookup");
    }

    @Test
    void shouldClearCache() throws IOException {
        Path plainFile = Files.createFile(tempDir.resolve("clear_test.mp3"));
        metadataService.extractMetadata(Track.fromPath(plainFile));

        assertThat(metadataService.getCacheSize()).isEqualTo(1);

        metadataService.clearCache();

        assertThat(metadataService.getCacheSize()).isEqualTo(0);
        assertThat(metadataService.getCached(plainFile)).isEmpty();
    }

    @Test
    void shouldExtractMetadataAsynchronously() throws IOException, ExecutionException, InterruptedException {
        Path plainFile = Files.createFile(tempDir.resolve("async_track.flac"));
        Track track = Track.fromPath(plainFile);

        Track result = metadataService.extractMetadataAsync(track).get();

        assertThat(result).isNotNull();
        assertThat(result.title()).isEqualTo("async_track");
        assertThat(metadataService.getCacheSize()).isEqualTo(1);
    }

    @Test
    void shouldExtractBatchMetadataAsynchronously() throws IOException, ExecutionException, InterruptedException {
        Path file1 = Files.createFile(tempDir.resolve("batch1.mp3"));
        Path file2 = Files.createFile(tempDir.resolve("batch2.wav"));

        List<Track> tracks = List.of(Track.fromPath(file1), Track.fromPath(file2));

        List<Track> results = metadataService.extractAllAsync(tracks).get();

        assertThat(results).hasSize(2);
        assertThat(results).extracting(Track::title).containsExactly("batch1", "batch2");
        assertThat(metadataService.getCacheSize()).isEqualTo(2);
    }

    @Test
    void shouldHandleMissingFilesGracefully() {
        Path missing = tempDir.resolve("ghost.mp3");
        Track track = Track.fromPath(missing);

        Track result = metadataService.extractMetadata(track);

        assertThat(result).isNotNull();
        assertThat(result.title()).isEqualTo("ghost");
    }

    @Test
    void shouldThrowOnNullArguments() {
        assertThatThrownBy(() -> metadataService.extractMetadata((Track) null))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> metadataService.extractMetadata((File) null))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> metadataService.extractMetadata((Path) null))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> metadataService.extractMetadataAsync(null))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> metadataService.extractAllAsync(null))
                .isInstanceOf(NullPointerException.class);
    }
}
