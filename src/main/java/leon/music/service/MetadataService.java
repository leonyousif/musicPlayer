package leon.music.service;

import leon.music.model.Track;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.AudioHeader;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Service responsible for extracting ID3/Vorbis audio metadata (title, artist, album, duration)
 * from media files, featuring in-memory caching and non-blocking asynchronous batch processing.
 */
public class MetadataService {

    private static final Logger log = LoggerFactory.getLogger(MetadataService.class);

    private final Map<Path, Track> cache;
    private final Executor executor;

    /**
     * Constructs a MetadataService with a default background thread pool of daemon threads.
     */
    public MetadataService() {
        this(Executors.newFixedThreadPool(
                Math.max(2, Runtime.getRuntime().availableProcessors()),
                r -> {
                    Thread t = new Thread(r, "metadata-extractor");
                    t.setDaemon(true);
                    return t;
                }
        ));
    }

    /**
     * Constructs a MetadataService with a custom executor (e.g. for deterministic unit testing).
     */
    public MetadataService(Executor executor) {
        this.cache = new ConcurrentHashMap<>();
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
    }

    /**
     * Extracts metadata synchronously, returning a cached result if previously loaded.
     */
    public Track extractMetadata(Track track) {
        Objects.requireNonNull(track, "track must not be null");
        Path normalizedPath = track.path().toAbsolutePath().normalize();

        Track cached = cache.get(normalizedPath);
        if (cached != null) {
            return cached;
        }

        Track enriched = parseMetadata(track);
        cache.put(normalizedPath, enriched);
        return enriched;
    }

    /**
     * Convenience method to extract metadata for a File.
     */
    public Track extractMetadata(File file) {
        Objects.requireNonNull(file, "file must not be null");
        return extractMetadata(Track.fromFile(file));
    }

    /**
     * Convenience method to extract metadata for a Path.
     */
    public Track extractMetadata(Path path) {
        Objects.requireNonNull(path, "path must not be null");
        return extractMetadata(Track.fromPath(path));
    }

    /**
     * Asynchronously extracts metadata for a Track off the caller's thread.
     */
    public CompletableFuture<Track> extractMetadataAsync(Track track) {
        Objects.requireNonNull(track, "track must not be null");
        return CompletableFuture.supplyAsync(() -> extractMetadata(track), executor);
    }

    /**
     * Asynchronously extracts metadata for a collection of tracks in parallel.
     */
    public CompletableFuture<List<Track>> extractAllAsync(List<Track> tracks) {
        Objects.requireNonNull(tracks, "tracks must not be null");
        List<CompletableFuture<Track>> futures = tracks.stream()
                .map(this::extractMetadataAsync)
                .toList();

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream()
                        .map(CompletableFuture::join)
                        .toList()
                );
    }

    /**
     * Looks up an existing cached Track without performing disk I/O.
     */
    public Optional<Track> getCached(Path path) {
        if (path == null) return Optional.empty();
        return Optional.ofNullable(cache.get(path.toAbsolutePath().normalize()));
    }

    /**
     * Clears all cached metadata entries.
     */
    public void clearCache() {
        cache.clear();
    }

    /**
     * Returns the current number of cached tracks.
     */
    public int getCacheSize() {
        return cache.size();
    }

    private Track parseMetadata(Track initialTrack) {
        File file = initialTrack.toFile();
        if (!file.exists() || !file.isFile()) {
            return initialTrack;
        }

        try {
            AudioFile af = AudioFileIO.read(file);
            Tag tag = af.getTag();
            AudioHeader header = af.getAudioHeader();

            String title = initialTrack.title();
            String artist = initialTrack.artist();
            String album = initialTrack.album();
            long durationMs = initialTrack.durationMs();

            if (tag != null) {
                String t = tag.getFirst(FieldKey.TITLE);
                String ar = tag.getFirst(FieldKey.ARTIST);
                String al = tag.getFirst(FieldKey.ALBUM);

                if (t != null && !t.isBlank()) title = t.trim();
                if (ar != null && !ar.isBlank()) artist = ar.trim();
                if (al != null && !al.isBlank()) album = al.trim();
            }

            if (header != null) {
                double preciseSeconds = header.getPreciseTrackLength();
                if (preciseSeconds > 0) {
                    durationMs = Math.round(preciseSeconds * 1000.0);
                } else if (header.getTrackLength() > 0) {
                    durationMs = header.getTrackLength() * 1000L;
                }
            }

            return initialTrack.withMetadata(title, artist, album, durationMs);
        } catch (Exception e) {
            log.warn("Could not read audio metadata for {}: {}", file.getName(), e.getMessage());
            return initialTrack;
        }
    }
}
