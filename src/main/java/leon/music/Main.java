package leon.music;

import uk.co.caprica.vlcj.factory.MediaPlayerFactory;
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery;
import uk.co.caprica.vlcj.player.base.MediaPlayer;

public class Main {

    public static void main(String[] args) throws InterruptedException {
        // 1. Try to find VLC on your system
        System.setProperty("jna.library.path", "E:\\VLC");
        boolean found = new NativeDiscovery().discover();
        System.out.println("VLC found: " + found);

        if (!found) {
            System.out.println("VLC was not found. Make sure it is installed (64-bit) and on your PATH.");
            return;
        }

        // 2. Path to your music file (change this!)
        // Example: "C:/Users/Leon/Music/song.mp3"
        String mediaPath = "C:\\Users\\GGPC\\Desktop\\TheoryOfEverything2.mp3";



        MediaPlayerFactory factory = new MediaPlayerFactory();
        MediaPlayer player = factory.mediaPlayers().newMediaPlayer();

        System.out.println("Playing: " + mediaPath);
        player.media().play(mediaPath);

        // 3. Keep the program alive while the music plays
        // (simple version: sleep for 5 minutes)
        Thread.sleep(5 * 60 * 1000);

        player.controls().stop();
        player.release();
        factory.release();

        System.out.println("Done.");
    }
}
