package leon.music;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;

import javax.swing.BorderFactory;
import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.border.EmptyBorder;
import javax.swing.border.CompoundBorder;

public class Theme {

    // theme colours
    public static final Color BG_COLOR = new Color(13, 15, 18);
    public static final Color SURFACE_COLOR = new Color(24, 27, 32);
    public static final Color SURFACE_RAISED = new Color(33, 37, 43);
    public static final Color FG_COLOR = new Color(238, 242, 247);

    // primary buttons
    public static final Color ACCENT_COLOR = new Color(38, 166, 154);
    public static final Color ACCENT_HOVER = new Color(53, 190, 176);
    public static final Color ACCENT_ALT = new Color(255, 183, 77);

    // text Colour
    public static final java.awt.Color TEXT_COLOR = new Color(238, 242, 247);
    public static final java.awt.Color MUTED_TEXT = new Color(150, 161, 176);

    // Progress sliders
    public static final Color PROGRESS_BG = new Color(43, 48, 56);
    public static final Color PROGRESS_FG = new Color(68, 202, 190);

    // Borders / subtle lines
    public static final Color BORDER_COLOR = new Color(58, 65, 75);

    // Base fonts
    private static final Font BASE_FONT = new Font("Segoe UI", Font.PLAIN, 13);
    private static final Font BUTTON_FONT = BASE_FONT.deriveFont(Font.BOLD, 12f);
    private static final Font LABEL_FONT = BASE_FONT.deriveFont(Font.PLAIN, 13f);
    private static final Font TITLE_FONT = BASE_FONT.deriveFont(Font.BOLD, 18f);
    private static final Font SMALL_FONT = BASE_FONT.deriveFont(Font.PLAIN, 12f);

  
    public static void styleButton(AbstractButton button) {
        button.setBackground(ACCENT_COLOR);
        button.setForeground(FG_COLOR);
        button.setFont(BUTTON_FONT);

        
        button.setBorder(new CompoundBorder(
                BorderFactory.createLineBorder(new Color(48, 191, 178), 1),
                new EmptyBorder(8, 16, 8, 16)));
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setOpaque(true);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        
        button.addChangeListener(e -> {
            if (button.isSelected()) {
                button.setBackground(ACCENT_ALT);
                button.setForeground(BG_COLOR);
            } else if (button.getModel().isRollover()) {
                button.setBackground(ACCENT_HOVER);
                button.setForeground(FG_COLOR);
            } else {
                button.setBackground(ACCENT_COLOR);
                button.setForeground(FG_COLOR);
            }
        });
    }

    public static void styleSecondaryButton(AbstractButton button) {
        button.setBackground(SURFACE_RAISED);
        button.setForeground(FG_COLOR);
        button.setFont(BUTTON_FONT);
        button.setBorder(new CompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR, 1),
                new EmptyBorder(8, 14, 8, 14)));
        button.setFocusPainted(false);
        button.setBorderPainted(true);
        button.setOpaque(true);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        button.addChangeListener(e -> {
            if (button.isSelected()) {
                button.setBackground(ACCENT_ALT);
                button.setForeground(BG_COLOR);
            } else if (button.getModel().isRollover()) {
                button.setBackground(new Color(43, 49, 58));
                button.setForeground(FG_COLOR);
            } else {
                button.setBackground(SURFACE_RAISED);
                button.setForeground(FG_COLOR);
            }
        });
    }

 
    public static void stylePanel(JPanel panel) {
        panel.setBackground(BG_COLOR);
        // Padding so components are not glued to the edges
        if (!(panel.getBorder() instanceof EmptyBorder)) {
            panel.setBorder(new EmptyBorder(8, 12, 8, 12));
        }
    }

   
    public static void styleSurfacePanel(JPanel panel) {
        panel.setBackground(SURFACE_COLOR);
        panel.setBorder(new CompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR, 1),
                new EmptyBorder(12, 14, 12, 14)));
    }

    public static void styleLabel(JLabel label) {
        label.setForeground(FG_COLOR);
        label.setFont(LABEL_FONT);
    }

    public static void styleTitleLabel(JLabel label) {
        label.setForeground(FG_COLOR);
        label.setFont(TITLE_FONT);
    }

    public static void styleMutedLabel(JLabel label) {
        label.setForeground(MUTED_TEXT);
        label.setFont(SMALL_FONT);
    }

    
    public static void styleProgressBar(JSlider slider) {
        slider.setBackground(BG_COLOR);
        slider.setForeground(PROGRESS_FG);
        slider.setFont(BASE_FONT);

        
        slider.setBorder(new EmptyBorder(4, 12, 4, 12));

        
        slider.setPaintTicks(false);
        slider.setPaintTrack(true);
    }

    public static void styleScrollPane(JScrollPane scrollPane) {
        scrollPane.setBorder(BorderFactory.createLineBorder(BORDER_COLOR, 1));
        scrollPane.getViewport().setBackground(SURFACE_COLOR);
        scrollPane.setBackground(SURFACE_COLOR);
    }

    public static void addBottomDivider(JComponent component) {
        component.setBorder(new CompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_COLOR),
                new EmptyBorder(14, 18, 14, 18)));
    }
}
