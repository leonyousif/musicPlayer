package leon.music;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public class PlaylistManager {

    private final List<File> playlist = new ArrayList<>();
    private final Random random = new Random();

    private int currentIndex = -1;
    private boolean shuffleEnabled = false;

    // keep extensions in one place
    private static final List<String> AUDIO_EXTS = Arrays.asList(
            "mp3", "wav", "flac", "ogg", "aac", "m4a"
    );

    public void setShuffleEnabled(boolean enabled) {
        this.shuffleEnabled = enabled;
    }

    public boolean isShuffleEnabled() {
        return shuffleEnabled;
    }

    public void clear() {
        playlist.clear();
        currentIndex = -1;
    }

    public int size() {
        return playlist.size();
    }

    public List<File> getAll() {
        return new ArrayList<>(playlist);
    }

    public int getCurrentIndex() {
        return currentIndex;
    }

    public void setCurrentIndex(int index) {
        if (index < 0 || index >= playlist.size()) {
            currentIndex = -1;
        } else {
            currentIndex = index;
        }
    }

    public File getCurrent() {
        if (currentIndex < 0 || currentIndex >= playlist.size()) return null;
        return playlist.get(currentIndex);
    }


    // Loads files from a folder sorted by name and returns the loaded list
    public List<File> loadFolder(File folder) {
        clear();

        if (folder == null || !folder.isDirectory()) {
            return getAll();
        }

        File[] files = folder.listFiles();
        if (files == null) return getAll();

        Arrays.stream(files)
                .filter(File::isFile)
                .filter(PlaylistManager::isAudioFile)
                .sorted(Comparator.comparing(f -> f.getName().toLowerCase(Locale.ROOT)))
                .forEach(playlist::add);

        if (!playlist.isEmpty()) currentIndex = 0;

        return getAll();
    }

    public File next() {
        if (playlist.isEmpty()) return null;

        if (shuffleEnabled && playlist.size() > 1) {
            int nextIndex;
            do {
                nextIndex = random.nextInt(playlist.size());
            } while (nextIndex == currentIndex);
            currentIndex = nextIndex;
        } else {
            currentIndex = (currentIndex + 1) % playlist.size();
        }

        return getCurrent();
    }

    public File previous() {
        if (playlist.isEmpty()) return null;

        if (shuffleEnabled && playlist.size() > 1) {
            int prevIndex;
            do {
                prevIndex = random.nextInt(playlist.size());
            } while (prevIndex == currentIndex);
            currentIndex = prevIndex;
        } else {
            currentIndex = (currentIndex - 1 + playlist.size()) % playlist.size();
        }

        return getCurrent();
    }

    public static boolean isAudioFile(File file) {
        if (file == null || !file.isFile()) return false;
        String name = file.getName().toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) return false;
        String ext = name.substring(dot + 1);
        return AUDIO_EXTS.contains(ext);
    }
}
