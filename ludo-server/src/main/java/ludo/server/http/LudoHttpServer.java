package ludo.server.http;

import com.sun.net.httpserver.HttpServer;
import ludo.server.config.NamedThreadFactory;
import ludo.server.config.ServerConfig;
import ludo.server.config.ServerLog;
import ludo.server.coordinator.SessionRegistry;
import ludo.server.coordinator.state.Reply;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class LudoHttpServer {

    private final HttpServer server;
    private final ExecutorService workers;

    public LudoHttpServer(ServerConfig config, SessionRegistry registry, ServerLog log) throws IOException {
        this.server = HttpServer.create(new InetSocketAddress(config.port()), 0);
        this.workers = Executors.newFixedThreadPool(config.httpThreads(),
                NamedThreadFactory.numbered("http-worker", false));
        server.setExecutor(workers);
        server.createContext("/games", new GamesHandler(registry, log));
        server.createContext("/health", exchange -> {
            if (exchange.getRequestMethod().equals("GET"))
                HttpReplies.send(exchange, 200, Map.of("status", registry.isAccepting() ? "UP" : "STOPPING",
                        "games", registry.all().size()));
            else
                HttpReplies.send(exchange, 405, Map.of("error", "use GET"));
        });
        server.createContext("/", exchange -> HttpReplies.send(exchange,
                Reply.notFound("no such path: " + exchange.getRequestURI().getPath())));
    }

    public void start() {
        server.start();
    }

    /** The real port (useful when the configured port was 0). */
    public int port() {
        return server.getAddress().getPort();
    }

    /**
     * Closes the listening socket and every connection (after up to 1 s), then
     * stops the workers.
     */
    public void stop() throws InterruptedException {
        server.stop(1);
        workers.shutdown();
        if (!workers.awaitTermination(5, TimeUnit.SECONDS))
            workers.shutdownNow();
    }
}
