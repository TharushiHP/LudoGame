package ludo.game;

import ludo.board.PlayerColor;

/**
 * Port that lets the outside world hold the game between turns (Dependency Inversion: Game
 * depends on this interface, never on HTTP or threads). Game calls it exactly once per turn:
 * {@link #beforeRoll} before the turn's first roll and {@link #afterTurn} after the turn's
 * moves are applied. Bonus rolls inside the same turn (after a six or a capture) do not call it again.
 * <p>
 * Both calls may block. In the server they will wait for the client's ROLL request, for all four
 * acknowledgements (the ack barrier), for the pacing delay, and while the game is paused.
 * The console game uses {@link NoOpTurnGate}.
 */
public interface TurnGate {

    void beforeRoll(PlayerColor color) throws InterruptedException;

    void afterTurn(GameSnapshot snapshot) throws InterruptedException;
}
