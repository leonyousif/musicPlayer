package leon.music;

import leon.music.controller.PlayerController;
import leon.music.service.MetadataService;
import leon.music.service.PlaylistService;
import leon.music.ui.MainWindow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.SwingUtilities;

/**
 * Application entry point and bootstrap class for VLCJ Music Player.
 */
public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        log.info("Starting VLCJ Music Player...");
        SwingUtilities.invokeLater(() -> {
            MusicPlayer audioPlayer = new MusicPlayer();
            PlaylistService playlistService = new PlaylistService();
            MetadataService metadataService = new MetadataService();

            PlayerController controller = new PlayerController(audioPlayer, playlistService, metadataService);
            MainWindow mainWindow = new MainWindow(controller);
            controller.attachView(mainWindow);
            controller.init();

            mainWindow.setVisible(true);
        });
    }
}
