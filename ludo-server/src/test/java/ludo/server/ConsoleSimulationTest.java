package ludo.server;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConsoleSimulationTest {

    @Test
    void parsesSeedArgument() {
        assertEquals(42L, ConsoleSimulation.parseSeed(new String[] {"--seed=42"}));
    }

    @Test
    void invalidSeedIsRejected() {
        assertThrows(NumberFormatException.class, () -> ConsoleSimulation.parseSeed(new String[] {"--seed=abc"}));
    }
}
