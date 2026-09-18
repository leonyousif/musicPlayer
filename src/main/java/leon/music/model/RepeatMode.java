package leon.music.model;

/**
 * Repeat modes for playlist playback control.
 */
public enum RepeatMode {
    /**
     * Playback stops when reaching the end of the playlist.
     */
    OFF("Off", "Off"),

    /**
     * Loops back to the start of the playlist when reaching the end.
     */
    ALL("Repeat All", "All"),

    /**
     * Repeats the currently playing track indefinitely.
     */
    ONE("Repeat One", "1");

    private final String displayName;
    private final String shortLabel;

    RepeatMode(String displayName, String shortLabel) {
        this.displayName = displayName;
        this.shortLabel = shortLabel;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getShortLabel() {
        return shortLabel;
    }

    /**
     * Cycles through repeat modes: OFF -> ALL -> ONE -> OFF.
     */
    public RepeatMode next() {
        return switch (this) {
            case OFF -> ALL;
            case ALL -> ONE;
            case ONE -> OFF;
        };
    }
}
