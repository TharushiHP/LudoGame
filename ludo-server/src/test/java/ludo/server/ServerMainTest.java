package ludo.server;

import ludo.game.EndCondition;
import ludo.server.config.ServerConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Command-line options of the server. */
class ServerMainTest {

    @Test
    void defaultsWithoutArguments() {
        ServerConfig config = ServerMain.parse(new String[0]);
        assertEquals(8080, config.port());
        assertEquals(500, config.turnDelayMs());
        assertEquals(10_000, config.moveTimeoutMs());
        assertEquals(EndCondition.FIRST_WINNER, config.endCondition());
    }

    @Test
    void parsesTheEndCondition() {
        assertEquals(EndCondition.ALL_PLACES, ServerMain.parse(new String[] {"--end-condition=ALL_PLACES"}).endCondition());
        assertEquals(EndCondition.FIRST_WINNER, ServerMain.parse(new String[] {"--end-condition=first_winner"}).endCondition());
        assertThrows(IllegalArgumentException.class, () -> ServerMain.parse(new String[] {"--end-condition=NEVER"}));
    }

    @Test
    void parsesEveryOption() {
        ServerConfig config = ServerMain.parse(new String[] {"--port=9000", "--turn-delay=0", "--move-timeout=2500"});
        assertEquals(9000, config.port());
        assertEquals(0, config.turnDelayMs());
        assertEquals(2500, config.moveTimeoutMs());
    }

    @Test
    void rejectsBadOptions() {
        assertThrows(IllegalArgumentException.class, () -> ServerMain.parse(new String[] {"--port=abc"}));
        assertThrows(IllegalArgumentException.class, () -> ServerMain.parse(new String[] {"--port=70000"}));
        assertThrows(IllegalArgumentException.class, () -> ServerMain.parse(new String[] {"--turn-delay=-1"}));
        assertThrows(IllegalArgumentException.class, () -> ServerMain.parse(new String[] {"--speed=3"}));
        assertThrows(IllegalArgumentException.class, () -> ServerMain.parse(new String[] {"--port"}));
    }
}
