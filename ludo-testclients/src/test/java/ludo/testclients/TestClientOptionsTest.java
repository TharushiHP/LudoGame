package ludo.testclients;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class TestClientOptionsTest {

    @Test
    void defaultsWithoutArguments() {
        TestClientOptions o = TestClientOptions.parse(new String[0]);
        assertEquals("http://localhost:8080", o.server());
        assertEquals("all", o.scenario());
        assertEquals(2, o.clients());
        assertEquals(200, o.requests());
        assertEquals(Path.of("logs"), o.outDir());
        assertNull(o.watchJar());
    }

    @Test
    void parsesEveryOption() {
        TestClientOptions o = TestClientOptions.parse(new String[] {"--server=http://pc2:8090", "--scenario=BURST",
                "--games=5", "--clients=3", "--requests=40", "--wave-size=10", "--creates=7", "--turn-delay=150",
                "--seed=9", "--timeout=1000", "--out-dir=out", "--watch=client.jar"});
        assertEquals("http://pc2:8090", o.server());
        assertEquals("burst", o.scenario());
        assertEquals(5, o.games());
        assertEquals(3, o.clients());
        assertEquals(40, o.requests());
        assertEquals(10, o.waveSize());
        assertEquals(7, o.creates());
        assertEquals(150, o.turnDelayMs());
        assertEquals(9, o.seed());
        assertEquals(1000, o.timeoutMs());
        assertEquals(Path.of("out"), o.outDir());
        assertEquals("client.jar", o.watchJar());
    }

    @Test
    void rejectsBadOptions() {
        assertThrows(IllegalArgumentException.class, () -> TestClientOptions.parse(new String[] {"--scenario=storm"}));
        assertThrows(IllegalArgumentException.class, () -> TestClientOptions.parse(new String[] {"--games=0"}));
        assertThrows(IllegalArgumentException.class, () -> TestClientOptions.parse(new String[] {"--requests=-5"}));
        assertThrows(IllegalArgumentException.class, () -> TestClientOptions.parse(new String[] {"--clients=x"}));
        assertThrows(IllegalArgumentException.class, () -> TestClientOptions.parse(new String[] {"--speed=3"}));
        assertThrows(IllegalArgumentException.class, () -> TestClientOptions.parse(new String[] {"--server"}));
        assertThrows(IllegalArgumentException.class, () -> TestClientOptions.parse(new String[] {"--scenario=burst", "--clients=5"}),
                "a burst client needs a seat of its own");
        assertEquals(8, TestClientOptions.parse(new String[] {"--scenario=create", "--clients=8"}).clients());
    }
}
