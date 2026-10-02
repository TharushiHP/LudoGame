package ludo.effect;

import ludo.board.BoardConstants;

/**
 * Briefing effect from the Beta mystery cell: the piece cannot move for four rounds, and
 * three consecutive rolls of 3 by its player during the briefing send it back to base (T-13).
 * One of the interchangeable {@link PieceEffect} implementations (Strategy pattern, OCP).
 */
public class BriefingEffect implements PieceEffect {

    private static final int TELEPORT_ROLL = 3;
    private static final int CONSECUTIVE_ROLLS_TO_TELEPORT = 3;

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
        if (diceValue == TELEPORT_ROLL) {
            consecutiveThrees++;
        } else {
            consecutiveThrees = 0;
        }
    }

    public boolean shouldTeleportToBase() {
        return consecutiveThrees >= CONSECUTIVE_ROLLS_TO_TELEPORT;
    }

    @Override
    public EffectKind kind() {
        return EffectKind.BRIEFING;
    }

    @Override
    public int getRoundsRemaining() {
        return roundsRemaining;
    }
}
