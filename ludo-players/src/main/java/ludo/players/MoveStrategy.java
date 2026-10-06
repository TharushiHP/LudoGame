package ludo.players;

import ludo.shared.decision.PieceChoice;
import ludo.shared.snapshot.GameSnapshot;

import java.util.List;

/**
 * A player's behaviour (Strategy pattern): which piece to move for a roll, and whether to bring a
 * piece out of base on a six. Strategies decide from a {@link GameSnapshot} only, never from live
 * game objects, so the same code can run on a client machine. New behaviours can be added without
 * changing existing ones (Open/Closed).
 */
public interface MoveStrategy {

    /**
     * @param candidates piece numbers the player may choose from (pieces rejected by the
     *                   Rule 7 fallback have been removed)
     */
    PieceChoice choosePiece(GameSnapshot snapshot, int roll, List<Integer> candidates);

    boolean prefersMoveFromBase(GameSnapshot snapshot);

    /** Rule 7: may the game ask again for another piece when the chosen one cannot move? */
    default boolean triesOtherPiecesWhenBlocked() {
        return true;
    }
}
