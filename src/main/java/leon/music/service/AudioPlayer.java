package leon.music.service;

import leon.music.WaveVisualizer;

/**
 * Abstraction for the audio playback engine.
 */
public interface AudioPlayer {

    /**
     * Initializes the underlying audio player subsystem and audio line.
     */
    void init();

    /**
     * Returns true if the player subsystem is initialized and ready.
     */
    boolean isReady();

    /**
     * Returns true if audio is actively playing.
     */
    boolean isPlaying();

    /**
     * Returns true if playback is currently paused.
     */
    boolean isPaused();

    /**
     * Plays the audio file at the specified media path.
     *
     * @param mediaPath absolute path to the media file
     * @return true if playback was successfully started
     */
    boolean play(String mediaPath);

    /**
     * Toggles pause/resume state.
     */
    void pause();

    /**
     * Stops playback and resets state.
     */
    void stop();

    /**
     * Stops playback requested directly by the user, ignoring subsequent finished callbacks.
     */
    void stopByUser();

    /**
     * Sets the playback volume (0 to 100).
     */
    void setVolume(int volume);

    /**
     * Returns total media duration in milliseconds, or 0 if unknown.
     */
    long getLengthMs();

    /**
     * Returns current playback timestamp in milliseconds.
     */
    long getTimeMs();

    /**
     * Seeks playback to the specified timestamp in milliseconds.
     */
    void seekToMs(long newTimeMs);

    /**
     * Registers a callback to be invoked when media playback reaches the end.
     */
    void setOnFinished(Runnable onFinished);

    /**
     * Connects a WaveVisualizer component to receive real-time audio samples.
     */
    void setVisualizer(WaveVisualizer visualizer);

    /**
     * Releases audio lines and native player resources.
     */
    void release();
}

