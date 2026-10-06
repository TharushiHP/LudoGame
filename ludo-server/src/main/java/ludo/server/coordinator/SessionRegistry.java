package ludo.server.coordinator;

import ludo.server.config.ServerConfig;
import ludo.server.config.ServerLog;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * All games on this server, by id. A ConcurrentHashMap, because many HTTP threads look games up
 * at the same time while others create new ones. Ids come from an AtomicLong, so two
 * simultaneous POST /games never get the same id.
 */
public final class SessionRegistry {

    private final ConcurrentHashMap<String, GameSession> sessions = new ConcurrentHashMap<>();
    private final AtomicLong nextId = new AtomicLong();
    private final ServerConfig config;
    private final ServerLog log;
    private volatile boolean accepting = true;

    public SessionRegistry(ServerConfig config, ServerLog log) {
        this.config = config;
        this.log = log;
    }

    /** Creates and starts a game; null arguments take a random seed / the server's turn delay. */
    public GameSession create(Long seed, Long turnDelayMs) {
        String id = String.valueOf(nextId.incrementAndGet());
        long actualSeed = seed != null ? seed : new Random().nextLong();
        long delay = turnDelayMs != null ? turnDelayMs : config.turnDelayMs();
        GameSession session = new GameSession(id, actualSeed, delay, config, log);
        sessions.put(id, session);
        session.start();
        log.log("game " + id + " created (seed " + actualSeed + ", turn delay " + delay + " ms)");
        return session;
    }

    public Optional<GameSession> find(String id) {
        return Optional.ofNullable(sessions.get(id));
    }

    /** Every game, oldest first. */
    public List<GameSession> all() {
        return sessions.values().stream()
                .sorted(Comparator.comparingLong(s -> Long.parseLong(s.id())))
                .toList();
    }

    /** False once shutdown has begun: new requests then get 503. */
    public boolean isAccepting() {
        return accepting;
    }

    /** Stops accepting requests, then interrupts every game thread and waits for each to end. */
    public void shutdownAll(long timeoutMs) {
        accepting = false;
        for (GameSession session : all()) {
            try {
                session.shutdown(timeoutMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}
