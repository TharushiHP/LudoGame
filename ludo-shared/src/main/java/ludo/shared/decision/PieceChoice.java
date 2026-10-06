package ludo.shared.decision;

import java.util.OptionalInt;

/**
 * A player's answer to "which piece?" (Value Object, Java record).
 *
 * @param piece the chosen piece number 1-4, or empty when the player has nothing to choose
 * @param memo  a small number the decider asks the server to keep for it and hand back in the next
 *              snapshot ({@code GameSnapshot.deciderMemo}). Empty means "keep what you have".
 *              Only Blue uses it, for its cycle position, so a client can resume after a reconnect.
 */
public record PieceChoice(OptionalInt piece, OptionalInt memo) {

    public static PieceChoice none() {
        return new PieceChoice(OptionalInt.empty(), OptionalInt.empty());
    }

    public static PieceChoice of(int pieceNumber) {
        return new PieceChoice(OptionalInt.of(pieceNumber), OptionalInt.empty());
    }

    public PieceChoice withMemo(int newMemo) {
        return new PieceChoice(piece, OptionalInt.of(newMemo));
    }
}
