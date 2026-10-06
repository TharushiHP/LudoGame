package ludo.server;

import ludo.server.config.NamedThreadFactory;
import ludo.server.config.ServerConfig;
import ludo.server.config.ServerLog;
import ludo.server.coordinator.GameSession;
import ludo.server.coordinator.SessionRegistry;
import ludo.server.http.LudoHttpServer;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The coordinator server as one object (composition root): it wires the session registry, the
 * HTTP tier and the keep-alive timer together, and stops them in a safe order. ServerMain and the
 * tests both start the server through this class.
 */
public final class LudoServer {

    private static final long GAME_STOP_TIMEOUT_MS = 5_000;

    private final ServerLog log;
    private final SessionRegistry registry;
    private final LudoHttpServer http;
    private final ScheduledExecutorService keepAlive;
    private final AtomicBoolean stopped = new AtomicBoolean();

    private LudoServer(ServerConfig config) throws IOException {
        this.log = new ServerLog(config.out());
        this.registry = new SessionRegistry(config, log);
        this.http = new LudoHttpServer(config, registry, log);
        // Daemon: keep-alives are housekeeping only; nothing is lost if the JVM exits without one.
        this.keepAlive = Executors.newSingleThreadScheduledExecutor(NamedThreadFactory.single("sse-keepalive", true));
        keepAlive.scheduleAtFixedRate(() -> registry.all().forEach(GameSession::keepAlive),
                config.keepAliveSeconds(), config.keepAliveSeconds(), TimeUnit.SECONDS);
    }

    public static LudoServer start(ServerConfig config) throws IOException {
        LudoServer server = new LudoServer(config);
        server.http.start();
        server.log.log("LUDO-T coordinator listening on port " + server.port()
                + " (turn delay " + config.turnDelayMs() + " ms, move timeout " + config.moveTimeoutMs() + " ms)");
        return server;
    }

    public int port() {
        return http.port();
    }

    public SessionRegistry registry() {
        return registry;
    }

    /**
     * Stops in this order: refuse new requests (503); interrupt every game thread, so each game ends
     * as ABORTED, broadcasts GAME_OVER and closes its event streams; then close the HTTP server and
     * the keep-alive timer. Safe to call more than once.
     */
    public void stop() {
        if (!stopped.compareAndSet(false, true))
            return;
        log.log("shutting down: no new requests; stopping " + registry.all().size() + " game(s)");
        registry.shutdownAll(GAME_STOP_TIMEOUT_MS);
        try {
            http.stop();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        keepAlive.shutdownNow();
        log.log("server stopped");
    }
}
