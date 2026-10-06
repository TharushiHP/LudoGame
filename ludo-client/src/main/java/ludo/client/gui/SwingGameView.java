package ludo.client.gui;

import ludo.client.control.GameView;
import ludo.client.net.ConnectionState;
import ludo.shared.protocol.GameOverEvent;
import ludo.shared.protocol.PausedEvent;
import ludo.shared.protocol.ResumedEvent;
import ludo.shared.protocol.ServerEvent;
import ludo.shared.protocol.StateEvent;

import javax.swing.SwingUtilities;
import java.util.concurrent.CompletableFuture;

/**
 * The Swing implementation of the controller's {@link GameView} port (Adapter). Each call comes
 * from a client thread and is moved to the Event Dispatch Thread with {@code invokeLater}; Swing
 * components are only ever touched there. Nothing slow runs on the EDT: just setting labels and
 * repainting.
 */
public final class SwingGameView implements GameView {

    private final MainWindow window;

    public SwingGameView(MainWindow window) {
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
    public void showConnection(ConnectionState state) {
        SwingUtilities.invokeLater(() -> window.showConnection(state));
    }

    @Override
    public void showWarning(String message) {
        SwingUtilities.invokeLater(() -> window.showWarning(message));
    }
}
