package leon.music.ui;

import leon.music.Theme;
import leon.music.controller.PlayerController;
import leon.music.model.Track;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Modular Swing panel hosting the track playlist table displaying
 * title, artist, and formatted duration using the Track model.
 */
public class PlaylistPanel extends JPanel {

    private final PlayerController controller;
    private final JTable trackTable;
    private final TrackTableModel tableModel;
    private final JLabel playlistCountLabel;
    private int currentTrackIndex = -1;

    public PlaylistPanel(PlayerController controller) {
        super(new BorderLayout(0, 8));
        this.controller = controller;
        Theme.styleSurfacePanel(this);

        setMinimumSize(new Dimension(210, 260));
        setPreferredSize(new Dimension(280, 0));

        // Header
        JLabel playlistTitleLabel = new JLabel("Library");
        Theme.styleTitleLabel(playlistTitleLabel);

        playlistCountLabel = new JLabel("0 tracks");
        Theme.styleMutedLabel(playlistCountLabel);

        JPanel playlistHeaderPanel = new JPanel(new BorderLayout(8, 0));
        playlistHeaderPanel.setBackground(Theme.SURFACE_COLOR);
        playlistHeaderPanel.setBorder(BorderFactory.createEmptyBorder(4, 4, 10, 4));
        playlistHeaderPanel.add(playlistTitleLabel, BorderLayout.WEST);
        playlistHeaderPanel.add(playlistCountLabel, BorderLayout.EAST);
        add(playlistHeaderPanel, BorderLayout.NORTH);

        // Table Model & JTable
        tableModel = new TrackTableModel();
        trackTable = new JTable(tableModel);
        trackTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        trackTable.setRowHeight(38);
        trackTable.setShowGrid(false);
        trackTable.setIntercellSpacing(new Dimension(0, 0));
        trackTable.setBackground(Theme.SURFACE_COLOR);
        trackTable.setForeground(Theme.TEXT_COLOR);
        trackTable.setSelectionBackground(Theme.ACCENT_COLOR);
        trackTable.setSelectionForeground(Theme.FG_COLOR);

        // Table Header Styling
        JTableHeader header = trackTable.getTableHeader();
        header.setBackground(Theme.SURFACE_RAISED);
        header.setForeground(Theme.MUTED_TEXT);
        header.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER_COLOR));

        // Column widths
        trackTable.getColumnModel().getColumn(0).setPreferredWidth(140);
        trackTable.getColumnModel().getColumn(1).setPreferredWidth(90);
        trackTable.getColumnModel().getColumn(2).setPreferredWidth(55);
        trackTable.getColumnModel().getColumn(2).setMaxWidth(80);

        // Custom Cell Renderer
        trackTable.setDefaultRenderer(Object.class, new TrackTableCellRenderer());

        // Mouse Listener for double-click play
        trackTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int row = trackTable.getSelectedRow();
                    if (row >= 0 && row < tableModel.getRowCount()) {
                        controller.playTrackAtIndex(row);
                    }
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(trackTable);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        Theme.styleScrollPane(scrollPane);
        add(scrollPane, BorderLayout.CENTER);
    }

    public void setTracks(List<Track> tracks) {
        tableModel.setTracks(tracks);
        playlistCountLabel.setText((tracks != null ? tracks.size() : 0) + " tracks");
    }

    public void setCurrentIndex(int index) {
        this.currentTrackIndex = index;
        if (index >= 0 && index < tableModel.getRowCount()) {
            trackTable.setRowSelectionInterval(index, index);
            trackTable.scrollRectToVisible(trackTable.getCellRect(index, 0, true));
        }
        trackTable.repaint();
    }

    public void updateTrack(int index, Track enrichedTrack) {
        tableModel.updateTrack(index, enrichedTrack);
    }

    public JTable getTrackTable() {
        return trackTable;
    }

    public int getCurrentTrackIndex() {
        return currentTrackIndex;
    }

    // Inner Table Model
    private static class TrackTableModel extends AbstractTableModel {
        private final String[] columns = {"Title", "Artist", "Duration"};
        private final List<Track> tracks = new ArrayList<>();

        public void setTracks(List<Track> newTracks) {
            tracks.clear();
            if (newTracks != null) {
                tracks.addAll(newTracks);
            }
            fireTableDataChanged();
        }

        public void updateTrack(int index, Track track) {
            if (index >= 0 && index < tracks.size() && track != null) {
                tracks.set(index, track);
                fireTableRowsUpdated(index, index);
            }
        }

        @Override
        public int getRowCount() {
            return tracks.size();
        }

        @Override
        public int getColumnCount() {
            return columns.length;
        }

        @Override
        public String getColumnName(int column) {
            return columns[column];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            if (rowIndex < 0 || rowIndex >= tracks.size()) return "";
            Track t = tracks.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> t.title();
                case 1 -> t.artist().isBlank() ? "—" : t.artist();
                case 2 -> t.getFormattedDuration();
                default -> "";
            };
        }
    }

    // Inner Cell Renderer
    private class TrackTableCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus,
                                                       int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            boolean isCurrent = (row == currentTrackIndex);
            if (column == 0 && value != null) {
                setText((isCurrent ? "▶  " : "   ") + value);
            }

            if (column == 2) {
                setHorizontalAlignment(SwingConstants.RIGHT);
            } else {
                setHorizontalAlignment(SwingConstants.LEFT);
            }

            setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

            if (isSelected) {
                c.setBackground(Theme.ACCENT_COLOR);
                c.setForeground(Theme.FG_COLOR);
            } else if (isCurrent) {
                c.setBackground(Theme.PROGRESS_BG);
                c.setForeground(Theme.ACCENT_ALT);
            } else {
                c.setBackground(Theme.SURFACE_COLOR);
                c.setForeground(Theme.TEXT_COLOR);
            }

            return c;
        }
    }
}

