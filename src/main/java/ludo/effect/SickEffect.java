package ludo.effect;

import ludo.board.BoardConstants;

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

    public int getRoundsRemaining() {
        return roundsRemaining;
    }
}
