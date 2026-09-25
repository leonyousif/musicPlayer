package leon.music.controller;

import leon.music.WaveVisualizer;
import leon.music.model.PlaybackState;
import leon.music.model.RepeatMode;
import leon.music.model.Track;
import leon.music.service.AudioPlayer;
import leon.music.service.MetadataService;
import leon.music.service.PlaylistService;
import leon.music.ui.MainWindow;
import leon.music.ui.PlaybackControlsPanel;
import leon.music.ui.PlaylistPanel;
import leon.music.ui.ProgressBarPanel;
import leon.music.ui.TrackInfoPanel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.io.File;
import java.util.List;
import java.util.Objects;

/**
 * Central controller coordinating audio playback, playlist management, metadata extraction,
 * and modular Swing view components strictly on the Swing Event Dispatch Thread (EDT).
 */
public class PlayerController {

    private static final Logger log = LoggerFactory.getLogger(PlayerController.class);

    private final AudioPlayer audioPlayer;
    private final PlaylistService playlistService;
    private final MetadataService metadataService;

    private MainWindow mainWindow;
    private TrackInfoPanel trackInfoPanel;
    private PlaybackControlsPanel playbackControlsPanel;
    private PlaylistPanel playlistPanel;
    private ProgressBarPanel progressBarPanel;
    private WaveVisualizer waveVisualizer;

    private volatile PlaybackState playbackState = PlaybackState.IDLE;
    private boolean isSeeking = false;
    private Timer progressTimer;

    public PlayerController(AudioPlayer audioPlayer,
                            PlaylistService playlistService,
                            MetadataService metadataService) {
        this.audioPlayer = Objects.requireNonNull(audioPlayer, "audioPlayer must not be null");
        this.playlistService = Objects.requireNonNull(playlistService, "playlistService must not be null");
        this.metadataService = Objects.requireNonNull(metadataService, "metadataService must not be null");

        this.progressTimer = new Timer(500, e -> updateProgress());
    }

    /**
     * Binds the MainWindow and its child view panels to this controller.
     */
    public void attachView(MainWindow window) {
        this.mainWindow = window;
        if (window != null) {
            this.trackInfoPanel = window.getTrackInfoPanel();
            this.playbackControlsPanel = window.getPlaybackControlsPanel();
            this.playlistPanel = window.getPlaylistPanel();
            this.progressBarPanel = window.getProgressBarPanel();
            this.waveVisualizer = window.getWaveVisualizer();

            if (waveVisualizer != null) {
                audioPlayer.setVisualizer(waveVisualizer);
                waveVisualizer.setPlaybackActive(playbackState == PlaybackState.PLAYING);
            }
        }
    }

    /**
     * Initializes player callbacks and starts the progress timer.
     */
    public void init() {
        audioPlayer.init();
        audioPlayer.setOnFinished(() -> runOnEdt(this::handlePlaybackFinished));
        progressTimer.start();
        log.info("PlayerController initialized successfully");
    }

    public void playOrPause() {
        if (playlistService.isEmpty()) {
            if (mainWindow != null) {
                JOptionPane.showMessageDialog(mainWindow, "Open a file first.");
            }
            return;
        }

        if (audioPlayer.isPlaying()) {
            audioPlayer.pause();
            setPlaybackState(PlaybackState.PAUSED);
            runOnEdt(() -> {
                if (playbackControlsPanel != null) playbackControlsPanel.setPlaybackState(PlaybackState.PAUSED);
                if (trackInfoPanel != null) trackInfoPanel.setStatus("Paused");
                if (playlistPanel != null) playlistPanel.repaint();
            });
            return;
        }

        if (audioPlayer.isPaused()) {
            audioPlayer.pause();
            setPlaybackState(PlaybackState.PLAYING);
            runOnEdt(() -> {
                if (playbackControlsPanel != null) playbackControlsPanel.setPlaybackState(PlaybackState.PLAYING);
                if (trackInfoPanel != null) trackInfoPanel.setStatus("Playing");
                if (!progressTimer.isRunning()) progressTimer.start();
                if (playlistPanel != null) playlistPanel.repaint();
            });
            return;
        }

        var currentOpt = playlistService.getCurrent();
        if (currentOpt.isEmpty() && playlistService.size() > 0) {
            playlistService.setCurrentIndex(0);
            currentOpt = playlistService.getCurrent();
        }

        currentOpt.ifPresent(this::startPlaybackForTrack);
    }

    public void stop() {
        audioPlayer.stopByUser();
        setPlaybackState(PlaybackState.STOPPED);
        runOnEdt(() -> {
            if (progressBarPanel != null) progressBarPanel.reset();
            if (playbackControlsPanel != null) playbackControlsPanel.setPlaybackState(PlaybackState.STOPPED);
            if (trackInfoPanel != null) trackInfoPanel.setStatus("Stopped");
            if (playlistPanel != null) playlistPanel.repaint();
        });
    }

    public void previous() {
        var prevOpt = playlistService.previous();
        prevOpt.ifPresent(this::startPlaybackForTrack);
    }

    public void next() {
        var nextOpt = playlistService.next();
        nextOpt.ifPresent(this::startPlaybackForTrack);
    }

    public void playTrackAtIndex(int index) {
        if (index < 0 || index >= playlistService.size()) {
            return;
        }
        playlistService.setCurrentIndex(index);
        playlistService.getCurrent().ifPresent(this::startPlaybackForTrack);
    }

    private void startPlaybackForTrack(Track track) {
        if (audioPlayer.isPlaying() || audioPlayer.isPaused()) {
            audioPlayer.stopByUser();
        }

        runOnEdt(() -> {
            if (progressBarPanel != null) progressBarPanel.reset();
            if (trackInfoPanel != null) trackInfoPanel.setTrack(track);
            if (playlistPanel != null) {
                playlistPanel.setCurrentIndex(playlistService.getCurrentIndex());
                playlistPanel.repaint();
            }
        });

        String path = track.path().toAbsolutePath().toString();
        boolean started = audioPlayer.play(path);
        if (started) {
            setPlaybackState(PlaybackState.PLAYING);
            runOnEdt(() -> {
                if (playbackControlsPanel != null) playbackControlsPanel.setPlaybackState(PlaybackState.PLAYING);
                if (trackInfoPanel != null) trackInfoPanel.setStatus("Status: playing " + track.title());
                if (!progressTimer.isRunning()) progressTimer.start();
            });
        } else {
            setPlaybackState(PlaybackState.STOPPED);
            runOnEdt(() -> {
                if (playbackControlsPanel != null) playbackControlsPanel.setPlaybackState(PlaybackState.STOPPED);
                if (trackInfoPanel != null) trackInfoPanel.setStatus("Status: failed to start playback");
            });
        }
    }

    public void handlePlaybackFinished() {
        RepeatMode mode = playlistService.getRepeatMode();
        if (mode == RepeatMode.ONE) {
            replayCurrentTrack();
            return;
        }

        if (!playlistService.hasAutomaticNext()) {
            stopAfterPlaybackFinished();
            return;
        }

        next();
    }

    private void stopAfterPlaybackFinished() {
        setPlaybackState(PlaybackState.STOPPED);
        runOnEdt(() -> {
            if (progressBarPanel != null) progressBarPanel.reset();
            if (playbackControlsPanel != null) playbackControlsPanel.setPlaybackState(PlaybackState.STOPPED);
            if (trackInfoPanel != null) trackInfoPanel.setStatus("Status: finished");
            if (playlistPanel != null) playlistPanel.repaint();
        });
    }

    private void replayCurrentTrack() {
        playlistService.getCurrent().ifPresent(track -> {
            runOnEdt(() -> {
                if (progressBarPanel != null) progressBarPanel.reset();
            });

            boolean started = audioPlayer.play(track.path().toAbsolutePath().toString());
            if (started) {
                setPlaybackState(PlaybackState.PLAYING);
                runOnEdt(() -> {
                    if (playbackControlsPanel != null) playbackControlsPanel.setPlaybackState(PlaybackState.PLAYING);
                    if (trackInfoPanel != null) trackInfoPanel.setStatus("Status: repeating");
                    if (!progressTimer.isRunning()) progressTimer.start();
                });
            } else {
                setPlaybackState(PlaybackState.STOPPED);
                runOnEdt(() -> {
                    if (playbackControlsPanel != null) playbackControlsPanel.setPlaybackState(PlaybackState.STOPPED);
                    if (trackInfoPanel != null) trackInfoPanel.setStatus("Status: failed to start playback");
                });
            }
        });
    }

    public void setVolume(int volume) {
        audioPlayer.setVolume(volume);
    }

    public void setSeeking(boolean seeking) {
        this.isSeeking = seeking;
    }

    public boolean isSeeking() {
        return isSeeking;
    }

    public void seekToFraction(double fraction) {
        long length = audioPlayer.getLengthMs();
        if (length > 0) {
            long newTime = (long) (fraction * length);
            log.debug("Seeking to time: {} ms", newTime);
            audioPlayer.seekToMs(newTime);
        }
        this.isSeeking = false;
    }

    public void setShuffle(boolean enabled) {
        playlistService.setShuffleEnabled(enabled);
        runOnEdt(() -> {
            if (trackInfoPanel != null) trackInfoPanel.setStatus("Status: shuffle " + (enabled ? "on" : "off"));
            if (playbackControlsPanel != null) playbackControlsPanel.setShuffle(enabled);
        });
    }

    public void setRepeat(boolean enabled) {
        RepeatMode mode = enabled ? RepeatMode.ALL : RepeatMode.OFF;
        playlistService.setRepeatMode(mode);
        runOnEdt(() -> {
            if (trackInfoPanel != null) trackInfoPanel.setStatus("Status: repeat " + (enabled ? "on" : "off"));
            if (playbackControlsPanel != null) playbackControlsPanel.setRepeat(enabled, mode.getShortLabel());
        });
    }

    public void toggleRepeat() {
        RepeatMode newMode = playlistService.cycleRepeatMode();
        runOnEdt(() -> {
            if (trackInfoPanel != null) trackInfoPanel.setStatus("Status: repeat " + newMode.getDisplayName().toLowerCase());
            if (playbackControlsPanel != null) playbackControlsPanel.setRepeat(newMode != RepeatMode.OFF, newMode.getShortLabel());
        });
    }

    public void cycleVisualizerMode() {
        if (waveVisualizer != null) {
            String modeName = waveVisualizer.nextMode();
            runOnEdt(() -> {
                if (playbackControlsPanel != null) playbackControlsPanel.setVisualizerModeText(modeName);
                if (trackInfoPanel != null) trackInfoPanel.setStatus("Status: visualizer " + modeName.toLowerCase());
            });
        }
    }

    public void openFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Choose an audio file");
        chooser.setFileFilter(new FileNameExtensionFilter(
                "Audio Files (mp3, wav, flac, ogg, aac, m4a)",
                "mp3", "wav", "flac", "ogg", "aac", "m4a"));

        int result = chooser.showOpenDialog(mainWindow);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selected = chooser.getSelectedFile();
            if (selected != null) {
                loadFile(selected);
            }
        }
    }

    public void loadFile(File file) {
        if (!PlaylistService.isAudioFile(file)) {
            runOnEdt(() -> {
                if (trackInfoPanel != null) trackInfoPanel.setStatus("Status: unsupported audio file");
            });
            return;
        }

        Track enriched = metadataService.extractMetadata(file);
        playlistService.loadTrack(enriched);

        runOnEdt(() -> {
            if (playlistPanel != null) {
                playlistPanel.setTracks(playlistService.getTracks());
                playlistPanel.setCurrentIndex(0);
            }
            if (trackInfoPanel != null) {
                trackInfoPanel.setTrack(enriched);
            }
        });

        startPlaybackForTrack(enriched);
    }

    public void openFolder() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Choose a music folder");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

        int result = chooser.showOpenDialog(mainWindow);
        if (result == JFileChooser.APPROVE_OPTION) {
            File folder = chooser.getSelectedFile();
            if (folder != null) {
                loadFolder(folder);
            }
        }
    }

    public void loadFolder(File folder) {
        List<Track> loaded = playlistService.loadFolder(folder);

        runOnEdt(() -> {
            if (playlistPanel != null) {
                playlistPanel.setTracks(loaded);
            }
        });

        if (loaded.isEmpty()) {
            if (audioPlayer.isPlaying() || audioPlayer.isPaused()) {
                audioPlayer.stopByUser();
            }
            setPlaybackState(PlaybackState.STOPPED);
            runOnEdt(() -> {
                if (playbackControlsPanel != null) playbackControlsPanel.setPlaybackState(PlaybackState.STOPPED);
                if (trackInfoPanel != null) {
                    trackInfoPanel.setTrack(null);
                    trackInfoPanel.setStatus("Status: no audio files found");
                }
                if (progressBarPanel != null) progressBarPanel.reset();
            });
            return;
        }

        runOnEdt(() -> {
            if (trackInfoPanel != null) {
                trackInfoPanel.setStatus("Status: loaded " + loaded.size() + " tracks");
            }
        });

        startPlaybackForTrack(loaded.get(0));

        // Asynchronously enrich all tracks with metadata and update playlist panel dynamically
        for (int i = 0; i < loaded.size(); i++) {
            final int index = i;
            final Track track = loaded.get(i);
            metadataService.extractMetadataAsync(track).thenAccept(enriched -> runOnEdt(() -> {
                if (playlistPanel != null && index < playlistService.size()) {
                    playlistPanel.updateTrack(index, enriched);
                    if (index == playlistService.getCurrentIndex() && trackInfoPanel != null) {
                        trackInfoPanel.setTrack(enriched);
                    }
                }
            }));
        }
    }

    private void updateProgress() {
        if (isSeeking || !audioPlayer.isPlaying()) {
            return;
        }

        long length = audioPlayer.getLengthMs();
        long time = audioPlayer.getTimeMs();

        if (length > 0) {
            runOnEdt(() -> {
                if (progressBarPanel != null) {
                    progressBarPanel.setProgress(time, length);
                }
            });
        }
    }

    public void shutdown() {
        if (progressTimer != null) {
            progressTimer.stop();
        }
        if (waveVisualizer != null) {
            waveVisualizer.dispose();
        }
        audioPlayer.release();
    }

    private void setPlaybackState(PlaybackState newState) {
        this.playbackState = newState;
        if (waveVisualizer != null) {
            waveVisualizer.setPlaybackActive(newState == PlaybackState.PLAYING);
        }
    }

    public WaveVisualizer getWaveVisualizer() {
        return waveVisualizer;
    }

    public void setWaveVisualizer(WaveVisualizer visualizer) {
        this.waveVisualizer = visualizer;
        if (visualizer != null) {
            visualizer.setPlaybackActive(playbackState == PlaybackState.PLAYING);
        }
    }

    public PlaybackState getPlaybackState() {
        return playbackState;
    }

    public AudioPlayer getAudioPlayer() {
        return audioPlayer;
    }

    public PlaylistService getPlaylistService() {
        return playlistService;
    }

    public MetadataService getMetadataService() {
        return metadataService;
    }

    private void runOnEdt(Runnable task) {
        if (SwingUtilities.isEventDispatchThread()) {
            task.run();
        } else {
            SwingUtilities.invokeLater(task);
        }
    }
}
