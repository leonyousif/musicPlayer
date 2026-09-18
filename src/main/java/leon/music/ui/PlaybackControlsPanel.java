package leon.music.ui;

import leon.music.Theme;
import leon.music.controller.PlayerController;
import leon.music.model.PlaybackState;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JToggleButton;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;

/**
 * Modular Swing panel hosting playback transport controls, toggles, volume slider,
 * and dialog triggers.
 */
public class PlaybackControlsPanel extends JPanel {

    private final PlayerController controller;

    private final JButton playPauseButton;
    private final JButton stopButton;
    private final JButton prevButton;
    private final JButton nextButton;
    private final JButton openButton;
    private final JButton openFolderButton;
    private final JToggleButton shuffleToggle;
    private final JToggleButton repeatToggle;
    private final JButton visualizerModeButton;
    private final JSlider volumeSlider;

    private boolean updatingVolumeInternally = false;

    public PlaybackControlsPanel(PlayerController controller) {
        super(new BorderLayout(0, 10));
        this.controller = controller;
        Theme.styleSurfacePanel(this);

        // Buttons
        prevButton = new JButton("Prev");
        Theme.styleSecondaryButton(prevButton);

        playPauseButton = new JButton("Play");
        Theme.styleButton(playPauseButton);

        nextButton = new JButton("Next");
        Theme.styleSecondaryButton(nextButton);

        stopButton = new JButton("Stop");
        Theme.styleSecondaryButton(stopButton);

        openButton = new JButton("Open File");
        Theme.styleSecondaryButton(openButton);

        openFolderButton = new JButton("Open Folder");
        Theme.styleSecondaryButton(openFolderButton);

        shuffleToggle = new JToggleButton("Shuffle");
        Theme.styleSecondaryButton(shuffleToggle);

        repeatToggle = new JToggleButton("Repeat");
        Theme.styleSecondaryButton(repeatToggle);

        visualizerModeButton = new JButton("Waveform");
        Theme.styleSecondaryButton(visualizerModeButton);

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

        // Volume
        JLabel volumeLabel = new JLabel("Volume");
        Theme.styleMutedLabel(volumeLabel);

        volumeSlider = new JSlider(0, 100, 100);
        Theme.styleProgressBar(volumeSlider);
        volumeSlider.setPreferredSize(new Dimension(160, 28));

        JPanel volumePanel = new JPanel(new BorderLayout(10, 0));
        Theme.stylePanel(volumePanel);
        volumePanel.add(volumeLabel, BorderLayout.WEST);
        volumePanel.add(volumeSlider, BorderLayout.CENTER);

        add(buttonPanel, BorderLayout.CENTER);
        add(volumePanel, BorderLayout.SOUTH);

        // Bind Action Listeners
        playPauseButton.addActionListener(e -> controller.playOrPause());
        stopButton.addActionListener(e -> controller.stop());
        prevButton.addActionListener(e -> controller.previous());
        nextButton.addActionListener(e -> controller.next());
        openButton.addActionListener(e -> controller.openFile());
        openFolderButton.addActionListener(e -> controller.openFolder());

        shuffleToggle.addActionListener(e -> controller.setShuffle(shuffleToggle.isSelected()));
        repeatToggle.addActionListener(e -> controller.toggleRepeat());
        visualizerModeButton.addActionListener(e -> controller.cycleVisualizerMode());

        volumeSlider.addChangeListener(e -> {
            if (!updatingVolumeInternally && !volumeSlider.getValueIsAdjusting()) {
                controller.setVolume(volumeSlider.getValue());
            }
        });
    }

    public void setPlaybackState(PlaybackState state) {
        if (state == PlaybackState.PLAYING) {
            playPauseButton.setText("Pause");
        } else {
            playPauseButton.setText("Play");
        }
    }

    public void setShuffle(boolean enabled) {
        shuffleToggle.setSelected(enabled);
    }

    public void setRepeat(boolean enabled, String label) {
        repeatToggle.setSelected(enabled);
        repeatToggle.setText(enabled ? "Repeat: " + label : "Repeat");
    }

    public void setVolume(int volume) {
        updatingVolumeInternally = true;
        try {
            volumeSlider.setValue(volume);
        } finally {
            updatingVolumeInternally = false;
        }
    }

    public void setVisualizerModeText(String modeName) {
        visualizerModeButton.setText(modeName);
    }

    public JButton getPlayPauseButton() {
        return playPauseButton;
    }

    public JToggleButton getShuffleToggle() {
        return shuffleToggle;
    }

    public JToggleButton getRepeatToggle() {
        return repeatToggle;
    }

    public JSlider getVolumeSlider() {
        return volumeSlider;
    }
}

