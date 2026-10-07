package ludo.testclients;

import ludo.server.LudoServer;
import ludo.server.config.ServerConfig;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.function.UnaryOperator;

/**
 * A real coordinator server, started in-process on a free port for one test: turn delay 0 and no
 * next game, so a whole game takes seconds; its log is kept in memory, not printed.
 */
final class TestServer implements AutoCloseable {

    private final ByteArrayOutputStream logBytes = new ByteArrayOutputStream();
    private final LudoServer server;

    TestServer(UnaryOperator<ServerConfig> change) throws Exception {
        PrintStream out = new PrintStream(logBytes, true, StandardCharsets.UTF_8);
        ServerConfig config = ServerConfig.defaults().withTurnDelayMs(0).withRematchDelayMs(0)
                .withMoveTimeoutMs(10_000).withPort(0).withEchoGameLog(false).withOut(out);
        server = LudoServer.start(change.apply(config));
    }

    String url() {
        return "http://localhost:" + server.port();
    }

    String log() {
        return logBytes.toString(StandardCharsets.UTF_8);
    }

    @Override
    public void close() {
        server.stop();
    }
}
