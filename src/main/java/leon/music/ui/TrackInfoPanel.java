package leon.music.ui;

import leon.music.Theme;
import leon.music.model.Track;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;

/**
 * Modular Swing panel displaying current track metadata (title, artist, album)
 * and playback status label.
 */
public class TrackInfoPanel extends JPanel {

    private final JLabel trackInfoLabel;
    private final JLabel trackSubtitleLabel;
    private final JLabel statusLabel;

    public TrackInfoPanel() {
        super(new BorderLayout(14, 4));
        Theme.stylePanel(this);
        Theme.addBottomDivider(this);

        JLabel nowPlayingHeader = new JLabel("Now Playing");
        Theme.styleMutedLabel(nowPlayingHeader);

        trackInfoLabel = new JLabel("No file loaded");
        Theme.styleTitleLabel(trackInfoLabel);

        trackSubtitleLabel = new JLabel(" ");
        Theme.styleMutedLabel(trackSubtitleLabel);

        JPanel titlePanel = new JPanel(new BorderLayout(0, 2));
        titlePanel.setBackground(Theme.BG_COLOR);
        titlePanel.add(nowPlayingHeader, BorderLayout.NORTH);
        titlePanel.add(trackInfoLabel, BorderLayout.CENTER);
        titlePanel.add(trackSubtitleLabel, BorderLayout.SOUTH);

        statusLabel = new JLabel("Status: idle");
        Theme.styleMutedLabel(statusLabel);
        statusLabel.setHorizontalAlignment(SwingConstants.RIGHT);

        JPanel statusPanel = new JPanel(new BorderLayout(0, 4));
        statusPanel.setBackground(Theme.BG_COLOR);
        statusPanel.add(statusLabel, BorderLayout.CENTER);

        add(titlePanel, BorderLayout.CENTER);
        add(statusPanel, BorderLayout.EAST);
    }

    /**
     * Updates the panel with the given track's metadata.
     */
    public void setTrack(Track track) {
        if (track == null) {
            trackInfoLabel.setText("No file loaded");
            trackSubtitleLabel.setText(" ");
            trackInfoLabel.setToolTipText(null);
            return;
        }

        trackInfoLabel.setText(track.title());
        trackInfoLabel.setToolTipText(track.getFullHeader());

        StringBuilder sub = new StringBuilder();
        if (!track.artist().isBlank()) {
            sub.append(track.artist());
        }
        if (!track.album().isBlank()) {
            if (!sub.isEmpty()) sub.append(" • ");
            sub.append(track.album());
        }
        trackSubtitleLabel.setText(sub.isEmpty() ? " " : sub.toString());
    }

    /**
     * Updates the status label text.
     */
    public void setStatus(String status) {
        statusLabel.setText(status != null ? status : "Status: idle");
    }

    public JLabel getTrackInfoLabel() {
        return trackInfoLabel;
    }

    public JLabel getStatusLabel() {
        return statusLabel;
    }
}

