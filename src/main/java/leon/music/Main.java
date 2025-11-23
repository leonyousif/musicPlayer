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

        playPauseButton.addActionListener(e -> onPlayPause());
        openButton.addActionListener(e -> onOpenFile());
        stopButton.addActionListener(e -> onStop());

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
        frame.add(statusLabel, BorderLayout.NORTH);
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
            currentMediaPath = selected.getAbsolutePath();
            statusLabel.setText("Status: selected " + selected.getName());

            if (player.status().isPlaying()) {
                player.controls().stop();
            }

            // auto play new file
            player.media().play(currentMediaPath);
            playPauseButton.setText("Pause");
            statusLabel.setText("Status: playing " + selected.getName());
        }
    }

    private void updateProgress() {
        if (player == null || isSeeking) {
            // don't overwrite the slider while the user is dragging it
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
    }

    public static void main(String[] args) {
        System.setProperty("jna.library.path", "E:\\VLC");
        SwingUtilities.invokeLater(Main::new);
    }
}
