package ludo.player.strategy;

import ludo.board.Board;
import ludo.board.PlayerColor;
import ludo.piece.Direction;
import ludo.piece.Piece;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Blue's behaviour: moves its pieces in turn (B1, B2, B3, B4, B1, ...), aiming for the mystery
 * cell when moving counterclockwise and avoiding it when moving clockwise.
 * A {@link MoveStrategy} implementation (Strategy pattern).
 * <p>
 * Two cases where the cycle's next piece is not a normal move:
 * <ul>
 *   <li>The next piece is Home (or still at base): it is skipped, and the next piece in
 *       cycle order that is on the board is chosen instead, so finished pieces do not
 *       waste Blue's turns. The cycle then continues after the chosen piece.</li>
 *   <li>The next piece is on the board but cannot move (exact roll needed for Home,
 *       blocked, or in briefing): it is still chosen and Blue's turn is skipped. Blue
 *       sticks to its cycle rather than switching to another piece.</li>
 * </ul>
 */
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

        Piece candidate = nextPieceOnBoardInCycle(ownPieces);

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

    // Skips pieces that are Home or at base; caller guarantees at least one piece is on the board.
    private Piece nextPieceOnBoardInCycle(List<Piece> ownPieces) {
        int size = ownPieces.size();
        for (int offset = 0; offset < size; offset++) {
            int index = (cycleIndex + offset) % size;
            Piece piece = ownPieces.get(index);
            if (piece.isActive()) {
                cycleIndex = (index + 1) % size;
                return piece;
            }
        }
        throw new IllegalStateException("No Blue piece on the board");
    }

    @Override
    public boolean prefersMoveFromBase(List<Piece> ownPieces, List<Piece> allPieces, Board board) {
        return true;
    }
}
