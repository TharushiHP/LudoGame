package ludo.game;

import ludo.shared.snapshot.GameSnapshot;
import ludo.shared.PlayerColor;

/**
 * A {@link TurnGate} that never waits, so the console game runs at full speed
 * (Null Object pattern: Game needs no "is there a gate?" checks).
 */
public class NoOpTurnGate implements TurnGate {

    @Override
    public void beforeRoll(PlayerColor color) {
        // nothing to wait for in the console game
    }

    @Override
    public void afterRoll(GameSnapshot snapshot) {
        // nothing to wait for in the console game
    }
}
