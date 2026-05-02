package leon.music;

import com.sun.jna.Pointer;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;

import uk.co.caprica.vlcj.player.base.callback.AudioCallbackAdapter;
import uk.co.caprica.vlcj.factory.MediaPlayerFactory;
import uk.co.caprica.vlcj.player.base.MediaPlayer;
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter;
//import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter;

import javax.swing.JOptionPane;

public class MusicPlayer {

    // fixing audio
    private SourceDataLine speakerLine;
    private AudioFormat speakerFormat;

    private MediaPlayerFactory factory;
    private MediaPlayer player;
    private Runnable onFinished;
    private boolean ignoreNextFinished = false;
    private long ignoreFinishedUntilNanos = 0L;
    private boolean paused = false;
    private volatile boolean releasing = false;

    // for visualiser
    private volatile WaveVisualizer visualizer;
    private AudioCallbackAdapter audioCallback;
    private volatile float outputVolume = 1.0f;
    private volatile int currentVolume = 100;
    private boolean audioCallbackWarned = false;

    private static final String PCM_FORMAT = "S16N";
    private static final int PCM_RATE = 44100;
    private static final int PCM_CHANNELS = 2;

    public void init() {
        try {
            factory = new MediaPlayerFactory();
            player = factory.mediaPlayers().newMediaPlayer();
            speakerFormat = new AudioFormat( // java sound output
                    PCM_RATE, // sample rate
                    16, // bits
                    PCM_CHANNELS, // channels
                    true, // signed
                    false // little-endian
            );

            try {
                DataLine.Info info = new DataLine.Info(SourceDataLine.class, speakerFormat);
                speakerLine = (SourceDataLine) AudioSystem.getLine(info);
                speakerLine.open(speakerFormat, PCM_RATE); // buffer ~1 second
                speakerLine.start();
            } catch (Exception e) {
                speakerLine = null;
                e.printStackTrace();
            }

            player.audio().setVolume(100);
            player.events().addMediaPlayerEventListener(new MediaPlayerEventAdapter() {
                @Override
                public void finished(MediaPlayer mediaPlayer) {
                    if (releasing) {
                        return;
                    }
                    if (ignoreNextFinished && System.nanoTime() <= ignoreFinishedUntilNanos) {
                        ignoreNextFinished = false;
                        return;
                    }
                    ignoreNextFinished = false;
                    paused = false;
                    setVisualizerActive(false);
                    if (onFinished != null)
                        onFinished.run();
                }
            });

            audioCallback = new AudioCallbackAdapter() {
                @Override
                public void play(MediaPlayer mediaPlayer, Pointer samples, int sampleCount, long pts) {

                    try {
                        int bytes = sampleCount * PCM_CHANNELS * 2;
                        if (bytes <= 0)
                            return;

                        byte[] pcm = samples.getByteArray(0, bytes);

                        if (speakerLine != null) {
                            byte[] outputPcm = applyVolume(pcm, outputVolume);
                            speakerLine.write(outputPcm, 0, outputPcm.length);
                        }

                        int bytesPerFrame = PCM_CHANNELS * 2;
                        int totalFrames = pcm.length / bytesPerFrame;
                        if (totalFrames <= 0)
                            return;

                        int target = 256;
                        int step = Math.max(1, totalFrames / target);

                        float[] mono = new float[Math.max(1, (totalFrames + step - 1) / step)];
                        int out = 0;

                        for (int i = 0; i < totalFrames; i += step) {
                            int base = i * bytesPerFrame;

                            // guard prevents out of range
                            if (base + (bytesPerFrame - 1) >= pcm.length)
                                break;

                            short l = (short) ((pcm[base + 1] << 8) | (pcm[base] & 0xff));
                            short r = (short) ((pcm[base + 3] << 8) | (pcm[base + 2] & 0xff));

                            mono[out++] = ((l / 32768f) + (r / 32768f)) * 0.5f;
                        }
                        // Auto-gain keeps the visualiser visible at low volume.
                        float sumSq = 0f;
                        for (int i = 0; i < out; i++) {
                            float s = mono[i];
                            sumSq += s * s;
                        }

                        float rms = (out > 0) ? (float) Math.sqrt(sumSq / out) : 0f;

                        // Target RMS controls the wave height.
                        float targetRms = 0.15f;

                        // Gain factor to normalize to target loudness
                        float gain = (rms > 1e-6f) ? (targetRms / rms) : 1f;

                        // Clamp gain so silence/noise doesn't explode
                        gain = Math.max(0.5f, Math.min(6.0f, gain));

                        // Apply gain + clamp to [-1, 1]
                        for (int i = 0; i < out; i++) {
                            float s = mono[i] * gain;
                            if (s > 1f)
                                s = 1f;
                            if (s < -1f)
                                s = -1f;
                            mono[i] = s;
                        }
                        WaveVisualizer v = visualizer;
                        if (v != null) {
                            v.pushSamples(mono);
                        }

                    } catch (Throwable t) {
                        if (!audioCallbackWarned) {
                            audioCallbackWarned = true;
                            System.out.println("Visualizer audio callback failed: " + t.getMessage());
                            t.printStackTrace();
                        }
                    }

                }
            };

            // enable callback
            player.audio().callback(PCM_FORMAT, PCM_RATE, PCM_CHANNELS, audioCallback, false);

        } catch (

        Throwable t) {
            JOptionPane.showMessageDialog(
                    null,
                    "Could not load VLC native libraries.\nCheck that VLC is installed at E:\\VLC and is 64-bit.",
                    "VLC error",
                    JOptionPane.ERROR_MESSAGE);
            t.printStackTrace();
            throw new IllegalStateException("Failed to initialize VLCJ", t);
        }
    }

    public boolean isReady() {
        return player != null;
    }

    public boolean isPlaying() {
        return player != null && player.status().isPlaying();
    }

    public boolean play(String mediaPath) {
        if (player == null)
            return false;
        if (mediaPath == null || mediaPath.isBlank())
            return false;

        releasing = false;
        paused = false;
        boolean started = player.media().play(mediaPath);
        player.audio().setVolume(currentVolume);
        setVisualizerActive(started);
        return started;
    }

    public void pause() {
        if (player == null)
            return;
        player.controls().pause();
        paused = !paused;
        setVisualizerActive(!paused);
    }

    public void stop() {
        if (player == null)
            return;
        player.controls().stop();
        paused = false;
        setVisualizerActive(false);
    }

    public void setVolume(int volume) {
        currentVolume = Math.max(0, Math.min(100, volume));
        outputVolume = currentVolume / 100f;
        if (player == null)
            return;
        player.audio().setVolume(currentVolume);
    }

    public long getLengthMs() {
        if (player == null)
            return 0L;
        return player.status().length();
    }

    public long getTimeMs() {
        if (player == null)
            return 0L;
        return player.status().time();
    }

    public void seekToMs(long newTimeMs) {
        if (player == null)
            return;
        player.controls().setTime(newTimeMs);
    }

    public void setOnFinished(Runnable onFinished) {
        this.onFinished = onFinished;
    }

    public void stopByUser() {
        if (isPlaying() || paused) {
            ignoreNextFinished = true;
            ignoreFinishedUntilNanos = System.nanoTime() + 1_500_000_000L;
        }
        stop();
    }

    public boolean isPaused() {
        return paused;
    }

    public void setVisualizer(WaveVisualizer panel) {
        this.visualizer = panel;
    }

    private void setVisualizerActive(boolean active) {
        WaveVisualizer v = visualizer;
        if (v != null) {
            v.setActive(active);
        }
    }

    private byte[] applyVolume(byte[] pcm, float volume) {
        if (volume >= 0.995f) {
            return pcm;
        }

        byte[] adjusted = pcm.clone();
        for (int i = 0; i + 1 < adjusted.length; i += 2) {
            short sample = (short) ((adjusted[i + 1] << 8) | (adjusted[i] & 0xff));
            int scaled = Math.round(sample * volume);
            if (scaled > Short.MAX_VALUE)
                scaled = Short.MAX_VALUE;
            if (scaled < Short.MIN_VALUE)
                scaled = Short.MIN_VALUE;
            adjusted[i] = (byte) (scaled & 0xff);
            adjusted[i + 1] = (byte) ((scaled >> 8) & 0xff);
        }

        return adjusted;
    }

    public void release() {

        releasing = true;
        onFinished = null;
        setVisualizerActive(false);

        try {
            if (player != null) {
                player.controls().stop();
            }
        } catch (Exception ignored) {
        }

        try {
            if (speakerLine != null) {
                speakerLine.flush();
                speakerLine.stop();
                speakerLine.close();
                speakerLine = null;
            }
        } catch (Exception ignored) {
        }

        try {
            if (player != null) {
                player.release();
                player = null;
            }
        } catch (Exception ignored) {
        }

        try {
            if (factory != null) {
                factory.release();
                factory = null;
            }
        } catch (Exception ignored) {
        }
    }
}
