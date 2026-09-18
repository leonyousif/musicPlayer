package leon.music.model;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;

/**
 * Immutable domain model representing an audio track in the music player.
 */
public record Track(
        Path path,
        String title,
        String artist,
        String album,
        long durationMs,
        long sizeBytes,
        String format
) {

    public Track {
        Objects.requireNonNull(path, "path must not be null");
        title = (title == null || title.isBlank()) ? extractDefaultTitle(path) : title.trim();
        artist = (artist == null) ? "" : artist.trim();
        album = (album == null) ? "" : album.trim();
        format = (format == null || format.isBlank()) ? extractExtension(path) : format.toLowerCase(Locale.ROOT).trim();
        durationMs = Math.max(0L, durationMs);
        sizeBytes = Math.max(0L, sizeBytes);
    }

    /**
     * Creates an initial Track instance from a File before metadata has been parsed.
     */
    public static Track fromFile(File file) {
        Objects.requireNonNull(file, "file must not be null");
        return fromPath(file.toPath());
    }

    /**
     * Creates an initial Track instance from a Path before metadata has been parsed.
     */
    public static Track fromPath(Path path) {
        Objects.requireNonNull(path, "path must not be null");
        long size = 0L;
        try {
            if (Files.isRegularFile(path)) {
                size = Files.size(path);
            }
        } catch (Exception ignored) {
            // size remains 0L
        }

        return new Track(
                path,
                extractDefaultTitle(path),
                "",
                "",
                0L,
                size,
                extractExtension(path)
        );
    }

    /**
     * Returns a new Track instance with updated metadata.
     */
    public Track withMetadata(String newTitle, String newArtist, String newAlbum, long newDurationMs) {
        return new Track(
                this.path,
                newTitle,
                newArtist,
                newAlbum,
                newDurationMs,
                this.sizeBytes,
                this.format
        );
    }

    /**
     * Returns the file name of the track (e.g. "song.mp3").
     */
    public String getFileName() {
        return path.getFileName() != null ? path.getFileName().toString() : "";
    }

    /**
     * Returns the duration formatted as "mm:ss" (or "hh:mm:ss" for tracks >= 1 hour).
     * Returns "--:--" if duration is not available.
     */
    public String getFormattedDuration() {
        if (durationMs <= 0) {
            return "--:--";
        }

        long totalSeconds = durationMs / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        if (hours > 0) {
            return String.format("%02d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format("%02d:%02d", minutes, seconds);
    }

    /**
     * Returns a full formatted display string (e.g. "Title - Artist [Album]").
     */
    public String getFullHeader() {
        StringBuilder sb = new StringBuilder(title);
        if (!artist.isEmpty()) {
            sb.append(" - ").append(artist);
        }
        if (!album.isEmpty()) {
            sb.append(" [").append(album).append("]");
        }
        return sb.toString();
    }

    /**
     * Convenience conversion to java.io.File.
     */
    public File toFile() {
        return path.toFile();
    }

    private static String extractDefaultTitle(Path path) {
        String fileName = path.getFileName() != null ? path.getFileName().toString() : "Unknown Track";
        int dot = fileName.lastIndexOf('.');
        return (dot > 0) ? fileName.substring(0, dot) : fileName;
    }

    private static String extractExtension(Path path) {
        String fileName = path.getFileName() != null ? path.getFileName().toString() : "";
        int dot = fileName.lastIndexOf('.');
        return (dot >= 0 && dot < fileName.length() - 1)
                ? fileName.substring(dot + 1).toLowerCase(Locale.ROOT)
                : "";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Track track)) return false;
        return path.toAbsolutePath().normalize().equals(track.path.toAbsolutePath().normalize());
    }

    @Override
    public int hashCode() {
        return path.toAbsolutePath().normalize().hashCode();
    }
}
