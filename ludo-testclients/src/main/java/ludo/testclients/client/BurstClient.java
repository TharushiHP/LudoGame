package ludo.testclients.client;

import ludo.client.net.ConnectionState;
import ludo.client.net.EventStreamListener;
import ludo.client.net.GatewayReply;
import ludo.client.net.HttpServerGateway;
import ludo.client.net.RequestObserver;
import ludo.client.net.RetryPolicy;
import ludo.shared.PlayerColor;
import ludo.shared.protocol.AckCommand;
import ludo.shared.protocol.GameOverEvent;
import ludo.shared.protocol.RollCommand;
import ludo.shared.protocol.RollRequest;
import ludo.shared.protocol.ServerEvent;
import ludo.shared.protocol.StateEvent;
import ludo.shared.protocol.StateHasher;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A fast automatic test client that fires bursts of asynchronous requests at one running game.
 * It has its own HttpClient, its own (spectator) event stream and its own daemon thread
 * {@code burst-client-N}, exactly as if it ran on another PC.
 * <p>
 * Whenever its stream shows a new ROLL_REQUEST for one of the burst seats, it launches a wave of
 * requests in a tight loop, all with {@code sendAsync}, so the whole wave is on its way before any
 * reply comes back. Every burst client sees the same ROLL_REQUEST at about the same moment, so the
 * clients fire simultaneously. A wave of ten requests looks like this (and repeats):
 * <ul>
 *   <li>3 ROLLs for its own seat with the current turnId and version, each with a fresh requestId
 *       (valid only when it is its seat's turn; they race the real player's ROLL)</li>
 *   <li>2 exact duplicates (same requestId) of the wave's first ROLL</li>
 *   <li>2 ACKs of the current version, with the hash recomputed from the STATE it received</li>
 *   <li>1 exact duplicate of the wave's first ACK</li>
 *   <li>2 stale ROLLs (previous turnId and version)</li>
 * </ul>
 * Every reply is recorded as a {@link Sent} so the scenario can check it afterwards.
 */
public final class BurstClient implements EventStreamListener.Callback {

    /** What a request was meant to test. */
    public enum Kind { ROLL, ROLL_DUPLICATE, ACK, ACK_DUPLICATE, ROLL_STALE }

    /** One request and its final answer (status -1: no answer, the request failed). */
    public record Sent(int client, Kind kind, String type, String requestId, long turnId, int status,
                       Map<String, Object> body, long nanos) {}

    private static final long WAVE_WAIT_MS = 30_000;

    private final int index;
    private final String gameId;
    private final PlayerColor seat;
    private final Set<PlayerColor> triggerSeats;
    private final int requests;
    private final int waveSize;
    private final HttpServerGateway gateway;
    private final EventStreamListener listener;
    private final BlockingQueue<RollRequest> triggers = new LinkedBlockingQueue<>();
    private final ConcurrentLinkedQueue<CompletableFuture<Void>> pending = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<Sent> sent = new ConcurrentLinkedQueue<>();
    private final List<Wave> waves = new ArrayList<>(); // burst thread only, read after done
    private final CountDownLatch done = new CountDownLatch(1);
    private final Thread thread;
    private volatile StateEvent latestState;
    private volatile boolean gameOver;
    private volatile int launched;

    /** When a wave finished launching, and when its first reply arrived. */
    private static final class Wave {
        final AtomicLong firstReplyAt = new AtomicLong(Long.MAX_VALUE);
        volatile long allLaunchedAt;
    }

    public BurstClient(int index, String server, String gameId, PlayerColor seat, Set<PlayerColor> triggerSeats,
                       int requests, int waveSize, RequestObserver observer) {
        this.index = index;
        this.gameId = gameId;
        this.seat = seat;
        this.triggerSeats = Set.copyOf(triggerSeats);
        this.requests = requests;
        this.waveSize = waveSize;
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        this.gateway = new HttpServerGateway(http, server, RetryPolicy.standard(), observer);
        this.listener = new EventStreamListener(http, gateway.eventsUri(gameId, null), this);
        this.thread = new Thread(this::run, "burst-client-" + index);
        this.thread.setDaemon(true); // the scenario's main thread decides how long the tool runs
    }

    /** Opens the event stream (waits up to {@code timeoutMs}), then starts the burst thread. */
    public boolean connect(long timeoutMs) throws InterruptedException {
        listener.start();
        boolean connected = listener.awaitConnected(timeoutMs);
        thread.start();
        return connected;
    }

    /** Waits until every wave has been launched (or the game ended first). */
    public boolean awaitLaunched(long timeoutMs) throws InterruptedException {
        return done.await(timeoutMs, TimeUnit.MILLISECONDS);
    }

    /** Waits until every request has its final answer. */
    public boolean awaitReplies(long timeoutMs) throws InterruptedException {
        try {
            CompletableFuture.allOf(pending.toArray(new CompletableFuture[0])).get(timeoutMs, TimeUnit.MILLISECONDS);
            return true;
        } catch (ExecutionException | TimeoutException e) {
            return false;
        }
    }

    public void close() {
        thread.interrupt();
        listener.close();
    }

    public int launched() {
        return launched;
    }

    public List<Sent> sent() {
        return new ArrayList<>(sent);
    }

    public int waves() {
        return waves.size();
    }

    /** Waves whose every request was sent before the first of their replies arrived. */
    public long wavesLaunchedBeforeFirstReply() {
        return waves.stream().filter(w -> w.allLaunchedAt <= w.firstReplyAt.get()).count();
    }

    public PlayerColor seat() {
        return seat;
    }

    // --- EventStreamListener.Callback (event-stream thread) ---

    @Override
    public void onEvent(ServerEvent event) {
        if (event instanceof StateEvent state) {
            latestState = state;
        } else if (event instanceof RollRequest request && triggerSeats.contains(request.colour())) {
            triggers.add(request);
        } else if (event instanceof GameOverEvent) {
            gameOver = true;
            triggers.add(new RollRequest(seat, -1, -1)); // wakes the burst thread
        }
    }

    @Override
    public void onConnection(ConnectionState state) {
        if (state == ConnectionState.CLOSED) {
            gameOver = true;
            triggers.add(new RollRequest(seat, -1, -1));
        }
    }

    @Override
    public void onProblem(String message) {
        // the stream reconnects by itself; the checks look at the replies, not at the stream
    }

    // --- the burst thread ---

    private void run() {
        try {
            while (launched < requests && !gameOver) {
                RollRequest trigger = triggers.poll(WAVE_WAIT_MS, TimeUnit.MILLISECONDS);
                if (trigger == null || gameOver)
                    break;
                RollRequest newer;
                while ((newer = triggers.poll()) != null) // fire at the newest turn only
                    trigger = newer;
                if (trigger.turnId() < 0)
                    break;
                StateEvent state = latestState;
                if (state == null)
                    continue;
                fireWave(trigger, state, Math.min(waveSize, requests - launched));
            }
        } catch (InterruptedException e) {
            // closed
        } finally {
            done.countDown();
        }
    }

    private void fireWave(RollRequest turn, StateEvent state, int size) {
        Wave wave = new Wave();
        waves.add(wave);
        String hash = StateHasher.hash(state.snapshot());
        RollCommand firstRoll = null;
        AckCommand firstAck = null;
        for (int i = 0; i < size; i++) {
            switch (i % 10) {
                case 0, 1, 2 -> {
                    RollCommand roll = new RollCommand(seat, turn.turnId(), turn.version(), newId());
                    if (i % 10 == 0)
                        firstRoll = roll;
                    roll(wave, Kind.ROLL, roll);
                }
                case 3, 4 -> roll(wave, Kind.ROLL_DUPLICATE, firstRoll);
                case 5, 6 -> {
                    AckCommand ack = new AckCommand(seat, state.version(), hash, newId());
                    if (i % 10 == 5)
                        firstAck = ack;
                    ack(wave, Kind.ACK, ack);
                }
                case 7 -> ack(wave, Kind.ACK_DUPLICATE, firstAck);
                default -> roll(wave, Kind.ROLL_STALE,
                        new RollCommand(seat, turn.turnId() - 1, turn.version() - 1, newId()));
            }
        }
        wave.allLaunchedAt = System.nanoTime();
        launched += size;
    }

    private void roll(Wave wave, Kind kind, RollCommand roll) {
        track(wave, kind, "roll", roll.requestId(), roll.turnId(), gateway.roll(gameId, roll));
    }

    private void ack(Wave wave, Kind kind, AckCommand ack) {
        track(wave, kind, "ack", ack.requestId(), -1, gateway.ack(gameId, ack));
    }

    private void track(Wave wave, Kind kind, String type, String requestId, long turnId,
                       CompletableFuture<GatewayReply> reply) {
        long start = System.nanoTime();
        pending.add(reply.handle((answer, error) -> {
            long now = System.nanoTime();
            wave.firstReplyAt.accumulateAndGet(now, Math::min);
            sent.add(new Sent(index, kind, type, requestId, turnId, error == null ? answer.status() : -1,
                    error == null ? answer.body() : Map.of("error", String.valueOf(error)), now - start));
            return null;
        }));
    }

    private static String newId() {
        return UUID.randomUUID().toString();
    }
}
