package leon.music.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RepeatMode Unit Tests")
class RepeatModeTest {

    @Test
    void shouldCycleModesCorrectly() {
        RepeatMode mode = RepeatMode.OFF;

        mode = mode.next();
        assertThat(mode).isEqualTo(RepeatMode.ALL);

        mode = mode.next();
        assertThat(mode).isEqualTo(RepeatMode.ONE);

        mode = mode.next();
        assertThat(mode).isEqualTo(RepeatMode.OFF);
    }

    @Test
    void shouldProvideDisplayNamesAndShortLabels() {
        assertThat(RepeatMode.OFF.getDisplayName()).isEqualTo("Off");
        assertThat(RepeatMode.OFF.getShortLabel()).isEqualTo("Off");

        assertThat(RepeatMode.ALL.getDisplayName()).isEqualTo("Repeat All");
        assertThat(RepeatMode.ALL.getShortLabel()).isEqualTo("All");

        assertThat(RepeatMode.ONE.getDisplayName()).isEqualTo("Repeat One");
        assertThat(RepeatMode.ONE.getShortLabel()).isEqualTo("1");
    }
}
