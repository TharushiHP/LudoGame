package ludo.player.strategy;

import ludo.board.Board;
import ludo.piece.Piece;

import java.util.List;

/**
 * A player's behaviour: which piece to move for a roll, and whether to bring a piece
 * out of base on a six (Strategy pattern; open for new behaviours, closed for change).
 */
public interface MoveStrategy {

    Piece choosePiece(List<Piece> ownPieces, List<Piece> allPieces, Board board, int diceValue);

    boolean prefersMoveFromBase(List<Piece> ownPieces, List<Piece> allPieces, Board board);

    /**
     * Rule 7: when the chosen piece cannot move, may the game ask this strategy again
     * for another piece? True for every behaviour except Blue's fixed cycle.
     */
    default boolean triesOtherPiecesWhenBlocked() {
        return true;
    }
}
