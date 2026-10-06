package ludo.client.console;

import ludo.client.control.GameView;
import ludo.client.control.Identity;
import ludo.client.net.ConnectionState;
import ludo.shared.protocol.GameOverEvent;
import ludo.shared.protocol.NewGameEvent;
import ludo.shared.protocol.PausedEvent;
import ludo.shared.protocol.ResumedEvent;
import ludo.shared.protocol.ServerEvent;
import ludo.shared.protocol.StateEvent;

import java.io.PrintStream;
import java.util.concurrent.CompletableFuture;

/**
 * The headless {@link GameView} (--headless): prints the game log exactly as the server sends it,
 * plus client status lines prefixed with "[client RED]". A STATE counts as shown as soon as it is
 * printed. Synchronised, because the controller, the listener and the decision worker may all print.
 */
public final class ConsoleGameView implements GameView {

    private final PrintStream out;
    private final String prefix;

    public ConsoleGameView(PrintStream out, Identity me) {
        this.out = out;
        this.prefix = "[client " + (me.isSpectator() ? "spectator" : me.colour().name()) + "] ";
    }

    @Override
    public synchronized CompletableFuture<Void> showState(StateEvent state, boolean synced) {
        state.log().forEach(out::println);
        if (!synced)
            out.println(prefix + "v" + state.version() + ": hash differs from the server's");
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void showRequest(ServerEvent request) {
        // The log already says what happened; requests are only of interest in the GUI.
    }

    @Override
    public synchronized void showPaused(PausedEvent paused) {
        out.println(prefix + "Paused: waiting for " + paused.colour().display() + " (" + paused.reason() + ")");
    }

    @Override
    public synchronized void showResumed(ResumedEvent resumed) {
        out.println(prefix + "Resumed" + (resumed.substituted() ? ": the server now plays " + resumed.colour().display() : ""));
    }

    @Override
    public synchronized void showGameOver(GameOverEvent gameOver) {
        out.println(prefix + "Game over (" + gameOver.status() + "): " + gameOver.finishPositions()
                + (gameOver.hasNextGame() ? "; next game in " + Math.round(gameOver.nextGameInMs() / 1000.0) + " s" : ""));
    }

    @Override
    public synchronized void showNewGame(NewGameEvent newGame) {
        out.println(prefix + "=== Game " + newGame.gameNumber() + " (seed " + newGame.seed() + ") ===");
    }

    @Override
    public synchronized void showConnection(ConnectionState state) {
        out.println(prefix + state.display());
    }

    @Override
    public synchronized void showWarning(String message) {
        out.println(prefix + message);
    }
}
