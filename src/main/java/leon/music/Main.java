package leon.music;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.JScrollPane;
import javax.swing.JToggleButton;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.co.caprica.vlcj.player.base.callback.AudioCallbackAdapter;

public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    // VLCJ
    private final MusicPlayer musicPlayer = new MusicPlayer();

    // visulaiser
    private AudioCallbackAdapter audioCallback;
    private volatile WaveVisualizer visualizer;

    // new mp3 path
    private String currentMediaPath = null;

    // GUI
    private JButton openButton;
    private JFrame frame;
    private JButton playPauseButton;
    private JButton stopButton;
    private JLabel statusLabel;
    private JSlider progressBar;
    private JSlider volumeSlider;
    private Timer progressTimer;

    // Playlist system
    private final PlaylistManager playlistManager = new PlaylistManager();

    private JList<File> trackList;
    private DefaultListModel<File> trackListModel;

    private JButton openFolderButton;
    private JButton nextButton;
    private JButton prevButton;
    private JButton visualizerModeButton;
    private JToggleButton shuffleToggle;
    private JToggleButton repeatToggle;

    private boolean isSeeking = false;
    private boolean repeatEnabled = false;

    private JLabel trackInfoLabel;
    private JLabel timeLabel;
    private JLabel playlistCountLabel;
    private WaveVisualizer wavePanel;

    public Main() {
        musicPlayer.init();
        musicPlayer.setOnFinished(() -> SwingUtilities.invokeLater(this::handlePlaybackFinished));

        createAndShowGui();
    }

    public void setVisualizer(WaveVisualizer panel) {
        this.visualizer = panel;
    }

    private void createAndShowGui() {
        frame = new JFrame("VLCJ Music Player");

        // Playlist
        trackListModel = new DefaultListModel<>();
        trackList = new JList<>(trackListModel);
        trackList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        trackList.setBackground(Theme.SURFACE_COLOR);
        trackList.setForeground(Theme.TEXT_COLOR);
        trackList.setFixedCellHeight(42);
        trackList.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        trackList.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int idx = trackList.locationToIndex(e.getPoint());
                    if (idx >= 0) {
                        playlistManager.setCurrentIndex(idx);
                        playCurrentFromPlaylist();  
                    }
                }
            }
        });

        trackList.setCellRenderer((list, value, index, isSelected, cellHasFocus) -> {
            String name = (value == null) ? "" : value.getName();
            boolean isCurrent = index == playlistManager.getCurrentIndex();

            JLabel label = new JLabel((isCurrent ? ">> " : "   ") + name);
            Theme.styleLabel(label);
            label.setOpaque(true);
            label.setBorder(BorderFactory.createEmptyBorder(7, 10, 7, 10));
            label.setToolTipText(name);

            if (isSelected) {
                label.setBackground(Theme.ACCENT_COLOR);
                label.setForeground(Theme.FG_COLOR);
            } else if (isCurrent) {
                label.setBackground(Theme.PROGRESS_BG);
                label.setForeground(Theme.ACCENT_ALT);
            } else {
                label.setBackground(Theme.SURFACE_COLOR);
                label.setForeground(Theme.TEXT_COLOR);
            }

            return label;
        });

        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.getContentPane().setBackground(Theme.BG_COLOR);

        // Buttons
        playPauseButton = new JButton("Play");
        Theme.styleButton(playPauseButton);

        stopButton = new JButton("Stop");
        Theme.styleSecondaryButton(stopButton);

        openButton = new JButton("Open File");
        Theme.styleSecondaryButton(openButton);

        statusLabel = new JLabel("Status: idle");
        Theme.styleMutedLabel(statusLabel);

        trackInfoLabel = new JLabel("No file loaded");
        Theme.styleTitleLabel(trackInfoLabel);

        timeLabel = new JLabel("00:00 / 00:00");
        Theme.styleMutedLabel(timeLabel);
        timeLabel.setHorizontalAlignment(SwingConstants.RIGHT);

        playlistCountLabel = new JLabel("0 tracks");
        Theme.styleMutedLabel(playlistCountLabel);

        // Playlist buttons
        prevButton = new JButton("Prev");
        Theme.styleSecondaryButton(prevButton);

        nextButton = new JButton("Next");
        Theme.styleSecondaryButton(nextButton);

        visualizerModeButton = new JButton("Waveform");
        Theme.styleSecondaryButton(visualizerModeButton);

        shuffleToggle = new JToggleButton("Shuffle");
        Theme.styleSecondaryButton(shuffleToggle);

        repeatToggle = new JToggleButton("Repeat");
        Theme.styleSecondaryButton(repeatToggle);

        openFolderButton = new JButton("Open Folder");
        Theme.styleSecondaryButton(openFolderButton);

        playPauseButton.addActionListener(e -> onPlayPause());
        openButton.addActionListener(e -> onOpenFile());
        stopButton.addActionListener(e -> onStop());

        prevButton.addActionListener(e -> {
            playlistManager.previous();
            playCurrentFromPlaylist();
        });

        nextButton.addActionListener(e -> {
            playlistManager.next();
            playCurrentFromPlaylist();
        });

        shuffleToggle.addActionListener(e -> {
            playlistManager.setShuffleEnabled(shuffleToggle.isSelected());
            statusLabel.setText("Status: shuffle " +
                    (shuffleToggle.isSelected() ? "on" : "off"));
        });

        repeatToggle.addActionListener(e -> {
            repeatEnabled = repeatToggle.isSelected();
            statusLabel.setText("Status: repeat " +
                    (repeatEnabled ? "on" : "off"));
        });

        openFolderButton.addActionListener(e -> onOpenFolder());

        JPanel topPanel = new JPanel(new BorderLayout(14, 4));
        Theme.stylePanel(topPanel);
        Theme.addBottomDivider(topPanel);

        JLabel nowPlayingLabel = new JLabel("Now Playing");
        Theme.styleMutedLabel(nowPlayingLabel);

        JPanel titlePanel = new JPanel(new BorderLayout(0, 4));
        titlePanel.setBackground(Theme.BG_COLOR);
        titlePanel.add(nowPlayingLabel, BorderLayout.NORTH);
        titlePanel.add(trackInfoLabel, BorderLayout.CENTER);

        JPanel statusPanel = new JPanel(new BorderLayout(0, 4));
        statusPanel.setBackground(Theme.BG_COLOR);
        statusPanel.add(statusLabel, BorderLayout.CENTER);
        statusPanel.add(timeLabel, BorderLayout.SOUTH);

        topPanel.add(titlePanel, BorderLayout.CENTER);
        topPanel.add(statusPanel, BorderLayout.EAST);

        // Button panel
        JPanel buttonPanel = new JPanel(new GridLayout(0, 4, 8, 8));
        Theme.stylePanel(buttonPanel);
        buttonPanel.add(prevButton);
        buttonPanel.add(playPauseButton);
        buttonPanel.add(nextButton);
        buttonPanel.add(stopButton);
        buttonPanel.add(openButton);
        buttonPanel.add(openFolderButton);
        buttonPanel.add(shuffleToggle);
        buttonPanel.add(repeatToggle);
        buttonPanel.add(visualizerModeButton);

        volumeSlider = new JSlider(0, 100, 100);
        Theme.styleProgressBar(volumeSlider);
        volumeSlider.setPreferredSize(new Dimension(160, 28));

        volumeSlider.addChangeListener(e -> {
            if (musicPlayer != null && !volumeSlider.getValueIsAdjusting()) {
                int vol = volumeSlider.getValue();
                musicPlayer.setVolume(volumeSlider.getValue());
                log.debug("Volume set to: {}", vol);
            }
        });

        // wave panel
        wavePanel = new WaveVisualizer();
        wavePanel.setGain(1.6f);
        musicPlayer.setVisualizer(wavePanel);
        visualizerModeButton.addActionListener(e -> {
            String modeName = wavePanel.nextMode();
            visualizerModeButton.setText(modeName);
            statusLabel.setText("Status: visualizer " + modeName.toLowerCase());
        });

        // Center panel
        JPanel centerPanel = new JPanel(new BorderLayout(0, 18));
        Theme.stylePanel(centerPanel);

        JPanel volumePanel = new JPanel(new BorderLayout(10, 0));
        Theme.stylePanel(volumePanel);
        JLabel volumeLabel = new JLabel("Volume");
        Theme.styleMutedLabel(volumeLabel);
        volumePanel.add(volumeLabel, BorderLayout.WEST);
        volumePanel.add(volumeSlider, BorderLayout.CENTER);

        JPanel controlPanel = new JPanel(new BorderLayout(0, 10));
        Theme.styleSurfacePanel(controlPanel);
        controlPanel.add(buttonPanel, BorderLayout.CENTER);
        controlPanel.add(volumePanel, BorderLayout.SOUTH);

        centerPanel.add(wavePanel, BorderLayout.CENTER);
        centerPanel.add(controlPanel, BorderLayout.SOUTH);

        // Progress bar
        progressBar = new JSlider(0, 1000, 0);
        Theme.styleProgressBar(progressBar);

        // listener to handle seeking
        progressBar.addChangeListener(e -> {
            if (musicPlayer == null) {
                return;
            }

            if (progressBar.getValueIsAdjusting()) {
                // user is currently dragging pause timer updates
                isSeeking = true;
            } else if (isSeeking) {
                // user just released the slider perform seek
                long length = musicPlayer.getLengthMs();
                if (length > 0) {
                    double fraction = progressBar.getValue() / (double) progressBar.getMaximum();
                    long newTime = (long) (fraction * length);
                    log.debug("Seeking to time: {} ms", newTime);
                    musicPlayer.seekToMs(newTime);
                }
                isSeeking = false;
            }
        });

        // Layout
        frame.setLayout(new BorderLayout());
        frame.add(topPanel, BorderLayout.NORTH);

        JPanel progressPanel = new JPanel(new BorderLayout());
        Theme.stylePanel(progressPanel);
        progressPanel.add(progressBar, BorderLayout.CENTER);
        frame.add(progressPanel, BorderLayout.SOUTH);

        // Playlist
        JScrollPane scrollPane = new JScrollPane(trackList);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        Theme.styleScrollPane(scrollPane);

        JLabel playlistTitleLabel = new JLabel("Library");
        Theme.styleTitleLabel(playlistTitleLabel);

        JPanel playlistHeaderPanel = new JPanel(new BorderLayout(8, 0));
        playlistHeaderPanel.setBackground(Theme.SURFACE_COLOR);
        playlistHeaderPanel.setBorder(BorderFactory.createEmptyBorder(4, 4, 10, 4));
        playlistHeaderPanel.add(playlistTitleLabel, BorderLayout.WEST);
        playlistHeaderPanel.add(playlistCountLabel, BorderLayout.EAST);

        JPanel playlistPanel = new JPanel(new BorderLayout(0, 8));
        Theme.styleSurfacePanel(playlistPanel);
        playlistPanel.setMinimumSize(new Dimension(210, 260));
        playlistPanel.setPreferredSize(new Dimension(280, 0));
        playlistPanel.add(playlistHeaderPanel, BorderLayout.NORTH);
        playlistPanel.add(scrollPane, BorderLayout.CENTER);

        centerPanel.setMinimumSize(new Dimension(430, 300));

        JSplitPane contentSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, playlistPanel, centerPanel);
        contentSplitPane.setBorder(null);
        contentSplitPane.setDividerSize(5);
        contentSplitPane.setResizeWeight(0.28);
        contentSplitPane.setContinuousLayout(true);
        contentSplitPane.setBackground(Theme.BG_COLOR);
        frame.add(contentSplitPane, BorderLayout.CENTER);

        // Progress timer
        progressTimer = new Timer(500, e -> updateProgress());
        progressTimer.start();

        frame.setMinimumSize(new Dimension(700, 480));
        frame.setSize(920, 580);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (progressTimer != null) {
                    progressTimer.stop();
                }
                if (wavePanel != null) {
                    wavePanel.dispose();
                }
                musicPlayer.release();
            }
        });
    }

    private void onPlayPause() {
        if (currentMediaPath == null) {
            JOptionPane.showMessageDialog(frame, "Open a file first.");
            return;
        }

        if (musicPlayer.isPlaying()) {
            musicPlayer.pause();
            statusLabel.setText("Paused");
            playPauseButton.setText("Play");
            trackList.repaint();
            return;
        }

        if (musicPlayer.isPaused()) {
            musicPlayer.pause();
            statusLabel.setText("Playing");
            playPauseButton.setText("Pause");
            if (!progressTimer.isRunning())
                progressTimer.start();
            return;
        }

        boolean started = musicPlayer.play(currentMediaPath);
        if (started) {
            statusLabel.setText("Playing");
            playPauseButton.setText("Pause");
            if (!progressTimer.isRunning())
                progressTimer.start();
        } else {
            statusLabel.setText("Failed to play");
        }
    }

    private void onStop() {
        musicPlayer.stopByUser();
        statusLabel.setText("Stopped");
        playPauseButton.setText("Play");

        progressBar.setValue(0);
        timeLabel.setText("00:00 / 00:00");
    }

    private void onOpenFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Choose an audio file");

        chooser.setFileFilter(new FileNameExtensionFilter(
                "Audio Files (mp3, wav, flac, ogg, aac, m4a)",
                "mp3", "wav", "flac", "ogg", "aac", "m4a"));

        int result = chooser.showOpenDialog(frame);

        if (result == JFileChooser.APPROVE_OPTION) {
            File selected = chooser.getSelectedFile();
            if (selected == null)
                return;
            if (!PlaylistManager.isAudioFile(selected)) {
                statusLabel.setText("Status: unsupported audio file");
                return;
            }

            loadSingleFileIntoPlaylist(selected);
            currentMediaPath = selected.getAbsolutePath();

            updateMetadata(selected); // see method below

            progressBar.setValue(0);
            timeLabel.setText("00:00 / 00:00");

            if (musicPlayer.isPlaying() || musicPlayer.isPaused()) {
                musicPlayer.stopByUser();
            }

            statusLabel.setText("Status: loading " + selected.getName());

            boolean started = musicPlayer.play(currentMediaPath);
            log.info("media().play(...) returned: {}", started);

            if (started) {
                playPauseButton.setText("Pause");
                statusLabel.setText("Status: playing " + selected.getName());
            } else {
                statusLabel.setText("Status: failed to start playback");
            }
        }
    }

    private void updateMetadata(File audioFile) {
        try {
            AudioFile af = AudioFileIO.read(audioFile);
            Tag tag = af.getTag();

            String title = audioFile.getName();
            String artist = "";
            String album = "";

            if (tag != null) {
                String t = tag.getFirst(FieldKey.TITLE);
                String ar = tag.getFirst(FieldKey.ARTIST);
                String al = tag.getFirst(FieldKey.ALBUM);

                if (t != null && !t.isBlank())
                    title = t;
                if (ar != null && !ar.isBlank())
                    artist = ar;
                if (al != null && !al.isBlank())
                    album = al;
            }

            StringBuilder sb = new StringBuilder(title);
            if (!artist.isEmpty())
                sb.append(" - ").append(artist);
            if (!album.isEmpty())
                sb.append(" [").append(album).append("]");

            trackInfoLabel.setText(sb.toString());
        } catch (Exception e) {

            trackInfoLabel.setText(audioFile.getName());
            log.warn("Could not read metadata for {}: {}", audioFile.getName(), e.getMessage());
        }
    }

    private void loadSingleFileIntoPlaylist(File selected) {
        var files = playlistManager.loadFile(selected);

        trackListModel.clear();
        for (File f : files)
            trackListModel.addElement(f);

        playlistCountLabel.setText(playlistManager.size() + " tracks");
        if (!files.isEmpty()) {
            trackList.setSelectedIndex(0);
        }
    }

    private String formatTime(long ms) {
        long totalSeconds = ms / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    private void updateProgress() {
        if (isSeeking)
            return;

        long length = musicPlayer.getLengthMs();
        long time = musicPlayer.getTimeMs();

        if (length > 0) {
            int value = (int) ((time * 1000) / length);
            progressBar.setValue(value);
            timeLabel.setText(formatTime(time) + " / " + formatTime(length));
        }
    }

    private void onOpenFolder() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Choose a music folder");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

        int result = chooser.showOpenDialog(frame);
        if (result != JFileChooser.APPROVE_OPTION)
            return;

        File folder = chooser.getSelectedFile();
        if (folder == null)
            return;

        var files = playlistManager.loadFolder(folder);

        trackListModel.clear();
        for (File f : files)
            trackListModel.addElement(f);

        playlistCountLabel.setText(playlistManager.size() + " tracks");

        if (playlistManager.size() == 0) {
            if (musicPlayer.isPlaying() || musicPlayer.isPaused()) {
                musicPlayer.stopByUser();
            }
            statusLabel.setText("Status: no audio files found");
            trackInfoLabel.setText("No audio files in folder");
            currentMediaPath = null;
            playPauseButton.setText("Play");
            progressBar.setValue(0);
            timeLabel.setText("00:00 / 00:00");
            return;
        }

        trackList.setSelectedIndex(0);
        statusLabel.setText("Status: loaded " + playlistManager.size() + " tracks");
        playCurrentFromPlaylist();
    }

    private void playCurrentFromPlaylist() {
        File current = playlistManager.getCurrent();
        if (current == null)
            return;

        currentMediaPath = current.getAbsolutePath();

        int idx = playlistManager.getCurrentIndex();
        if (idx >= 0 && idx < trackListModel.getSize()) {
            trackList.setSelectedIndex(idx);
            trackList.ensureIndexIsVisible(idx);
        }

        updateMetadata(current);
        progressBar.setValue(0);
        timeLabel.setText("00:00 / 00:00");

        if (musicPlayer.isPlaying() || musicPlayer.isPaused()) {
            musicPlayer.stopByUser();
        }
        boolean started = musicPlayer.play(currentMediaPath);

        if (started) {
            playPauseButton.setText("Pause");
            statusLabel.setText("Status: playing " + current.getName());
            if (!progressTimer.isRunning())
                progressTimer.start();
        } else {
            statusLabel.setText("Status: failed to start playback");
        }

        trackList.repaint();

    }

    private void playNextFromPlaylist() {
        if (playlistManager.size() == 0)
            return;

        playlistManager.next();

        int idx = playlistManager.getCurrentIndex();
        if (idx >= 0 && idx < trackListModel.getSize()) {
            trackList.setSelectedIndex(idx);
            trackList.ensureIndexIsVisible(idx);
        }

        playCurrentFromPlaylist();
    }

    private void handlePlaybackFinished() {
        if (repeatEnabled) {
            replayCurrentTrack();
            return;
        }

        if (!playlistManager.hasAutomaticNext()) {
            stopAfterPlaybackFinished();
            return;
        }

        playNextFromPlaylist();
    }

    private void stopAfterPlaybackFinished() {
        progressBar.setValue(0);
        timeLabel.setText("00:00 / 00:00");
        playPauseButton.setText("Play");
        statusLabel.setText("Status: finished");
        trackList.repaint();
    }

    private void replayCurrentTrack() {
        if (currentMediaPath == null)
            return;

        progressBar.setValue(0);
        timeLabel.setText("00:00 / 00:00");

        boolean started = musicPlayer.play(currentMediaPath);

        if (started) {
            playPauseButton.setText("Pause");
            statusLabel.setText("Status: repeating");
            if (!progressTimer.isRunning())
                progressTimer.start();
        } else {
            statusLabel.setText("Status: failed to start playback");
        }
    }

    public static void main(String[] args) {
        log.info("Starting VLCJ Music Player...");
        SwingUtilities.invokeLater(Main::new);
    }
}
