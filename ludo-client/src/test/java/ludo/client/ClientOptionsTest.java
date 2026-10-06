package ludo.client;

import ludo.shared.PlayerColor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ClientOptionsTest {

    @Test
    void noArgumentsOpensTheConnectWindowOnLocalhost() {
        ClientOptions options = ClientOptions.parse();
        assertEquals("http://localhost:8080", options.server());
        assertFalse(options.skipsConnectWindow());
        assertFalse(options.headless());
    }

    @Test
    void allOptions() {
        ClientOptions options = ClientOptions.parse("--server=http://192.168.1.20:8080", "--game=3",
                "--colour=blue", "--name=Player D", "--headless");
        assertEquals("http://192.168.1.20:8080", options.server());
        assertEquals("3", options.gameId());
        assertEquals(PlayerColor.BLUE, options.identity().colour());
        assertEquals("Player D", options.identity().name());
        assertTrue(options.headless());
        assertTrue(options.skipsConnectWindow());
    }

    @Test
    void spectator() {
        ClientOptions options = ClientOptions.parse("--game=1", "--colour=SPECTATOR");
        assertTrue(options.identity().isSpectator());
        assertEquals("Spectator: Spectator", options.identity().headline());
    }

    @Test
    void defaultNameAndHeadline() {
        ClientOptions options = ClientOptions.parse("--game=1", "--colour=RED");
        assertEquals("You are Player Red (Red)", options.identity().headline());
    }

    @Test
    void badOptionsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> ClientOptions.parse("--colour=PURPLE"));
        assertThrows(IllegalArgumentException.class, () -> ClientOptions.parse("--speed=3"));
        assertThrows(IllegalArgumentException.class, () -> ClientOptions.parse("--headless", "--colour=RED"));
    }
}
