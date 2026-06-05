package ludo.player.strategy;

import ludo.board.Board;
import ludo.board.PlayerColor;
import ludo.piece.Direction;
import ludo.piece.Piece;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class CyclicStrategy implements MoveStrategy {

    private final PlayerColor color;
    private int cycleIndex;

    public CyclicStrategy(PlayerColor color) {
        this.color = color;
        this.cycleIndex = 0;
    }

    @Override
    public Piece choosePiece(List<Piece> ownPieces, List<Piece> allPieces, Board board, int diceValue) {

        Optional<Piece> mustCapture = findPieceNeedingCapture(ownPieces, allPieces, board, diceValue);
        if (mustCapture.isPresent()) {
            return mustCapture.get();
        }

        List<Piece> movable = ownPieces.stream()
                .filter(Piece::isActive)
                .collect(Collectors.toList());

        if (movable.isEmpty()) {
            return null;
        }

        Piece candidate = ownPieces.get(cycleIndex % ownPieces.size());
        advanceCycle(ownPieces.size());

        if (!candidate.isActive()) {
            candidate = movable.get(0);
        }

        if (candidate.isOnMainPath()) {
            return applyMysteryPreference(candidate, movable, board, diceValue);
        }

        return candidate;
    }

    private Optional<Piece> findPieceNeedingCapture(List<Piece> ownPieces, List<Piece> allPieces,
            Board board, int diceValue) {
        List<Piece> opponents = allPieces.stream()
                .filter(p -> !p.getColor().equals(color))
                .filter(Piece::isOnMainPath)
                .collect(Collectors.toList());

        return ownPieces.stream()
                .filter(Piece::isOnMainPath)
                .filter(p -> !p.hasCapture())
                .filter(p -> {
                    int target = board.advance(p.getMainPathPosition(), diceValue, p.getDirection());
                    return opponents.stream().anyMatch(op -> op.getMainPathPosition() == target);
                })
                .findFirst();
    }

    private Piece applyMysteryPreference(Piece candidate, List<Piece> movable, Board board, int diceValue) {
        if (candidate.getDirection() == Direction.COUNTER_CLOCKWISE) {
            return preferMystery(candidate, movable, board, diceValue);
        }
        return avoidMystery(candidate, movable, board, diceValue);
    }

    private Piece preferMystery(Piece candidate, List<Piece> movable, Board board, int diceValue) {
        if (!board.isMysteryActive())
            return candidate;

        int mystery = board.getMysteryCell().getPosition();
        boolean lands = board.advance(
                candidate.getMainPathPosition(), diceValue, candidate.getDirection()) == mystery;
        if (lands)
            return candidate;

        return movable.stream()
                .filter(Piece::isOnMainPath)
                .filter(p -> p.getDirection() == Direction.COUNTER_CLOCKWISE)
                .filter(p -> board.advance(p.getMainPathPosition(), diceValue, p.getDirection()) == mystery)
                .findFirst()
                .orElse(candidate);
    }

    private Piece avoidMystery(Piece candidate, List<Piece> movable, Board board, int diceValue) {
        if (!board.isMysteryActive())
            return candidate;

        int mystery = board.getMysteryCell().getPosition();
        boolean lands = board.advance(
                candidate.getMainPathPosition(), diceValue, candidate.getDirection()) == mystery;
        if (!lands)
            return candidate;

        return movable.stream()
                .filter(Piece::isOnMainPath)
                .filter(p -> board.advance(p.getMainPathPosition(), diceValue, p.getDirection()) != mystery)
                .findFirst()
                .orElse(candidate);
    }

    private void advanceCycle(int size) {
        cycleIndex = (cycleIndex + 1) % size;
    }

    @Override
    public boolean prefersMoveFromBase(List<Piece> ownPieces, List<Piece> allPieces, Board board) {
        return true;
    }
}
