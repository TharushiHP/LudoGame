package ludo.game;

/**
 * Immutable copy of the mystery cell's state (Value Object, Java record).
 *
 * @param cell            main-path cell of the mystery cell, or -1 when none is on the board
 * @param roundsRemaining rounds it stays at that cell
 */
public record MysterySnapshot(int cell, int roundsRemaining) {

    public boolean isActive() {
        return cell >= 0;
    }
}
