package leon.music;

import java.awt.BorderLayout;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import uk.co.caprica.vlcj.factory.MediaPlayerFactory;
import uk.co.caprica.vlcj.player.base.MediaPlayer;

public class Main {

    // VLCJ fields
    private MediaPlayerFactory factory;
    private MediaPlayer player;

    // Change this to the MP3 you tested earlier
    private final String mediaPath = "C:\\Users\\GGPC\\Desktop\\TheoryOfEverything2.mp3";

    // GUI fields
    private JFrame frame;
    private JButton playPauseButton;
    private JButton stopButton;
    private JLabel statusLabel;

    public Main() {
        initVlcj();
        createAndShowGui();
    }

    private void initVlcj() {
        try {
            // This will use vlcj's built-in native discovery
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
                    JOptionPane.ERROR_MESSAGE
            );
            t.printStackTrace();
            System.exit(1);
        }
    }

    private void createAndShowGui() {
        frame = new JFrame("Leon VLCJ Music Player");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        playPauseButton = new JButton("Play");
        stopButton = new JButton("Stop");
        statusLabel = new JLabel("Status: idle");

        playPauseButton.addActionListener(e -> onPlayPause());
        stopButton.addActionListener(e -> onStop());

        JPanel buttonPanel = new JPanel();
        buttonPanel.add(playPauseButton);
        buttonPanel.add(stopButton);

        frame.setLayout(new BorderLayout());
        frame.add(statusLabel, BorderLayout.NORTH);
        frame.add(buttonPanel, BorderLayout.CENTER);

        frame.setSize(320, 130);
        frame.setLocationRelativeTo(null); // center on screen
        frame.setVisible(true);
    }

    private void onPlayPause() {
        if (!player.status().isPlaying()) {
            // Not playing → start playback
            statusLabel.setText("Status: playing");
            playPauseButton.setText("Pause");
            player.media().play(mediaPath);
        } else {
            // Already playing → pause
            player.controls().pause();
            statusLabel.setText("Status: paused");
            playPauseButton.setText("Play");
        }
    }

    private void onStop() {
        if (player != null) {
            player.controls().stop();
            statusLabel.setText("Status: stopped");
            playPauseButton.setText("Play");
        }
    }

    public static void main(String[] args) {
        // Tell JNA/VLCJ where VLC is installed
        System.setProperty("jna.library.path", "E:\\VLC");

        // Start GUI on the Swing event thread
        SwingUtilities.invokeLater(Main::new);
    }
}
