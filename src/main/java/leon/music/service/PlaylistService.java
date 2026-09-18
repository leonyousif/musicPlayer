package leon.music.service;

import leon.music.model.RepeatMode;
import leon.music.model.Track;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;

/**
 * Service managing playlist state, queue manipulation, repeat modes,
 * and deterministic non-repeating Fisher-Yates shuffle traversal.
 */
public class PlaylistService {

    private static final Logger log = LoggerFactory.getLogger(PlaylistService.class);

    public static final List<String> SUPPORTED_EXTENSIONS = List.of(
            "mp3", "wav", "flac", "ogg", "aac", "m4a"
    );

    private final List<Track> tracks = new ArrayList<>();
    private final List<Integer> shuffleDeck = new ArrayList<>();
    private final Random random;

    private int currentIndex = -1;
    private int shufflePos = -1;
    private boolean shuffleEnabled = false;
    private RepeatMode repeatMode = RepeatMode.OFF;

    public PlaylistService() {
        this(new Random());
    }

    public PlaylistService(Random random) {
        this.random = Objects.requireNonNull(random, "random must not be null");
    }

    /**
     * Clears all tracks and resets playback position.
     */
    public synchronized void clear() {
        tracks.clear();
        shuffleDeck.clear();
        currentIndex = -1;
        shufflePos = -1;
        log.debug("Playlist cleared");
    }

    /**
     * Returns the total number of tracks in the playlist.
     */
    public synchronized int size() {
        return tracks.size();
    }

    /**
     * Returns true if the playlist contains no tracks.
     */
    public synchronized boolean isEmpty() {
        return tracks.isEmpty();
    }

    /**
     * Returns an unmodifiable defensive copy of the current track list.
     */
    public synchronized List<Track> getTracks() {
        return List.copyOf(tracks);
    }

    /**
     * Returns the current zero-based track index, or -1 if no track is active.
     */
    public synchronized int getCurrentIndex() {
        return currentIndex;
    }

    /**
     * Sets the active track index. If invalid, resets index to -1.
     */
    public synchronized void setCurrentIndex(int index) {
        if (index < 0 || index >= tracks.size()) {
            currentIndex = -1;
            shufflePos = -1;
        } else {
            currentIndex = index;
            syncShufflePositionWithCurrentIndex();
        }
    }

    /**
     * Returns the currently active Track, or empty Optional if none is active.
     */
    public synchronized Optional<Track> getCurrent() {
        if (currentIndex < 0 || currentIndex >= tracks.size()) {
            return Optional.empty();
        }
        return Optional.of(tracks.get(currentIndex));
    }

    /**
     * Replaces the current playlist with a single track and sets it active.
     */
    public synchronized Optional<Track> loadTrack(Track track) {
        clear();
        if (track != null) {
            addTrack(track);
            setCurrentIndex(0);
        }
        return getCurrent();
    }

    /**
     * Replaces the current playlist with a collection of tracks.
     */
    public synchronized void setTracks(List<Track> newTracks) {
        clear();
        if (newTracks != null && !newTracks.isEmpty()) {
            tracks.addAll(newTracks);
            rebuildShuffleDeck();
            setCurrentIndex(0);
        }
    }

    /**
     * Appends a single track to the end of the playlist.
     */
    public synchronized void addTrack(Track track) {
        Objects.requireNonNull(track, "track must not be null");
        tracks.add(track);
        if (shuffleEnabled) {
            shuffleDeck.add(tracks.size() - 1);
        }
        if (currentIndex == -1 && tracks.size() == 1) {
            currentIndex = 0;
            shufflePos = 0;
        }
        log.debug("Added track: {}", track.title());
    }

    /**
     * Appends multiple tracks to the playlist.
     */
    public synchronized void addTracks(Collection<Track> newTracks) {
        if (newTracks == null || newTracks.isEmpty()) return;
        for (Track t : newTracks) {
            if (t != null) addTrack(t);
        }
    }

    /**
     * Removes the track at the specified index.
     */
    public synchronized Optional<Track> removeTrack(int index) {
        if (index < 0 || index >= tracks.size()) {
            return Optional.empty();
        }

        Track removed = tracks.remove(index);

        if (tracks.isEmpty()) {
            currentIndex = -1;
            shufflePos = -1;
            shuffleDeck.clear();
        } else if (index == currentIndex) {
            if (currentIndex >= tracks.size()) {
                currentIndex = tracks.size() - 1;
            }
            rebuildShuffleDeck();
        } else if (index < currentIndex) {
            currentIndex--;
            rebuildShuffleDeck();
        } else {
            rebuildShuffleDeck();
        }

        log.debug("Removed track: {}", removed.title());
        return Optional.of(removed);
    }

    /**
     * Moves a track from fromIndex to toIndex.
     */
    public synchronized void moveTrack(int fromIndex, int toIndex) {
        if (fromIndex < 0 || fromIndex >= tracks.size() || toIndex < 0 || toIndex >= tracks.size()) {
            return;
        }
        if (fromIndex == toIndex) return;

        Track track = tracks.remove(fromIndex);
        tracks.add(toIndex, track);

        if (currentIndex == fromIndex) {
            currentIndex = toIndex;
        } else if (fromIndex < currentIndex && toIndex >= currentIndex) {
            currentIndex--;
        } else if (fromIndex > currentIndex && toIndex <= currentIndex) {
            currentIndex++;
        }

        if (shuffleEnabled) {
            rebuildShuffleDeck();
        }
    }

    /**
     * Scans a directory for audio files, sorts them alphabetically case-insensitively,
     * and loads them into the playlist.
     */
    public synchronized List<Track> loadFolder(File folder) {
        clear();
        if (folder == null || !folder.isDirectory()) {
            return getTracks();
        }

        File[] files = folder.listFiles();
        if (files == null) {
            return getTracks();
        }

        List<Track> loaded = Arrays.stream(files)
                .filter(File::isFile)
                .filter(PlaylistService::isAudioFile)
                .sorted(Comparator.comparing(f -> f.getName().toLowerCase(Locale.ROOT)))
                .map(Track::fromFile)
                .toList();

        setTracks(loaded);
        log.info("Loaded {} audio tracks from folder: {}", loaded.size(), folder.getAbsolutePath());
        return getTracks();
    }

    /**
     * Advances to the next track according to shuffle and repeat modes.
     */
    public synchronized Optional<Track> next() {
        if (tracks.isEmpty()) return Optional.empty();

        if (repeatMode == RepeatMode.ONE && currentIndex >= 0) {
            return getCurrent();
        }

        if (shuffleEnabled && tracks.size() > 1) {
            if (shufflePos + 1 < shuffleDeck.size()) {
                shufflePos++;
                currentIndex = shuffleDeck.get(shufflePos);
                return getCurrent();
            } else if (repeatMode == RepeatMode.ALL) {
                reshuffleDeckForNextCycle();
                shufflePos = 0;
                currentIndex = shuffleDeck.get(0);
                return getCurrent();
            } else {
                return Optional.empty();
            }
        }

        // Sequential mode
        if (currentIndex < 0) {
            currentIndex = 0;
            return getCurrent();
        }

        if (currentIndex + 1 < tracks.size()) {
            currentIndex++;
            return getCurrent();
        } else if (repeatMode == RepeatMode.ALL) {
            currentIndex = 0;
            return getCurrent();
        } else {
            return Optional.empty();
        }
    }

    /**
     * Steps backward to the previous track according to shuffle and repeat modes.
     */
    public synchronized Optional<Track> previous() {
        if (tracks.isEmpty()) return Optional.empty();

        if (repeatMode == RepeatMode.ONE && currentIndex >= 0) {
            return getCurrent();
        }

        if (shuffleEnabled && tracks.size() > 1) {
            if (shufflePos > 0) {
                shufflePos--;
                currentIndex = shuffleDeck.get(shufflePos);
                return getCurrent();
            } else if (repeatMode == RepeatMode.ALL) {
                shufflePos = shuffleDeck.size() - 1;
                currentIndex = shuffleDeck.get(shufflePos);
                return getCurrent();
            } else {
                return getCurrent();
            }
        }

        // Sequential mode
        if (currentIndex <= 0) {
            if (repeatMode == RepeatMode.ALL) {
                currentIndex = tracks.size() - 1;
                return getCurrent();
            } else {
                currentIndex = 0;
                return getCurrent();
            }
        }

        currentIndex--;
        return getCurrent();
    }

    /**
     * Determines whether playback should automatically advance to a next track
     * upon playback finishing.
     */
    public synchronized boolean hasAutomaticNext() {
        if (tracks.isEmpty()) return false;
        if (repeatMode == RepeatMode.ONE || repeatMode == RepeatMode.ALL) return true;

        if (shuffleEnabled) {
            return shufflePos >= 0 && shufflePos < shuffleDeck.size() - 1;
        }

        return currentIndex >= 0 && currentIndex < tracks.size() - 1;
    }

    public synchronized boolean isShuffleEnabled() {
        return shuffleEnabled;
    }

    public synchronized void setShuffleEnabled(boolean enabled) {
        if (this.shuffleEnabled == enabled) return;
        this.shuffleEnabled = enabled;

        if (enabled) {
            rebuildShuffleDeck();
        } else {
            shuffleDeck.clear();
            shufflePos = -1;
        }
        log.debug("Shuffle mode set to: {}", enabled);
    }

    public synchronized RepeatMode getRepeatMode() {
        return repeatMode;
    }

    public synchronized void setRepeatMode(RepeatMode mode) {
        this.repeatMode = Objects.requireNonNull(mode, "repeatMode must not be null");
        log.debug("Repeat mode set to: {}", mode);
    }

    public synchronized RepeatMode cycleRepeatMode() {
        setRepeatMode(repeatMode.next());
        return repeatMode;
    }

    private void rebuildShuffleDeck() {
        shuffleDeck.clear();
        for (int i = 0; i < tracks.size(); i++) {
            shuffleDeck.add(i);
        }
        Collections.shuffle(shuffleDeck, random);

        if (currentIndex >= 0 && tracks.size() > 1) {
            int posOfCurrent = shuffleDeck.indexOf(currentIndex);
            if (posOfCurrent > 0) {
                Collections.swap(shuffleDeck, 0, posOfCurrent);
            }
            shufflePos = 0;
        } else {
            shufflePos = tracks.isEmpty() ? -1 : 0;
        }
    }

    private void reshuffleDeckForNextCycle() {
        int lastTrack = shuffleDeck.isEmpty() ? -1 : shuffleDeck.get(shuffleDeck.size() - 1);
        Collections.shuffle(shuffleDeck, random);

        // Ensure the first track of new cycle is not identical to last track of previous cycle
        if (tracks.size() > 1 && shuffleDeck.get(0) == lastTrack) {
            Collections.swap(shuffleDeck, 0, 1);
        }
    }

    private void syncShufflePositionWithCurrentIndex() {
        if (!shuffleEnabled || shuffleDeck.isEmpty() || currentIndex < 0) {
            return;
        }
        int pos = shuffleDeck.indexOf(currentIndex);
        if (pos >= 0) {
            shufflePos = pos;
        }
    }

    /**
     * Checks if a file has a supported audio extension.
     */
    public static boolean isAudioFile(File file) {
        if (file == null || !file.isFile()) return false;
        return isAudioPath(file.toPath());
    }

    /**
     * Checks if a Path has a supported audio extension.
     */
    public static boolean isAudioPath(Path path) {
        if (path == null) return false;
        Path fileName = path.getFileName();
        if (fileName == null) return false;

        String name = fileName.toString().toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) return false;
        String ext = name.substring(dot + 1);
        return SUPPORTED_EXTENSIONS.contains(ext);
    }
}
