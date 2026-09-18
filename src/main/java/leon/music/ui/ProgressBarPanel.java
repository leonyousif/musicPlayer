package leon.music.ui;

import leon.music.Theme;
import leon.music.controller.PlayerController;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;

/**
 * Modular Swing panel hosting the seek slider and formatted elapsed / total time display.
 */
public class ProgressBarPanel extends JPanel {

    private final PlayerController controller;
    private final JSlider progressBar;
    private final JLabel timeLabel;

    public ProgressBarPanel(PlayerController controller) {
        super(new BorderLayout(12, 0));
        this.controller = controller;
        Theme.stylePanel(this);

        progressBar = new JSlider(0, 1000, 0);
        Theme.styleProgressBar(progressBar);

        timeLabel = new JLabel("00:00 / 00:00");
        Theme.styleMutedLabel(timeLabel);
        timeLabel.setHorizontalAlignment(SwingConstants.RIGHT);

        add(progressBar, BorderLayout.CENTER);
        add(timeLabel, BorderLayout.EAST);

        progressBar.addChangeListener(e -> {
            if (progressBar.getValueIsAdjusting()) {
                controller.setSeeking(true);
            } else if (controller.isSeeking()) {
                double fraction = progressBar.getValue() / (double) progressBar.getMaximum();
                controller.seekToFraction(fraction);
            }
        });
    }

    public void setProgress(long currentMs, long totalMs) {
        if (totalMs > 0) {
            int value = (int) ((currentMs * 1000) / totalMs);
            if (!progressBar.getValueIsAdjusting()) {
                progressBar.setValue(value);
            }
            timeLabel.setText(formatTime(currentMs) + " / " + formatTime(totalMs));
        }
    }

    public void reset() {
        progressBar.setValue(0);
        timeLabel.setText("00:00 / 00:00");
    }

    public JSlider getProgressBar() {
        return progressBar;
    }

    public JLabel getTimeLabel() {
        return timeLabel;
    }

    public static String formatTime(long ms) {
        if (ms <= 0) return "00:00";
        long totalSeconds = ms / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        if (hours > 0) {
            return String.format("%02d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format("%02d:%02d", minutes, seconds);
    }
}

