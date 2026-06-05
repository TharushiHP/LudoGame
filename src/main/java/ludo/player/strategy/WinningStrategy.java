package ludo.player.strategy;

import ludo.board.Board;
import ludo.board.PlayerColor;
import ludo.piece.Piece;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class WinningStrategy implements MoveStrategy {

    private final PlayerColor color;

    public WinningStrategy(PlayerColor color) {
        this.color = color;
    }

    @Override
    public Piece choosePiece(List<Piece> ownPieces, List<Piece> allPieces, Board board, int diceValue) {
        Optional<Piece> captureNeeded = findPieceNeedingCaptureInRange(ownPieces, allPieces, board, diceValue);
        if (captureNeeded.isPresent()) {
            return captureNeeded.get();
        }
        return findPieceClosestToHome(ownPieces, board);
    }

    private Optional<Piece> findPieceNeedingCaptureInRange(
            List<Piece> ownPieces, List<Piece> allPieces, Board board, int diceValue) {

        List<Piece> opponents = allPieces.stream()
                .filter(p -> !p.getColor().equals(color))
                .filter(Piece::isOnMainPath)
                .collect(Collectors.toList());

        return ownPieces.stream()
                .filter(Piece::isOnMainPath)
                .filter(p -> !p.hasCapture())
                .filter(p -> canReachOpponent(p, diceValue, opponents, board))
                .findFirst();
    }

    private boolean canReachOpponent(Piece piece, int steps, List<Piece> opponents, Board board) {
        int target = board.advance(piece.getMainPathPosition(), steps, piece.getDirection());
        return opponents.stream().anyMatch(op -> op.getMainPathPosition() == target);
    }

    private Piece findPieceClosestToHome(List<Piece> ownPieces, Board board) {
        return ownPieces.stream()
                .filter(Piece::isOnMainPath)
                .min(Comparator.comparingInt(p -> board.distanceToApproach(p)))
                .orElseGet(() -> ownPieces.stream()
                        .filter(Piece::isOnHomeStraight)
                        .findFirst()
                        .orElse(null));
    }

    @Override
    public boolean prefersMoveFromBase(List<Piece> ownPieces, List<Piece> allPieces, Board board) {
        return true;
    }
}
