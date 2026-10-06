package ludo.client.gui;

import ludo.client.control.GameView;
import ludo.client.net.ConnectionState;
import ludo.shared.protocol.GameOverEvent;
import ludo.shared.protocol.NewGameEvent;
import ludo.shared.protocol.PausedEvent;
import ludo.shared.protocol.ResumedEvent;
import ludo.shared.protocol.ServerEvent;
import ludo.shared.protocol.StateEvent;

import javax.swing.SwingUtilities;
import java.util.concurrent.CompletableFuture;

/**
 * The Swing implementation of the controller's {@link GameView} port (Adapter). Each call comes
 * from a client thread and is moved to the Event Dispatch Thread with {@code invokeLater}; Swing
 * components are only ever touched there. Nothing slow runs on the EDT: just storing the state and
 * repainting.
 * <p>
 * {@link #showState} completes as soon as the window has <b>applied</b> the state, never after an
 * animation: in the spectator window tokens then walk towards a state that is already applied,
 * and a player window draws it at once (see {@link GameWindow}). So the controller's ACK timing
 * is exactly as before.
 */
public final class SwingGameView implements GameView {

    private final GameWindow window;

    public SwingGameView(GameWindow window) {
        this.window = window;
    }

    /** Completes on the EDT, right after the window has applied the state. */
    @Override
    public CompletableFuture<Void> showState(StateEvent state, boolean synced) {
        CompletableFuture<Void> applied = new CompletableFuture<>();
        SwingUtilities.invokeLater(() -> {
            try {
                window.applyState(state, synced);
                applied.complete(null);
            } catch (RuntimeException e) {
                applied.completeExceptionally(e);
            }
        });
        return applied;
    }

    @Override
    public void showRequest(ServerEvent request) {
        SwingUtilities.invokeLater(() -> window.showRequest(request));
    }

    @Override
    public void showPaused(PausedEvent paused) {
        SwingUtilities.invokeLater(() -> window.showPaused(paused));
    }

    @Override
    public void showResumed(ResumedEvent resumed) {
        SwingUtilities.invokeLater(() -> window.showResumed(resumed));
    }

    @Override
    public void showGameOver(GameOverEvent gameOver) {
        SwingUtilities.invokeLater(() -> window.showGameOver(gameOver));
    }

    @Override
    public void showNewGame(NewGameEvent newGame) {
        SwingUtilities.invokeLater(() -> window.showNewGame(newGame));
    }

    @Override
    public void showConnection(ConnectionState state) {
        SwingUtilities.invokeLater(() -> window.showConnection(state));
    }

    @Override
    public void showWarning(String message) {
        SwingUtilities.invokeLater(() -> window.showWarning(message));
    }
}
