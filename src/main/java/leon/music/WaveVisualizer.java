package leon.music;

import javax.swing.*;
import java.awt.*;


public class WaveVisualizer extends JPanel {

    
    private final float[] ring;
    private int writePos = 0;

   
    private volatile float gain = 1.0f;
    private volatile boolean active = false;
    private volatile float lastPeak = 0f;
    private volatile long lastSampleNanos = 0L;
    private float fallbackPhase = 0f;
    private final Timer repaintTimer;

    public WaveVisualizer(int bufferSize) {
        this.ring = new float[Math.max(512, bufferSize)];
        setOpaque(true);
        setBackground(Theme.BG_COLOR);

        repaintTimer = new Timer(16, e -> repaint());
    }

    public WaveVisualizer() {
        this(2048);
    }

    
    public void setGain(float gain) {
        this.gain = Math.max(0f, Math.min(10f, gain));
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public void dispose() {
        repaintTimer.stop();
        active = false;
    }

    @Override
    public void addNotify() {
        super.addNotify();
        if (!repaintTimer.isRunning()) {
            repaintTimer.start();
        }
    }

    @Override
    public void removeNotify() {
        repaintTimer.stop();
        super.removeNotify();
    }

   
    public void pushSamples(float[] samples) {
        if (samples == null || samples.length == 0) return;

        float peak = 0f;
        
        for (float s : samples) {
            peak = Math.max(peak, Math.abs(s));
            ring[writePos] = s;
            writePos++;
            if (writePos >= ring.length) writePos = 0;
        }

        lastPeak = peak;
        lastSampleNanos = System.nanoTime();
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(520, 180);
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

            GradientPaint backgroundFade = new GradientPaint(
                    0, 0, Theme.SURFACE_RAISED,
                    0, h, Theme.BG_COLOR);
            g2.setPaint(backgroundFade);
            g2.fillRoundRect(0, 0, w - 1, h - 1, 18, 18);

            g2.setColor(Theme.BORDER_COLOR);
            g2.drawRoundRect(0, 0, w - 1, h - 1, 18, 18);

            g2.setColor(Theme.PROGRESS_BG);
            for (int i = 1; i < 4; i++) {
                int y = (h * i) / 4;
                g2.drawLine(14, y, w - 14, y);
            }

            GradientPaint waveFade = new GradientPaint(
                    0, 0, Theme.ACCENT_ALT,
                    w, h, Theme.PROGRESS_FG);
            g2.setPaint(waveFade);
            g2.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

           
            int targetPoints = Math.max(48, Math.min(180, w / 8));
            int points = Math.min(targetPoints, ring.length);
            int step = Math.max(1, ring.length / points);

            int[] xs = new int[points];
            int[] ys = new int[points];

            
            int start = writePos; 
            float ampScale = (h * 0.45f) * gain;
            boolean hasRecentSamples = System.nanoTime() - lastSampleNanos < 350_000_000L;
            boolean useFallbackWave = active && (!hasRecentSamples || lastPeak < 0.004f);

            if (useFallbackWave) {
                fallbackPhase += 0.06f;
            }

            int idx = start;
            for (int i = 0; i < points; i++) {
                float s = useFallbackWave ? fallbackSample(i, points) : averageSample(idx, step);
                
                if (s > 1f) s = 1f;
                if (s < -1f) s = -1f;

                xs[i] = (points == 1) ? 0 : Math.round(i * (w - 1f) / (points - 1f));
                ys[i] = midY - Math.round(s * ampScale);

                idx += step;
                while (idx >= ring.length) idx -= ring.length;
            }

            g2.drawPolyline(xs, ys, points);

           
            g2.setColor(new Color(
                    Theme.PROGRESS_FG.getRed(),
                    Theme.PROGRESS_FG.getGreen(),
                    Theme.PROGRESS_FG.getBlue(),
                    95));
            g2.setStroke(new BasicStroke(1f));
            g2.drawLine(14, midY, w - 14, midY);

        } finally {
            g2.dispose();
        }
    }

    private float averageSample(int start, int count) {
        float total = 0f;
        int idx = start;
        int actualCount = Math.max(1, Math.min(count, ring.length));

        for (int i = 0; i < actualCount; i++) {
            total += ring[idx];
            idx++;
            if (idx >= ring.length) {
                idx = 0;
            }
        }

        return total / actualCount;
    }

    private float fallbackSample(int point, int pointCount) {
        if (!active || pointCount <= 1) {
            return 0f;
        }

        float progress = point / (float) (pointCount - 1);
        float envelope = (float) Math.sin(progress * Math.PI);
        float waveOne = (float) Math.sin(point * 0.075f + fallbackPhase);
        float waveTwo = (float) Math.sin(point * 0.024f - fallbackPhase * 1.7f);
        return (waveOne * 0.26f + waveTwo * 0.16f) * envelope;
    }
}
