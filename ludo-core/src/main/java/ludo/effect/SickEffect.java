package ludo.effect;

import ludo.shared.EffectKind;
import ludo.shared.BoardConstants;

/**
 * Sick effect from the Alpha teleport: the piece moves half the dice value (rounded down) for four rounds.
 * One of the interchangeable {@link PieceEffect} implementations (Strategy pattern, OCP).
 */
public class SickEffect implements PieceEffect {

    private int roundsRemaining;

    public SickEffect() {
        this.roundsRemaining = BoardConstants.EFFECT_DURATION_ROUNDS;
    }

    @Override
    public int applyToSteps(int diceValue) {
        return diceValue / 2;
    }

    @Override
    public boolean isActive() {
        return roundsRemaining > 0;
    }

    @Override
    public void decrementRound() {
        if (roundsRemaining > 0) {
            roundsRemaining--;
        }
    }

    @Override
    public String description() {
        return "sick (movement speed halves)";
    }

    @Override
    public EffectKind kind() {
        return EffectKind.SICK;
    }

    @Override
    public int getRoundsRemaining() {
        return roundsRemaining;
    }
}
