package ludo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MainTest {

    @Test
    void parsesSeedArgument() {
        assertEquals(42L, Main.parseSeed(new String[] {"--seed=42"}));
    }

    @Test
    void invalidSeedIsRejected() {
        assertThrows(NumberFormatException.class, () -> Main.parseSeed(new String[] {"--seed=abc"}));
    }
}
