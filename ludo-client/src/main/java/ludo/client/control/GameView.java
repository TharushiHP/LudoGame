package ludo.client.control;

import ludo.client.net.ConnectionState;
import ludo.shared.protocol.GameOverEvent;
import ludo.shared.protocol.NewGameEvent;
import ludo.shared.protocol.PausedEvent;
import ludo.shared.protocol.ResumedEvent;
import ludo.shared.protocol.ServerEvent;
import ludo.shared.protocol.StateEvent;

import java.util.concurrent.CompletableFuture;

public interface GameView {

    CompletableFuture<Void> showState(StateEvent state, boolean synced);

    void showRequest(ServerEvent request);

    void showPaused(PausedEvent paused);

    void showResumed(ResumedEvent resumed);

    void showGameOver(GameOverEvent gameOver);

    void showNewGame(NewGameEvent newGame);

    void showConnection(ConnectionState state);

    void showWarning(String message);
}
