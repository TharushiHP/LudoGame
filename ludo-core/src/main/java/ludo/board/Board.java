package ludo.board;

import ludo.shared.BoardConstants;
import ludo.shared.Direction;
import ludo.shared.PathMath;
import ludo.shared.PlayerColor;
import ludo.dice.RandomSource;
import ludo.piece.Piece;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Board geometry and path rules: where a move lands, blocks in the way, and the mystery cell
 * schedule. Pure rule logic with no I/O (Single Responsibility).
 */
public class Board {

    private final MysteryCell mysteryCell;
    private int roundCount;
    private boolean mysterySpawnEnabled;

    public Board(RandomSource random) {
        this.mysteryCell = new MysteryCell(random);
        this.roundCount = 0;
        this.mysterySpawnEnabled = false;
    }

    public void onRoundComplete(List<Piece> allPieces) {
        roundCount++;
        if (!mysterySpawnEnabled && roundCount >= BoardConstants.MYSTERY_SPAWN_AFTER_ROUNDS) {
            if (allPieces.stream().anyMatch(Piece::isOnMainPath)) {
                mysterySpawnEnabled = true;
            }
        }
        if (mysterySpawnEnabled) {
            mysteryCell.trySpawn(occupiedMainPathCells(allPieces));
        }
    }

    private List<Integer> occupiedMainPathCells(List<Piece> pieces) {
        return pieces.stream()
                .filter(Piece::isOnMainPath)
                .map(Piece::getMainPathPosition)
                .collect(Collectors.toList());
    }

    public MoveTarget computeMoveTarget(Piece piece, int steps) {
        if (piece.isOnHomeStraight()) {
            return computeHomeStraightTarget(piece.getHomePathIndex(), steps);
        }

        int current = piece.getMainPathPosition();
        int approach = piece.approachPosition();
        Direction dir = piece.getDirection();

        int stepsToApproach = PathMath.stepsToApproach(current, piece.getColor(), dir);

        if (stepsToApproach < 0 || stepsToApproach > steps) {
            int newPos = advance(current, steps, dir);
            return MoveTarget.mainPath(newPos);
        }

        if (stepsToApproach == steps) {
            return MoveTarget.mainPath(approach);
        }

        int stepsIntoHome = steps - stepsToApproach;
        return computeHomeStraightTarget(-1, stepsIntoHome);
    }

    private MoveTarget computeHomeStraightTarget(int currentHomeIndex, int steps) {
        int newIndex = PathMath.homeStraightIndexAfter(currentHomeIndex, steps);
        if (newIndex < BoardConstants.HOME_STRAIGHT_SIZE) {
            return MoveTarget.homeStraight(newIndex);
        }
        if (newIndex == BoardConstants.HOME_STRAIGHT_SIZE) {
            return MoveTarget.home();
        }
        return MoveTarget.overshoot();
    }

    public int stepsToApproach(Piece piece) {
        return PathMath.stepsToApproach(piece.getMainPathPosition(), piece.getColor(), piece.getDirection());
    }

    public int advance(int position, int steps, Direction direction) {
        return PathMath.advance(position, steps, direction);
    }

    public boolean isOpponentBlock(PlayerColor movingColor, int position, List<Piece> allPieces) {
        long count = allPieces.stream()
                .filter(p -> !p.getColor().equals(movingColor))
                .filter(Piece::isOnMainPath)
                .filter(p -> p.getMainPathPosition() == position)
                .count();
        return count >= 2;
    }

    public int findBlockCell(Piece moving, int steps, List<Piece> allPieces) {
        int current = moving.getMainPathPosition();
        for (int i = 1; i <= steps; i++) {
            int next = advance(current, i, moving.getDirection());
            if (isOpponentBlock(moving.getColor(), next, allPieces)) {
                return next;
            }
        }
        return -1;
    }

    public int findCellBeforeBlock(Piece moving, int steps, List<Piece> allPieces) {
        int current = moving.getMainPathPosition();
        for (int i = 1; i <= steps; i++) {
            int next = advance(current, i, moving.getDirection());
            if (isOpponentBlock(moving.getColor(), next, allPieces)) {
                return advance(current, i - 1, moving.getDirection());
            }
        }
        return -1;
    }

    public boolean pathCrossesBlock(Piece moving, int steps, List<Piece> allPieces) {
        int current = moving.getMainPathPosition();
        for (int i = 1; i <= steps; i++) {
            int next = advance(current, i, moving.getDirection());
            if (isOpponentBlock(moving.getColor(), next, allPieces)) {
                return true;
            }
        }
        return false;
    }

    public List<Piece> piecesAtCell(int position, List<Piece> allPieces) {
        return allPieces.stream()
                .filter(Piece::isOnMainPath)
                .filter(p -> p.getMainPathPosition() == position)
                .collect(Collectors.toList());
    }

    public List<Piece> opponentPiecesAtCell(PlayerColor movingColor, int position, List<Piece> allPieces) {
        return piecesAtCell(position, allPieces).stream()
                .filter(p -> !p.getColor().equals(movingColor))
                .collect(Collectors.toList());
    }

    public boolean hasFriendlyPieceAt(PlayerColor color, int position, Piece excluding, List<Piece> allPieces) {
        return allPieces.stream()
                .filter(p -> p.getColor().equals(color))
                .filter(p -> p != excluding)
                .filter(Piece::isOnMainPath)
                .anyMatch(p -> p.getMainPathPosition() == position);
    }

    public boolean hasSameColorBlock(int position, PlayerColor color, List<Piece> allPieces) {
        return allPieces.stream()
                .filter(p -> p.getColor().equals(color))
                .filter(Piece::isOnMainPath)
                .filter(p -> p.getMainPathPosition() == position)
                .count() >= 2;
    }

    public int distanceToApproach(Piece piece) {
        return PathMath.stepsToApproach(piece.getMainPathPosition(), piece.getColor(), piece.getDirection());
    }

    public MysteryCell getMysteryCell() {
        return mysteryCell;
    }

    public boolean isMysteryActive() {
        return mysteryCell.isActive();
    }

    public int getRoundCount() {
        return roundCount;
    }
}
