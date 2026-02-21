package leon.music;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.JScrollPane;
import javax.swing.JToggleButton;
import javax.swing.ListSelectionModel;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;

public class Main {

    // VLCJ
    private final MusicPlayer musicPlayer = new MusicPlayer();

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
    private JToggleButton shuffleToggle;

    private boolean isSeeking = false;

    private JLabel trackInfoLabel;
    private JLabel timeLabel;

    public Main() {
        musicPlayer.init();

        musicPlayer.setOnFinished(() -> SwingUtilities.invokeLater(this::playNextFromPlaylist));

        createAndShowGui();
    }

    private void createAndShowGui() {
        frame = new JFrame("VLCJ Music Player");

        // Playlist
        trackListModel = new DefaultListModel<>();
        trackList = new JList<>(trackListModel);
        trackList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        trackList.setBackground(Theme.BG_COLOR);
        trackList.setForeground(Theme.TEXT_COLOR);

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

            JLabel label = new JLabel((isCurrent ? "▶ " : "  ") + name);
            Theme.styleLabel(label);
            label.setOpaque(true);

            if (isSelected) {
                label.setBackground(Theme.ACCENT_COLOR);
            } else if (isCurrent) {
                label.setBackground(Theme.PROGRESS_BG);
            } else {
                label.setBackground(Theme.BG_COLOR);
            }

            return label;
        });

        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.getContentPane().setBackground(Theme.BG_COLOR);

        // Buttons
        playPauseButton = new JButton("Play");
        Theme.styleButton(playPauseButton);

        stopButton = new JButton("Stop");
        Theme.styleButton(stopButton);

        openButton = new JButton("Open");
        Theme.styleButton(openButton);

        statusLabel = new JLabel("Status: idle");
        Theme.styleLabel(statusLabel);

        trackInfoLabel = new JLabel("No file loaded");
        Theme.styleLabel(trackInfoLabel);

        timeLabel = new JLabel("00:00 / 00:00");
        Theme.styleLabel(timeLabel);

        // Playlist buttons
        prevButton = new JButton("Prev");
        Theme.styleButton(prevButton);

        nextButton = new JButton("Next");
        Theme.styleButton(nextButton);

        shuffleToggle = new JToggleButton("Shuffle");
        Theme.styleButton(shuffleToggle);

        openFolderButton = new JButton("Open Folder");
        Theme.styleButton(openFolderButton);

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

        openFolderButton.addActionListener(e -> onOpenFolder());

        JPanel topPanel = new JPanel(new BorderLayout());
        Theme.stylePanel(topPanel);
        topPanel.add(trackInfoLabel, BorderLayout.NORTH);
        topPanel.add(statusLabel, BorderLayout.CENTER);
        topPanel.add(timeLabel, BorderLayout.SOUTH);

        // Button panel
        JPanel buttonPanel = new JPanel();
        Theme.stylePanel(buttonPanel);
        buttonPanel.add(prevButton);
        buttonPanel.add(playPauseButton);
        buttonPanel.add(nextButton);
        buttonPanel.add(stopButton);
        buttonPanel.add(openButton);
        buttonPanel.add(openFolderButton);
        buttonPanel.add(shuffleToggle);

        volumeSlider = new JSlider(0, 100, 100);
        Theme.styleProgressBar(volumeSlider);
        volumeSlider.setPreferredSize(new Dimension(120, 20));

        volumeSlider.addChangeListener(e -> {
            if (musicPlayer != null && !volumeSlider.getValueIsAdjusting()) {
                int vol = volumeSlider.getValue();
                musicPlayer.setVolume(volumeSlider.getValue());
                System.out.println("Volume set to: " + vol);
            }
        });

        // Center panel
        JPanel centerPanel = new JPanel(new BorderLayout());
        Theme.stylePanel(centerPanel);
        centerPanel.add(buttonPanel, BorderLayout.CENTER);
        centerPanel.add(volumeSlider, BorderLayout.SOUTH);

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
                    System.out.println("Seeking to time: " + newTime + " ms");
                    musicPlayer.seekToMs(newTime);
                }
                isSeeking = false;
            }
        });

        // Layout
        frame.setLayout(new BorderLayout());
        frame.add(topPanel, BorderLayout.NORTH);
        frame.add(centerPanel, BorderLayout.CENTER);
        frame.add(progressBar, BorderLayout.SOUTH);

        // Playlist
        JScrollPane scrollPane = new JScrollPane(trackList);
        scrollPane.setPreferredSize(new Dimension(220, 0));
        frame.add(scrollPane, BorderLayout.WEST);

        // Progress timer
        progressTimer = new Timer(500, e -> updateProgress());
        progressTimer.start();

        frame.setSize(460, 210);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
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

            currentMediaPath = selected.getAbsolutePath();

            updateMetadata(selected); // see method below

            progressBar.setValue(0);
            timeLabel.setText("00:00 / 00:00");

            if (musicPlayer.isPlaying()) {
                musicPlayer.stop();
            }

            statusLabel.setText("Status: loading " + selected.getName());

            boolean started = musicPlayer.play(currentMediaPath);
            System.out.println("media().play(...) returned: " + started);

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
            System.out.println("Could not read metadata: " + e.getMessage());
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

        if (playlistManager.size() == 0) {
            statusLabel.setText("Status: no audio files found");
            trackInfoLabel.setText("No audio files in folder");
            currentMediaPath = null;
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

        musicPlayer.stop();
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

    public static void main(String[] args) {
        System.setProperty("jna.library.path", "E:\\VLC");
        SwingUtilities.invokeLater(Main::new);
    }
}
