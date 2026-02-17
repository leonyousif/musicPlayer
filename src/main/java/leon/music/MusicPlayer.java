package leon.music;

import uk.co.caprica.vlcj.factory.MediaPlayerFactory;
import uk.co.caprica.vlcj.player.base.MediaPlayer;

import javax.swing.JOptionPane;

public class MusicPlayer {

    private MediaPlayerFactory factory;
    private MediaPlayer player;

    public void init() {
        try {
            factory = new MediaPlayerFactory();
            player = factory.mediaPlayers().newMediaPlayer();
            player.audio().setVolume(100);
        } catch (Throwable t) {
            JOptionPane.showMessageDialog(
                    null,
                    "Could not load VLC native libraries.\nCheck that VLC is installed at E:\\VLC and is 64-bit.",
                    "VLC error",
                    JOptionPane.ERROR_MESSAGE
            );
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
        if (player == null) return false;
        if (mediaPath == null || mediaPath.isBlank()) return false;
        return player.media().play(mediaPath);
    }

    public void pause() {
        if (player == null) return;
        player.controls().pause();
    }

    public void stop() {
        if (player == null) return;
        player.controls().stop();
    }

    public void setVolume(int volume) {
        if (player == null) return;
        player.audio().setVolume(volume);
    }

    public long getLengthMs() {
        if (player == null) return 0L;
        return player.status().length();
    }

    public long getTimeMs() {
        if (player == null) return 0L;
        return player.status().time();
    }

    public void seekToMs(long newTimeMs) {
        if (player == null) return;
        player.controls().setTime(newTimeMs);
    }

    public void release() {
        try {
            if (player != null) {
                player.controls().stop();
                player.release();
                player = null;
            }
        } catch (Exception ignored) {}

        try {
            if (factory != null) {
                factory.release();
                factory = null;
            }
        } catch (Exception ignored) {}
    }
}
