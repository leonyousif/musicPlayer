package leon.music;

import javax.swing.*;
import java.awt.*;

public class Theme {

    
    public static final Color BG_COLOR      = new Color(18, 18, 18);
    public static final Color FG_COLOR      = new Color(230, 230, 230);
    public static final Color ACCENT_COLOR  = new Color(76, 175, 80);
    public static final Color PROGRESS_BG   = new Color(30, 30, 30);
    public static final Color PROGRESS_FG   = new Color(129, 199, 132);

   // buttons
    public static void styleButton(JButton button) {
        button.setBackground(ACCENT_COLOR);
        button.setForeground(FG_COLOR);
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setOpaque(true);
    }

    // dark background 
    public static void stylePanel(JPanel panel) {
        panel.setBackground(BG_COLOR);
    }

    // labels
    public static void styleLabel(JLabel label) {
        label.setForeground(FG_COLOR);
    }

    // progress bar
    public static void styleProgressBar(JSlider slider) {
        slider.setBackground(PROGRESS_BG);
        slider.setForeground(PROGRESS_FG);
    }
}
