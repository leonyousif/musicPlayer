package leon.music;

import javax.swing.*;
import java.awt.*;


public class WaveVisualizer extends JPanel {

    
    private final float[] ring;
    private int writePos = 0;

   
    private volatile float gain = 1.0f;

    public WaveVisualizer(int bufferSize) {
        this.ring = new float[Math.max(512, bufferSize)];
        setOpaque(true);
        setBackground(Theme.BG_COLOR);

        
        new Timer(16, e -> repaint()).start();
    }

    public WaveVisualizer() {
        this(2048);
    }

    
    public void setGain(float gain) {
        this.gain = Math.max(0f, Math.min(10f, gain));
    }

   
    public void pushSamples(float[] samples) {
        if (samples == null || samples.length == 0) return;

        
        for (float s : samples) {
            ring[writePos] = s;
            writePos++;
            if (writePos >= ring.length) writePos = 0;
        }
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(400, 70);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        int w = getWidth();
        int h = getHeight();
        if (w <= 2 || h <= 2) return;

        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            
            int midY = h / 2;

            
            g2.setColor(Theme.PROGRESS_FG);
            g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

           
            int points = Math.min(w, ring.length);
            int step = Math.max(1, ring.length / points);

            int[] xs = new int[points];
            int[] ys = new int[points];

            
            int start = writePos; 
            float ampScale = (h * 0.45f) * gain;

            int idx = start;
            for (int i = 0; i < points; i++) {
                float s = ring[idx];
                
                if (s > 1f) s = 1f;
                if (s < -1f) s = -1f;

                xs[i] = i;
                ys[i] = midY - Math.round(s * ampScale);

                idx += step;
                while (idx >= ring.length) idx -= ring.length;
            }

            g2.drawPolyline(xs, ys, points);

           
            g2.setColor(Theme.BORDER_COLOR);
            g2.setStroke(new BasicStroke(1f));
            g2.drawLine(0, midY, w, midY);

        } finally {
            g2.dispose();
        }
    }
}