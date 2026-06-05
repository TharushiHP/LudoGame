package ludo.player.strategy;

import ludo.board.Board;
import ludo.board.BoardConstants;
import ludo.board.PlayerColor;
import ludo.piece.Piece;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class AggressiveStrategy implements MoveStrategy {

    private final PlayerColor color;

    public AggressiveStrategy(PlayerColor color) {
        this.color = color;
    }

    @Override
    public Piece choosePiece(List<Piece> ownPieces, List<Piece> allPieces, Board board, int diceValue) {
        Optional<Piece> capturingPiece = findPieceToCapture(ownPieces, allPieces, board, diceValue);
        if (capturingPiece.isPresent()) {
            return capturingPiece.get();
        }
        return findActivePiecePreferringNoBlock(ownPieces, board);
    }

    private Optional<Piece> findPieceToCapture(List<Piece> ownPieces, List<Piece> allPieces, Board board, int diceValue) {
        List<Piece> opponentPieces = allPieces.stream()
                .filter(p -> !p.getColor().equals(color))
                .filter(Piece::isOnMainPath)
                .collect(Collectors.toList());

        return ownPieces.stream()
                .filter(Piece::isOnMainPath)
                .filter(p -> canCaptureOpponent(p, diceValue, opponentPieces, board))
                .min(Comparator.comparingInt((Piece p) -> board.distanceToApproach(p)));
    }

    private boolean canCaptureOpponent(Piece piece, int steps, List<Piece> opponents, Board board) {
        int target = board.advance(piece.getMainPathPosition(), steps, piece.getDirection());
        return opponents.stream().anyMatch(op -> op.getMainPathPosition() == target);
    }

    private Piece findActivePiecePreferringNoBlock(List<Piece> ownPieces, Board board) {
        List<Piece> active = ownPieces.stream()
                .filter(Piece::isOnMainPath)
                .collect(Collectors.toList());

        if (active.isEmpty()) {
            Piece homePiece = ownPieces.stream()
                    .filter(Piece::isOnHomeStraight)
                    .findFirst()
                    .orElse(null);
            if (homePiece != null) return homePiece;
            return ownPieces.stream().filter(Piece::isAtBase).findFirst().orElse(null);
        }

        return active.stream()
                .filter(p -> !isInBlock(p, ownPieces))
                .findFirst()
                .orElse(active.get(0));
    }

    private boolean isInBlock(Piece piece, List<Piece> ownPieces) {
        long count = ownPieces.stream()
                .filter(Piece::isOnMainPath)
                .filter(p -> p.getMainPathPosition() == piece.getMainPathPosition())
                .count();
        return count >= 2;
    }

    @Override
    public boolean prefersMoveFromBase(List<Piece> ownPieces, List<Piece> allPieces, Board board) {
        long activeCount = ownPieces.stream().filter(Piece::isOnMainPath).count();
        if (activeCount == 0) {
            return true;
        }
        boolean canCapture = canCaptureAnyOpponent(ownPieces, allPieces, board);
        return !canCapture;
    }

    private boolean canCaptureAnyOpponent(List<Piece> ownPieces, List<Piece> allPieces, Board board) {
        List<Piece> opponents = allPieces.stream()
                .filter(p -> !p.getColor().equals(color))
                .filter(Piece::isOnMainPath)
                .collect(Collectors.toList());

        return ownPieces.stream()
                .filter(Piece::isOnMainPath)
                .anyMatch(p -> canCaptureOpponent(p, BoardConstants.MOVE_FROM_BASE_ROLL, opponents, board));
    }
}
