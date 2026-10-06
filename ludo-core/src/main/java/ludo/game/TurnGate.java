package ludo.game;

import ludo.shared.snapshot.GameSnapshot;
import ludo.shared.PlayerColor;

/**
 * Port that lets the outside world hold the game between rolls (Dependency Inversion: Game
 * depends on this interface, never on HTTP or threads). Game calls it for EVERY roll, including
 * bonus rolls after a six or a capture: {@link #beforeRoll} just before the dice are rolled and
 * {@link #afterRoll} after that roll's move has been applied.
 * <p>
 * Both calls may block. In the server they will wait for the client's ROLL request, for all four
 * acknowledgements (the ack barrier), for the pacing delay, and while the game is paused.
 * The console game uses {@link NoOpTurnGate}.
 */
public interface TurnGate {

    void beforeRoll(PlayerColor color) throws InterruptedException;

    void afterRoll(GameSnapshot snapshot) throws InterruptedException;
}
