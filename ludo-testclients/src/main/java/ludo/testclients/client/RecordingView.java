package ludo.testclients.client;

import ludo.client.control.GameView;
import ludo.client.net.ConnectionState;
import ludo.shared.protocol.GameOverEvent;
import ludo.shared.protocol.NewGameEvent;
import ludo.shared.protocol.PausedEvent;
import ludo.shared.protocol.ResumedEvent;
import ludo.shared.protocol.ServerEvent;
import ludo.shared.protocol.StateEvent;
import ludo.shared.protocol.StateHasher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A screen without a screen: the {@link GameView} of a test player (Dependency Inversion: the real
 * {@code ClientController} drives it exactly as it drives the Swing window). It "shows" a STATE at
 * once and records what a checker needs: how many STATEs did not match the server's hash, the hash
 * of the last STATE, every warning and the first GAME_OVER. Called from the client-controller thread
 * and the event-stream thread, so every field is thread-safe.
 */
public final class RecordingView implements GameView {

    private final AtomicInteger states = new AtomicInteger();
    private final AtomicInteger mismatches = new AtomicInteger();
    private final AtomicInteger pauses = new AtomicInteger();
    private final List<String> warnings = Collections.synchronizedList(new ArrayList<>());
    private final CountDownLatch gameOver = new CountDownLatch(1);
    private volatile String lastHash;
    private volatile GameOverEvent result;

    @Override
    public CompletableFuture<Void> showState(StateEvent state, boolean synced) {
        states.incrementAndGet();
        if (!synced)
            mismatches.incrementAndGet();
        lastHash = StateHasher.hash(state.snapshot()); // this client's own hash of what it received
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void showRequest(ServerEvent request) {}

    @Override
    public void showPaused(PausedEvent paused) {
        pauses.incrementAndGet();
    }

    @Override
    public void showResumed(ResumedEvent resumed) {}

    @Override
    public void showGameOver(GameOverEvent over) {
        if (result == null)
            result = over;
        gameOver.countDown();
    }

    @Override
    public void showNewGame(NewGameEvent newGame) {}

    @Override
    public void showConnection(ConnectionState state) {}

    @Override
    public void showWarning(String message) {
        warnings.add(message);
    }

    public boolean awaitGameOver(long timeoutMs) throws InterruptedException {
        return gameOver.await(timeoutMs, TimeUnit.MILLISECONDS);
    }

    public int states() {
        return states.get();
    }

    /** STATEs whose hash, computed here, differed from the server's. */
    public int mismatches() {
        return mismatches.get();
    }

    public int pauses() {
        return pauses.get();
    }

    public List<String> warnings() {
        synchronized (warnings) {
            return List.copyOf(warnings);
        }
    }

    /** This client's hash of the last STATE it received, or null. */
    public String lastHash() {
        return lastHash;
    }

    /** The first GAME_OVER, or null. */
    public GameOverEvent result() {
        return result;
    }
}
