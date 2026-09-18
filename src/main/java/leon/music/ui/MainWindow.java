package leon.music.ui;

import leon.music.Theme;
import leon.music.WaveVisualizer;
import leon.music.controller.PlayerController;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * Main application window hosting the layout, split panes, and child Swing view components.
 */
public class MainWindow extends JFrame {

    private final PlayerController controller;
    private final TrackInfoPanel trackInfoPanel;
    private final PlaybackControlsPanel playbackControlsPanel;
    private final PlaylistPanel playlistPanel;
    private final ProgressBarPanel progressBarPanel;
    private final WaveVisualizer waveVisualizer;

    public MainWindow(PlayerController controller) {
        super("VLCJ Music Player");
        this.controller = controller;

        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        getContentPane().setBackground(Theme.BG_COLOR);
        setLayout(new BorderLayout());

        // Child View Panels
        trackInfoPanel = new TrackInfoPanel();
        playbackControlsPanel = new PlaybackControlsPanel(controller);
        playlistPanel = new PlaylistPanel(controller);
        progressBarPanel = new ProgressBarPanel(controller);

        waveVisualizer = new WaveVisualizer();
        waveVisualizer.setGain(1.6f);

        // Center Panel (Visualizer + Controls)
        JPanel centerPanel = new JPanel(new BorderLayout(0, 18));
        Theme.stylePanel(centerPanel);
        centerPanel.setMinimumSize(new Dimension(430, 300));
        centerPanel.add(waveVisualizer, BorderLayout.CENTER);
        centerPanel.add(playbackControlsPanel, BorderLayout.SOUTH);

        // Content Split Pane
        JSplitPane contentSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, playlistPanel, centerPanel);
        contentSplitPane.setBorder(null);
        contentSplitPane.setDividerSize(5);
        contentSplitPane.setResizeWeight(0.28);
        contentSplitPane.setContinuousLayout(true);
        contentSplitPane.setBackground(Theme.BG_COLOR);

        // Window Layout
        add(trackInfoPanel, BorderLayout.NORTH);
        add(contentSplitPane, BorderLayout.CENTER);
        add(progressBarPanel, BorderLayout.SOUTH);

        setMinimumSize(new Dimension(700, 480));
        setSize(920, 580);
        setLocationRelativeTo(null);

        // Shutdown hook
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                controller.shutdown();
            }
        });
    }

    public TrackInfoPanel getTrackInfoPanel() {
        return trackInfoPanel;
    }

    public PlaybackControlsPanel getPlaybackControlsPanel() {
        return playbackControlsPanel;
    }

    public PlaylistPanel getPlaylistPanel() {
        return playlistPanel;
    }

    public ProgressBarPanel getProgressBarPanel() {
        return progressBarPanel;
    }

    public WaveVisualizer getWaveVisualizer() {
        return waveVisualizer;
    }
}

