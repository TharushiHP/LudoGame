package ludo.effect;

import ludo.board.BoardConstants;

/**
 * Energized effect from the Alpha teleport: the piece moves double the dice value for four rounds.
 * One of the interchangeable {@link PieceEffect} implementations (Strategy pattern, OCP).
 */
public class EnergizedEffect implements PieceEffect {

    private int roundsRemaining;

    public EnergizedEffect() {
        this.roundsRemaining = BoardConstants.EFFECT_DURATION_ROUNDS;
    }

    @Override
    public int applyToSteps(int diceValue) {
        return diceValue * 2;
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
        return "energized (movement speed doubles)";
    }

    @Override
    public EffectKind kind() {
        return EffectKind.ENERGIZED;
    }

    @Override
    public int getRoundsRemaining() {
        return roundsRemaining;
    }
}
