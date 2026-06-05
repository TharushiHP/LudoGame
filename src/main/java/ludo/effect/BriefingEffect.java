package ludo.effect;

import ludo.board.BoardConstants;

public class BriefingEffect implements PieceEffect {

    private int roundsRemaining;
    private int consecutiveThrees;

    public BriefingEffect() {
        this.roundsRemaining = BoardConstants.EFFECT_DURATION_ROUNDS;
        this.consecutiveThrees = 0;
    }

    @Override
    public int applyToSteps(int diceValue) {
        return 0;
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
        return "in briefing (cannot move for " + roundsRemaining + " rounds)";
    }

    public void recordRoll(int diceValue) {
        if (diceValue == 3) {
            consecutiveThrees++;
        } else {
            consecutiveThrees = 0;
        }
    }

    public boolean shouldTeleportToBase() {
        return consecutiveThrees >= 2;
    }

    public int getRoundsRemaining() {
        return roundsRemaining;
    }
}
