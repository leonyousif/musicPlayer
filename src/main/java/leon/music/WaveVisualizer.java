package leon.music;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.atomic.AtomicReference;


public class WaveVisualizer extends JPanel {

    public enum VisualizerMode {
        WAVEFORM("Waveform"),
        BARS("Bars"),
        CIRCLE("Circle");

        private final String label;

        VisualizerMode(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    // --- Audio sample ring buffer & atomic snapshot buffer ---
    private final float[] ringBuffer;
    private int writePos = 0;
    private final AtomicReference<float[]> snapshotRef;

    private volatile float gain = 1.0f;
    private volatile boolean active = false;
    private volatile VisualizerMode visualizerMode = VisualizerMode.WAVEFORM;
    private volatile float lastPeak = 0f;
    private volatile long lastSampleNanos = 0L;
    private float fallbackPhase = 0f;
    private final Timer repaintTimer;

    public WaveVisualizer(int bufferSize) {
        int capacity = Math.max(512, bufferSize);
        this.ringBuffer = new float[capacity];
        this.snapshotRef = new AtomicReference<>(new float[capacity]);
        setOpaque(true);
        setBackground(Theme.BG_COLOR);

        repaintTimer = new Timer(16, e -> repaint());
    }

    public WaveVisualizer() {
        this(2048);
    }

    public float[] getSnapshot() {
        float[] snap = snapshotRef.get();
        return snap != null ? snap.clone() : new float[0];
    }

    public AtomicReference<float[]> getSnapshotRef() {
        return snapshotRef;
    }

    public int getBufferSize() {
        return ringBuffer.length;
    }

    
    public void setGain(float gain) {
        this.gain = Math.max(0f, Math.min(10f, gain));
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String nextMode() {
        if (visualizerMode == VisualizerMode.WAVEFORM) {
            visualizerMode = VisualizerMode.BARS;
        } else if (visualizerMode == VisualizerMode.BARS) {
            visualizerMode = VisualizerMode.CIRCLE;
        } else {
            visualizerMode = VisualizerMode.WAVEFORM;
        }

        repaint();
        return visualizerMode.getLabel();
    }

    public String getModeLabel() {
        return visualizerMode.getLabel();
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

   
    public synchronized void pushSamples(float[] samples) {
        if (samples == null || samples.length == 0) return;

        float peak = 0f;
        for (float s : samples) {
            float normalized = normalizeSample(s);
            peak = Math.max(peak, Math.abs(normalized));
            ringBuffer[writePos] = normalized;
            writePos++;
            if (writePos >= ringBuffer.length) {
                writePos = 0;
            }
        }

        snapshotRef.set(ringBuffer.clone());
        lastPeak = peak;
        lastSampleNanos = System.nanoTime();
    }

    private static float normalizeSample(float s) {
        if (Float.isNaN(s)) {
            return 0f;
        }
        if (s > 1.0f) {
            return 1.0f;
        }
        if (s < -1.0f) {
            return -1.0f;
        }
        return s;
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

            boolean hasRecentSamples = System.nanoTime() - lastSampleNanos < 350_000_000L;
            boolean useFallbackWave = active && (!hasRecentSamples || lastPeak < 0.004f);

            if (useFallbackWave) {
                fallbackPhase += 0.06f;
            }

            drawBackground(g2, w, h);

            float[] snapshot = snapshotRef.get();
            if (snapshot == null) {
                snapshot = new float[0];
            }

            if (visualizerMode == VisualizerMode.BARS) {
                drawBars(g2, w, h, useFallbackWave, snapshot);
            } else if (visualizerMode == VisualizerMode.CIRCLE) {
                drawCircleSpectrum(g2, w, h, useFallbackWave, snapshot);
            } else {
                drawWaveform(g2, w, h, useFallbackWave, snapshot);
            }

        } finally {
            g2.dispose();
        }
    }

    private void drawBackground(Graphics2D g2, int w, int h) {
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
    }

    private void drawWaveform(Graphics2D g2, int w, int h, boolean useFallbackWave, float[] snapshot) {
        int midY = h / 2;
        if (snapshot == null || snapshot.length == 0) {
            g2.setColor(withAlpha(Theme.PROGRESS_FG, 95));
            g2.setStroke(new BasicStroke(1f));
            g2.drawLine(14, midY, w - 14, midY);
            return;
        }

        int targetPoints = Math.max(48, Math.min(180, w / 8));
        int points = Math.min(targetPoints, snapshot.length);
        int step = Math.max(1, snapshot.length / points);

        int[] xs = new int[points];
        int[] ys = new int[points];

        int idx = 0;
        float ampScale = (h * 0.45f) * gain;
        for (int i = 0; i < points; i++) {
            float s = useFallbackWave ? fallbackSample(i, points) : averageSample(snapshot, idx, step);
            s = clamp(s, -1f, 1f);

            xs[i] = (points == 1) ? 0 : Math.round(i * (w - 1f) / (points - 1f));
            ys[i] = midY - Math.round(s * ampScale);

            idx += step;
            if (idx >= snapshot.length) {
                idx -= snapshot.length;
            }
        }

        g2.setPaint(new GradientPaint(0, 0, Theme.ACCENT_ALT, w, h, Theme.PROGRESS_FG));
        g2.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.drawPolyline(xs, ys, points);

        g2.setColor(withAlpha(Theme.PROGRESS_FG, 95));
        g2.setStroke(new BasicStroke(1f));
        g2.drawLine(14, midY, w - 14, midY);
    }

    private void drawBars(Graphics2D g2, int w, int h, boolean useFallbackWave, float[] snapshot) {
        int barCount = Math.max(18, Math.min(52, w / 16));
        float[] levels = buildLevels(barCount, useFallbackWave, snapshot);
        int left = 22;
        int right = w - 22;
        int top = 24;
        int bottom = h - 26;
        int availableHeight = Math.max(24, bottom - top);
        float slotWidth = (right - left) / (float) barCount;
        int barWidth = Math.max(4, Math.round(slotWidth * 0.62f));

        for (int i = 0; i < barCount; i++) {
            float level = levels[i];
            int barHeight = Math.max(4, Math.round(level * availableHeight));
            int x = Math.round(left + (i * slotWidth) + ((slotWidth - barWidth) / 2f));
            int y = bottom - barHeight;

            Color barColor = blend(Theme.ACCENT_ALT, Theme.PROGRESS_FG, i / (float) Math.max(1, barCount - 1));
            g2.setColor(withAlpha(barColor, 220));
            g2.fillRoundRect(x, y, barWidth, barHeight, 8, 8);
        }
    }

    private void drawCircleSpectrum(Graphics2D g2, int w, int h, boolean useFallbackWave, float[] snapshot) {
        int barCount = 72;
        float[] levels = buildLevels(barCount, useFallbackWave, snapshot);
        int centerX = w / 2;
        int centerY = h / 2;
        float baseRadius = Math.max(28f, Math.min(w, h) * 0.22f);
        float maxBarLength = Math.max(24f, Math.min(w, h) * 0.22f);

        g2.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 0; i < barCount; i++) {
            double angle = ((Math.PI * 2) * i / barCount) - (Math.PI / 2);
            float level = levels[i];
            float innerRadius = baseRadius;
            float outerRadius = baseRadius + 8f + (level * maxBarLength);

            int x1 = centerX + Math.round((float) Math.cos(angle) * innerRadius);
            int y1 = centerY + Math.round((float) Math.sin(angle) * innerRadius);
            int x2 = centerX + Math.round((float) Math.cos(angle) * outerRadius);
            int y2 = centerY + Math.round((float) Math.sin(angle) * outerRadius);

            Color barColor = blend(Theme.ACCENT_ALT, Theme.PROGRESS_FG, i / (float) Math.max(1, barCount - 1));
            g2.setColor(withAlpha(barColor, 225));
            g2.drawLine(x1, y1, x2, y2);
        }

        g2.setColor(withAlpha(Theme.PROGRESS_FG, 80));
        g2.setStroke(new BasicStroke(1f));
        g2.drawOval(
                centerX - Math.round(baseRadius),
                centerY - Math.round(baseRadius),
                Math.round(baseRadius * 2),
                Math.round(baseRadius * 2));
    }

    private float[] buildLevels(int levelCount, boolean useFallbackWave, float[] snapshot) {
        float[] levels = new float[levelCount];
        if (snapshot == null || snapshot.length == 0) {
            return levels;
        }
        int step = Math.max(1, snapshot.length / levelCount);
        int idx = 0;

        for (int i = 0; i < levelCount; i++) {
            float level = useFallbackWave
                    ? Math.abs(fallbackSample(i, levelCount))
                    : averageAbsSample(snapshot, idx, step);
            float weightedLevel = (float) Math.pow(clamp(level * gain * 2.5f, 0f, 1f), 0.62f);
            levels[i] = weightedLevel;

            idx += step;
            if (idx >= snapshot.length) {
                idx -= snapshot.length;
            }
        }

        return levels;
    }

    private float averageSample(float[] buffer, int start, int count) {
        if (buffer == null || buffer.length == 0) {
            return 0f;
        }
        float total = 0f;
        int idx = (start % buffer.length + buffer.length) % buffer.length;
        int actualCount = Math.max(1, Math.min(count, buffer.length));

        for (int i = 0; i < actualCount; i++) {
            total += buffer[idx];
            idx++;
            if (idx >= buffer.length) {
                idx = 0;
            }
        }

        return total / actualCount;
    }

    private float averageAbsSample(float[] buffer, int start, int count) {
        if (buffer == null || buffer.length == 0) {
            return 0f;
        }
        float total = 0f;
        int idx = (start % buffer.length + buffer.length) % buffer.length;
        int actualCount = Math.max(1, Math.min(count, buffer.length));

        for (int i = 0; i < actualCount; i++) {
            total += Math.abs(buffer[idx]);
            idx++;
            if (idx >= buffer.length) {
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

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private Color blend(Color start, Color end, float amount) {
        float clampedAmount = clamp(amount, 0f, 1f);
        int red = Math.round(start.getRed() + ((end.getRed() - start.getRed()) * clampedAmount));
        int green = Math.round(start.getGreen() + ((end.getGreen() - start.getGreen()) * clampedAmount));
        int blue = Math.round(start.getBlue() + ((end.getBlue() - start.getBlue()) * clampedAmount));
        return new Color(red, green, blue);
    }

    private Color withAlpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }
}
