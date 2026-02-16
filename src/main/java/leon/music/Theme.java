package leon.music;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.border.EmptyBorder;
import javax.swing.AbstractButton;

public class Theme {

    // theme colours
    public static final Color BG_COLOR = new Color(18, 18, 18);
    public static final Color FG_COLOR = new Color(235, 235, 235);

    // primary buttons
    public static final Color ACCENT_COLOR = new Color(76, 175, 80);
    public static final Color ACCENT_HOVER = new Color(96, 200, 100);

    // text Colour
    public static final java.awt.Color TEXT_COLOR = java.awt.Color.WHITE;

    // Progress sliders
    public static final Color PROGRESS_BG = new Color(32, 32, 32);
    public static final Color PROGRESS_FG = new Color(129, 199, 132);

    // Borders / subtle lines
    public static final Color BORDER_COLOR = new Color(45, 45, 45);

    // Base fonts
    private static final Font BASE_FONT = new Font("Segoe UI", Font.PLAIN, 13);
    private static final Font BUTTON_FONT = BASE_FONT.deriveFont(Font.BOLD, 13f);
    private static final Font LABEL_FONT = BASE_FONT.deriveFont(Font.PLAIN, 13f);

  
    public static void styleButton(AbstractButton button) {
        button.setBackground(ACCENT_COLOR);
        button.setForeground(FG_COLOR);
        button.setFont(BUTTON_FONT);

        
        button.setBorder(new EmptyBorder(6, 16, 6, 16));
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setOpaque(true);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        
        button.addChangeListener(e -> {
            if (button.getModel().isRollover()) {
                button.setBackground(ACCENT_HOVER);
            } else {
                button.setBackground(ACCENT_COLOR);
            }
        });
    }

 
    public static void stylePanel(JPanel panel) {
        panel.setBackground(BG_COLOR);
        // Padding so components aren’t glued to the edges
        if (!(panel.getBorder() instanceof EmptyBorder)) {
            panel.setBorder(new EmptyBorder(8, 12, 8, 12));
        }
    }

   
    public static void styleLabel(JLabel label) {
        label.setForeground(FG_COLOR);
        label.setFont(LABEL_FONT);
    }

    
    public static void styleProgressBar(JSlider slider) {
        slider.setBackground(BG_COLOR);
        slider.setForeground(PROGRESS_FG);
        slider.setFont(BASE_FONT);

        
        slider.setBorder(new EmptyBorder(4, 12, 4, 12));

        
        slider.setPaintTicks(false);
        slider.setPaintTrack(true);
    }
}
