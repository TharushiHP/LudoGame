package ludo.player.strategy;

import ludo.board.Board;
import ludo.piece.Piece;

import java.util.List;

public interface MoveStrategy {

    Piece choosePiece(List<Piece> ownPieces, List<Piece> allPieces, Board board, int diceValue);

    boolean prefersMoveFromBase(List<Piece> ownPieces, List<Piece> allPieces, Board board);
}
