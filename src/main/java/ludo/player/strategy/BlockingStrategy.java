package ludo.player.strategy;

import ludo.board.Board;
import ludo.board.PlayerColor;
import ludo.piece.Piece;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class BlockingStrategy implements MoveStrategy {

    private final PlayerColor color;

    public BlockingStrategy(PlayerColor color) {
        this.color = color;
    }

    @Override
    public Piece choosePiece(List<Piece> ownPieces, List<Piece> allPieces, Board board, int diceValue) {

        Optional<Piece> mustCapture = findPieceNeedingCapture(ownPieces, allPieces, board, diceValue);
        if (mustCapture.isPresent()) {
            return mustCapture.get();
        }

        Optional<Piece> blockFormer = findPieceThatCreatesBlock(ownPieces, diceValue, board);
        if (blockFormer.isPresent()) {
            return blockFormer.get();
        }

        Optional<Piece> blockMover = findPieceInBlockClosestToHome(ownPieces, board);
        if (blockMover.isPresent()) {
            return blockMover.get();
        }

        return findPieceClosestToHome(ownPieces, board);
    }

    private Optional<Piece> findPieceNeedingCapture(List<Piece> ownPieces, List<Piece> allPieces,
            Board board, int diceValue) {
        List<Piece> opponents = opponents(allPieces);
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

    private Optional<Piece> findPieceThatCreatesBlock(List<Piece> ownPieces, int diceValue, Board board) {
        return ownPieces.stream()
                .filter(Piece::isOnMainPath)
                .filter(p -> {
                    int target = board.advance(p.getMainPathPosition(), diceValue, p.getDirection());
                    return ownPieces.stream()
                            .filter(Piece::isOnMainPath)
                            .filter(other -> other != p)
                            .anyMatch(other -> other.getMainPathPosition() == target);
                })
                .findFirst();
    }

    private Optional<Piece> findPieceInBlockClosestToHome(List<Piece> ownPieces, Board board) {
        return ownPieces.stream()
                .filter(Piece::isOnMainPath)
                .filter(p -> isInBlock(p, ownPieces))
                .min(Comparator.comparingInt(board::distanceToApproach));
    }

    private Piece findPieceClosestToHome(List<Piece> ownPieces, Board board) {
        return ownPieces.stream()
                .filter(Piece::isActive)
                .min(Comparator.comparingInt(p -> p.isOnHomeStraight()
                        ? -1
                        : board.distanceToApproach(p)))
                .orElse(null);
    }

    private boolean isInBlock(Piece piece, List<Piece> ownPieces) {
        return ownPieces.stream()
                .filter(Piece::isOnMainPath)
                .filter(p -> p.getMainPathPosition() == piece.getMainPathPosition())
                .count() >= 2;
    }

    private List<Piece> opponents(List<Piece> allPieces) {
        return allPieces.stream()
                .filter(p -> !p.getColor().equals(color))
                .filter(Piece::isOnMainPath)
                .collect(Collectors.toList());
    }

    @Override
    public boolean prefersMoveFromBase(List<Piece> ownPieces, List<Piece> allPieces, Board board) {
        return !canFormBlockWithSix(ownPieces, board);
    }

    private boolean canFormBlockWithSix(List<Piece> ownPieces, Board board) {
        return ownPieces.stream()
                .filter(Piece::isOnMainPath)
                .anyMatch(p -> {
                    int target = board.advance(p.getMainPathPosition(), 6, p.getDirection());
                    return ownPieces.stream()
                            .filter(Piece::isOnMainPath)
                            .filter(other -> other != p)
                            .anyMatch(other -> other.getMainPathPosition() == target);
                });
    }
}
