package ludo.client.control;

import ludo.client.net.ConnectionState;
import ludo.shared.protocol.GameOverEvent;
import ludo.shared.protocol.NewGameEvent;
import ludo.shared.protocol.PausedEvent;
import ludo.shared.protocol.ResumedEvent;
import ludo.shared.protocol.ServerEvent;
import ludo.shared.protocol.StateEvent;

import java.util.concurrent.CompletableFuture;

/**
 * What the controller needs from a screen (port; Dependency Inversion). The controller knows
 * nothing about Swing: the GUI ({@code SwingGameView}), the console ({@code ConsoleGameView}) and the
 * tests' fake all implement this. Methods may be called from any thread; implementations must
 * move the work to their own thread (the GUI uses the Event Dispatch Thread).
 */
public interface GameView {

    /**
     * Shows a new STATE. The returned future completes only once the state is really on screen,
     * because the controller ACKs only after that ("acknowledged" means "this player sees it").
     *
     * @param synced true if the hash this client computed equals the server's hash
     */
    CompletableFuture<Void> showState(StateEvent state, boolean synced);

    /** A ROLL_REQUEST or DECISION_REQUEST, for any colour: who the game is waiting for. */
    void showRequest(ServerEvent request);

    void showPaused(PausedEvent paused);

    void showResumed(ResumedEvent resumed);

    void showGameOver(GameOverEvent gameOver);

    /** The server starts the next game of the session (same seats): reset the screen for it. */
    void showNewGame(NewGameEvent newGame);

    void showConnection(ConnectionState state);

    /** Something worth telling the user that is not part of the game log (e.g. a refused request). */
    void showWarning(String message);
}
