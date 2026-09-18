package leon.music.model;

/**
 * Represents the current state of the audio playback engine.
 */
public enum PlaybackState {
    /** No media is loaded, or the engine is uninitialized. */
    IDLE,

    /** Audio is actively playing. */
    PLAYING,

    /** Playback is currently paused at a specific timestamp. */
    PAUSED,

    /** Playback has been stopped, resetting current position. */
    STOPPED
}
