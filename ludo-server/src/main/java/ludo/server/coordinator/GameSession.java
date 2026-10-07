package ludo.server.coordinator;

import ludo.game.EndCondition;
import ludo.server.config.ServerConfig;
import ludo.server.config.ServerLog;
import ludo.server.coordinator.state.Reply;
import ludo.shared.PlayerColor;
import ludo.shared.protocol.ClientRequest;
import ludo.shared.protocol.GameOverEvent;
import ludo.shared.protocol.ServerEvent;
import ludo.shared.protocol.StateEvent;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * One game on the server, as seen by other threads (Active Object). It owns the game's command
 * queue and its game thread, {@code game-<id>}, which is the only thread that ever touches the
 * Game (see {@link Coordinator}). With rematch on, the same session plays one game after another
 * (same seats and event streams); {@link #gameNumber()} and {@link #seed()} describe the current one.
 * <ul>
 *   <li>Producer-consumer: HTTP threads call {@link #request}, which wraps the request with a
 *       CompletableFuture, puts it on a bounded ArrayBlockingQueue and waits for the reply. When
 *       the queue is full the request is refused at once with 503 (back-pressure).</li>
 *   <li>Results are published through volatile fields holding immutable values (the latest STATE,
 *       the coordinator state's name), so reads such as GET /state never use the queue or a lock.</li>
 *   <li>The version is an AtomicLong: only the game thread increments it, any thread may read it.</li>
 *   <li>Queue evidence for GET /games/{id}: the queue's high-water mark (an AtomicInteger raised by
 *       the HTTP thread that has just queued a command) and how many requests were answered 2xx,
 *       409, 503 (queue full) or otherwise (AtomicLongs, counted by the HTTP thread that got the reply).</li>
 * </ul>
 */
public final class GameSession {

    private final String id;
    private volatile long seed;
    private volatile int gameNumber = 1;
    private final long turnDelayMs;
    private final EndCondition endCondition;
    private final ServerConfig config;
    private final ServerLog log;
    private final BlockingQueue<Command> queue;
    private final Broadcaster broadcaster;
    private final Thread gameThread;
    private final AtomicLong version = new AtomicLong();
    private final AtomicInteger peakQueueDepth = new AtomicInteger();
    private final AtomicLong accepted = new AtomicLong();
    private final AtomicLong rejected = new AtomicLong();
    private final AtomicLong refused = new AtomicLong();
    private final AtomicLong otherErrors = new AtomicLong();
    private volatile ServerEvent openRequest;

    private volatile StateView latest;
    private volatile String stateName = "Created";
    private volatile int joined;
    private volatile Set<PlayerColor> taken = Set.of();
    private volatile boolean closed;
    private volatile GameOverEvent result;

    /** A game that ends as the server's config says ({@link ServerConfig#endCondition()}). */
    public GameSession(String id, long seed, long turnDelayMs, ServerConfig config, ServerLog log) {
        this(id, seed, turnDelayMs, config.endCondition(), config, log);
    }

    public GameSession(String id, long seed, long turnDelayMs, EndCondition endCondition, ServerConfig config, ServerLog log) {
        this.id = id;
        this.seed = seed;
        this.turnDelayMs = turnDelayMs;
        this.endCondition = endCondition;
        this.config = config;
        this.log = log;
        this.queue = new ArrayBlockingQueue<>(config.queueCapacity());
        this.broadcaster = new Broadcaster(id, log, this::streamLost);
        Coordinator coordinator = new Coordinator(this, queue, broadcaster);
        // Non-daemon: the JVM must not exit in the middle of a game. Stopped by interrupt().
        this.gameThread = new Thread(coordinator::run, "game-" + id);
        this.gameThread.setDaemon(false);
    }

    public void start() {
        gameThread.start();
    }

    /** Called by HTTP threads: queue the request and wait for the game thread's reply. */
    public Reply request(ClientRequest request) {
        Reply reply = awaitReply(submit(request));
        count(reply);
        return reply;
    }

    private Reply awaitReply(CompletableFuture<Reply> reply) {
        try {
            return reply.get(config.replyTimeoutMs(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            return Reply.timeout("game " + id + " did not reply within " + config.replyTimeoutMs() + " ms");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Reply.unavailable("server is shutting down");
        } catch (ExecutionException e) {
            return Reply.unavailable("request failed: " + e.getCause());
        }
    }

    private void count(Reply reply) {
        if (reply.isSuccess())
            accepted.incrementAndGet();
        else if (reply.status() == 409)
            rejected.incrementAndGet();
        else if (reply.status() == 503)
            refused.incrementAndGet();
        else
            otherErrors.incrementAndGet();
    }

    /** Puts a request on the queue; the future is already completed with 503/409 if it cannot go on. */
    CompletableFuture<Reply> submit(ClientRequest request) {
        if (closed)
            return CompletableFuture.completedFuture(Reply.conflict("game " + id + " is over"));
        Command.Request command = new Command.Request(request, new CompletableFuture<>());
        if (!queue.offer(command)) {
            log.log("queue full (" + config.queueCapacity() + "): " + request.type() + " refused with 503");
            return CompletableFuture.completedFuture(Reply.unavailable(
                    "game " + id + " is busy: command queue full (" + config.queueCapacity() + "), retry later"));
        }
        peakQueueDepth.accumulateAndGet(queue.size(), Math::max);
        // The game thread sets closed and then drains the queue. If it closed just after our offer,
        // either it drained our command (and completed it) or we take it back here.
        if (closed && queue.remove(command))
            return CompletableFuture.completedFuture(Reply.conflict("game " + id + " is over"));
        return command.reply();
    }

    /**
     * Registers a client's event stream. If the game is already over, the final STATE and
     * GAME_OVER are written straight away and the stream is closed.
     */
    public void connect(EventSink sink) {
        if (broadcaster.add(sink)) {
            if (!queue.offer(new Command.Connected(sink)))
                log.log("queue full: " + sink.name() + " connected without an immediate resync");
            return;
        }
        try {
            StateView last = latest;
            if (last != null)
                sink.send(Broadcaster.frame(0, new StateEvent(last.version(), last.snapshot(), last.hash(), List.of())));
            if (result != null)
                sink.send(Broadcaster.frame(0, result));
        } catch (IOException e) {
            // the client has gone already
        } finally {
            sink.close();
        }
    }

    /** Sends a keep-alive comment on every open stream (called by the keep-alive thread). */
    public void keepAlive() {
        broadcaster.keepAlive();
    }

    /** Interrupts the game thread (the game ends as ABORTED) and waits for it to finish. */
    public void shutdown(long timeoutMs) throws InterruptedException {
        gameThread.interrupt();
        gameThread.join(timeoutMs);
    }

    public boolean isFinished() {
        return closed;
    }

    public String id() {
        return id;
    }

    /** The current game's seed (a new one for every next game of the session). */
    public long seed() {
        return seed;
    }

    /** 1 for the session's first game, then 2, 3, ... (the server starts each next game by itself). */
    public int gameNumber() {
        return gameNumber;
    }

    public long turnDelayMs() {
        return turnDelayMs;
    }

    /** When this game ends (Rule 11): at the first winner or when every place is decided. */
    public EndCondition endCondition() {
        return endCondition;
    }

    public long version() {
        return version.get();
    }

    /** The last STATE broadcast, or null before the first. */
    public StateView latestState() {
        return latest;
    }

    public String stateName() {
        return stateName;
    }

    public int joined() {
        return joined;
    }

    /** Colours that have joined: an immutable set, replaced by the game thread on every JOIN. */
    public Set<PlayerColor> taken() {
        return taken;
    }

    public Thread gameThread() {
        return gameThread;
    }

    /** The open ROLL_REQUEST or DECISION_REQUEST the game is waiting for, or null. */
    public ServerEvent openRequest() {
        return openRequest;
    }

    public int queueCapacity() {
        return config.queueCapacity();
    }

    /** Commands waiting for the game thread right now. */
    public int queueDepth() {
        return queue.size();
    }

    /** The most commands that were ever waiting at once (high-water mark): above 1 means requests were queued. */
    public int peakQueueDepth() {
        return peakQueueDepth.get();
    }

    /** Requests answered 2xx. */
    public long accepted() {
        return accepted.get();
    }

    /** Requests answered 409 (stale, wrong turn, game over). */
    public long rejected() {
        return rejected.get();
    }

    /** Requests answered 503 (queue full or shutting down); the client retries them. */
    public long refused() {
        return refused.get();
    }

    /** Requests answered with any other error (400, 504). */
    public long otherErrors() {
        return otherErrors.get();
    }

    // --- used by the game thread (Coordinator) ---

    ServerConfig config() {
        return config;
    }

    ServerLog log() {
        return log;
    }

    long nextVersion() {
        return version.incrementAndGet();
    }

    void publishLatest(StateView view) {
        latest = view;
    }

    void publishOpenRequest(ServerEvent request) {
        openRequest = request;
    }

    void publishStateName(String name) {
        stateName = name;
    }

    void publishJoined(int count) {
        joined = count;
    }

    void publishTaken(Set<PlayerColor> colours) {
        taken = Set.copyOf(colours);
    }

    void publishNextGame(int number, long newSeed) {
        seed = newSeed;
        gameNumber = number;
    }

    /** After the session's last game: no more requests, no more event streams. */
    void markClosed(GameOverEvent gameOver) {
        result = gameOver;
        closed = true;
    }

    private void streamLost(EventSink sink) {
        queue.offer(new Command.Lost(sink)); // best effort: the ack barrier also checks connections itself
    }
}
