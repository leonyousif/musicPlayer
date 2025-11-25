package leon.music;

import java.awt.BorderLayout;
import java.awt.Dimension;
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

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;

import uk.co.caprica.vlcj.factory.MediaPlayerFactory;
import uk.co.caprica.vlcj.player.base.MediaPlayer;

public class Main {

    // VLCJ
    private MediaPlayerFactory factory;
    private MediaPlayer player;

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

    private boolean isSeeking = false;

    private JLabel trackInfoLabel;
    private JLabel timeLabel;

    public Main() {
        initVlcj();
        createAndShowGui();
    }

    private void initVlcj() {
        try {

            factory = new MediaPlayerFactory();
            player = factory.mediaPlayers().newMediaPlayer();
            player.audio().setVolume(100);
        } catch (Throwable t) {
            // If VLC native libs can't be loaded, show an error and exit
            JOptionPane.showMessageDialog(
                    null,
                    "Could not load VLC native libraries.\n" +
                            "Check that VLC is installed at E:\\VLC and is 64-bit.",
                    "VLC error",
                    JOptionPane.ERROR_MESSAGE);
            t.printStackTrace();
            System.exit(1);
        }
    }

    private void createAndShowGui() {
        frame = new JFrame("Leon VLCJ Music Player");
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

        playPauseButton.addActionListener(e -> onPlayPause());
        openButton.addActionListener(e -> onOpenFile());
        stopButton.addActionListener(e -> onStop());

        JPanel topPanel = new JPanel(new BorderLayout());
        Theme.stylePanel(topPanel);
        topPanel.add(trackInfoLabel, BorderLayout.NORTH);
        topPanel.add(statusLabel, BorderLayout.CENTER);
        topPanel.add(timeLabel, BorderLayout.SOUTH);

        // Button panel
        JPanel buttonPanel = new JPanel();
        Theme.stylePanel(buttonPanel);
        buttonPanel.add(playPauseButton);
        buttonPanel.add(stopButton);
        buttonPanel.add(openButton);

        volumeSlider = new JSlider(0, 100, 100);
        Theme.styleProgressBar(volumeSlider);
        volumeSlider.setPreferredSize(new Dimension(120, 20));

        volumeSlider.addChangeListener(e -> {
            if (player != null && !volumeSlider.getValueIsAdjusting()) {
                int vol = volumeSlider.getValue();
                player.audio().setVolume(vol);
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
            if (player == null) {
                return;
            }

            if (progressBar.getValueIsAdjusting()) {
                // user is currently dragging pause timer updates
                isSeeking = true;
            } else if (isSeeking) {
                // user just released the slider perform seek
                long length = player.status().length();
                if (length > 0) {
                    double fraction = progressBar.getValue() / (double) progressBar.getMaximum();
                    long newTime = (long) (fraction * length);
                    System.out.println("Seeking to time: " + newTime + " ms");
                    player.controls().setTime(newTime);
                }
                isSeeking = false;
            }
        });

        // Layout
        frame.setLayout(new BorderLayout());
        frame.add(topPanel, BorderLayout.NORTH);
        frame.add(centerPanel, BorderLayout.CENTER);
        frame.add(progressBar, BorderLayout.SOUTH);

        // Progress timer
        progressTimer = new Timer(500, e -> updateProgress());
        progressTimer.start();

        frame.setSize(460, 210);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private void onPlayPause() {
        if (!player.status().isPlaying()) {
            // Not playing → start playback
            statusLabel.setText("Status: playing");
            playPauseButton.setText("Pause");
            player.media().play(currentMediaPath);
        } else {
            // Already playing → pause
            player.controls().pause();
            statusLabel.setText("Status: paused");
            playPauseButton.setText("Play");
        }
    }

    private void onStop() {
        if (player != null) {
            System.out.println("Stopping playback.");
            player.controls().stop();
            statusLabel.setText("Status: stopped");
            playPauseButton.setText("Play");
            progressBar.setValue(0);
        }
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

            if (player.status().isPlaying()) {
                player.controls().stop();
            }

            statusLabel.setText("Status: loading " + selected.getName());

            boolean started = player.media().play(currentMediaPath);
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
        if (player == null || isSeeking) {
            
            return;
        }

        long length = player.status().length();
        long time = player.status().time();

        if (length <= 0 || time < 0) {
            progressBar.setValue(0);
            return;
        }

        double fraction = (double) time / (double) length;
        int sliderValue = (int) (fraction * progressBar.getMaximum());
        progressBar.setValue(sliderValue);

        timeLabel.setText(formatTime(time) + " / " + formatTime(length));
    }

    public static void main(String[] args) {
        System.setProperty("jna.library.path", "E:\\VLC");
        SwingUtilities.invokeLater(Main::new);
    }
}
