package leon.music;

import com.sun.jna.Pointer;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import leon.music.service.AudioPlayer;
import uk.co.caprica.vlcj.player.base.callback.AudioCallbackAdapter;
import uk.co.caprica.vlcj.factory.MediaPlayerFactory;
import uk.co.caprica.vlcj.player.base.MediaPlayer;
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter;

import javax.swing.JOptionPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MusicPlayer implements AudioPlayer {

    private static final Logger log = LoggerFactory.getLogger(MusicPlayer.class);

    // fixing audio
    private SourceDataLine speakerLine;
    private AudioFormat speakerFormat;
    private final BlockingQueue<byte[]> audioQueue = new ArrayBlockingQueue<>(16);
    private volatile boolean playbackRunning = false;
    private Thread playbackThread;

    private MediaPlayerFactory factory;
    private MediaPlayer player;
    private Runnable onFinished;
    private final AtomicBoolean userRequestedStop = new AtomicBoolean(false);
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
                log.error("Failed to initialize Java Sound SourceDataLine", e);
            }

            ensurePlaybackThreadStarted();

            player.audio().setVolume(100);
            player.events().addMediaPlayerEventListener(new MediaPlayerEventAdapter() {
                @Override
                public void finished(MediaPlayer mediaPlayer) {
                    if (releasing) {
                        return;
                    }
                    if (userRequestedStop.compareAndSet(true, false)) {
                        return;
                    }
                    paused = false;
                    setVisualizerActive(false);
                    audioQueue.clear();
                    if (speakerLine != null) {
                        try {
                            speakerLine.flush();
                        } catch (Exception e) {
                            log.debug("Error flushing speakerLine on track finished", e);
                        }
                    }
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

                        if (speakerLine != null) {
                            applyVolume(pcm, outputVolume);
                            queueAudioChunk(pcm);
                        }

                    } catch (Throwable t) {
                        if (!audioCallbackWarned) {
                            audioCallbackWarned = true;
                            log.warn("Visualizer audio callback failed: {}", t.getMessage(), t);
                        }
                    }

                }
            };

            // enable callback
            player.audio().callback(PCM_FORMAT, PCM_RATE, PCM_CHANNELS, audioCallback, false);

        } catch (Throwable t) {
            log.error("Could not load VLC native libraries. Ensure 64-bit VLC media player is installed.", t);
            JOptionPane.showMessageDialog(
                    null,
                    "Could not load VLC native libraries.\nCheck that 64-bit VLC media player is installed.",
                    "VLC error",
                    JOptionPane.ERROR_MESSAGE);
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
        userRequestedStop.set(false);
        if (player == null)
            return false;
        if (mediaPath == null || mediaPath.isBlank())
            return false;

        releasing = false;
        paused = false;
        ensurePlaybackThreadStarted();
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
        if (player != null) {
            player.controls().stop();
        }
        paused = false;
        setVisualizerActive(false);
        audioQueue.clear();
        if (speakerLine != null) {
            try {
                speakerLine.flush();
            } catch (Exception e) {
                log.debug("Error flushing speakerLine during stop", e);
            }
        }
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
        audioQueue.clear();
        if (speakerLine != null) {
            try {
                speakerLine.flush();
            } catch (Exception e) {
                log.debug("Error flushing speakerLine during seek", e);
            }
        }
        if (player != null) {
            player.controls().setTime(newTimeMs);
        }
    }

    public void setOnFinished(Runnable onFinished) {
        this.onFinished = onFinished;
    }

    public void stopByUser() {
        userRequestedStop.set(true);
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

    void queueAudioChunk(byte[] chunk) {
        if (chunk == null || chunk.length == 0) {
            return;
        }
        if (!audioQueue.offer(chunk)) {
            audioQueue.poll();
            audioQueue.offer(chunk);
        }
    }

    private synchronized void ensurePlaybackThreadStarted() {
        if (playbackThread == null || !playbackThread.isAlive()) {
            playbackRunning = true;
            playbackThread = new Thread(this::playbackLoop, "audio-playback-thread");
            playbackThread.setDaemon(true);
            playbackThread.start();
        }
    }

    private void playbackLoop() {
        while (playbackRunning && !Thread.currentThread().isInterrupted()) {
            try {
                byte[] chunk = audioQueue.poll(100, TimeUnit.MILLISECONDS);
                if (chunk == null) {
                    continue;
                }
                SourceDataLine line = speakerLine;
                if (line != null && line.isOpen()) {
                    line.write(chunk, 0, chunk.length);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Throwable t) {
                if (!playbackRunning) {
                    break;
                }
                log.warn("Error writing audio to speakerLine in playback thread: {}", t.getMessage());
            }
        }
    }

    BlockingQueue<byte[]> getAudioQueue() {
        return audioQueue;
    }

    Thread getPlaybackThread() {
        return playbackThread;
    }

    boolean isPlaybackRunning() {
        return playbackRunning;
    }

    AtomicBoolean getUserRequestedStop() {
        return userRequestedStop;
    }

    void setSpeakerLine(SourceDataLine speakerLine) {
        this.speakerLine = speakerLine;
    }

    void startPlaybackThread() {
        ensurePlaybackThreadStarted();
    }

    byte[] applyVolume(byte[] pcm, float volume) {
        if (pcm == null || pcm.length == 0 || volume >= 0.995f) {
            return pcm;
        }

        if (volume <= 0.001f) {
            java.util.Arrays.fill(pcm, (byte) 0);
            return pcm;
        }

        for (int i = 0; i + 1 < pcm.length; i += 2) {
            short sample = (short) ((pcm[i + 1] << 8) | (pcm[i] & 0xff));
            int scaled = Math.round(sample * volume);
            if (scaled > Short.MAX_VALUE) {
                scaled = Short.MAX_VALUE;
            } else if (scaled < Short.MIN_VALUE) {
                scaled = Short.MIN_VALUE;
            }
            pcm[i] = (byte) (scaled & 0xff);
            pcm[i + 1] = (byte) ((scaled >> 8) & 0xff);
        }

        return pcm;
    }

    public void release() {

        releasing = true;
        onFinished = null;
        setVisualizerActive(false);

        playbackRunning = false;
        audioQueue.clear();
        if (playbackThread != null) {
            playbackThread.interrupt();
            try {
                playbackThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            playbackThread = null;
        }

        try {
            if (player != null) {
                player.controls().stop();
            }
        } catch (Exception e) {
            log.debug("Error stopping player during release", e);
        }

        try {
            if (speakerLine != null) {
                speakerLine.flush();
                speakerLine.stop();
                speakerLine.close();
                speakerLine = null;
            }
        } catch (Exception e) {
            log.debug("Error closing speakerLine during release", e);
        }

        try {
            if (player != null) {
                player.release();
                player = null;
            }
        } catch (Exception e) {
            log.debug("Error releasing player during release", e);
        }

        try {
            if (factory != null) {
                factory.release();
                factory = null;
            }
        } catch (Exception e) {
            log.debug("Error releasing factory during release", e);
        }
    }
}
