package ludo.players;

import ludo.shared.PathMath;
import ludo.shared.PlayerColor;
import ludo.shared.decision.PieceChoice;
import ludo.shared.snapshot.GameSnapshot;
import ludo.shared.snapshot.PieceSnapshot;

import java.util.List;
import java.util.stream.Collectors;


final class SnapshotQueries {

    private SnapshotQueries() {}

    /** The player's pieces that are candidates, in piece-number order. */
    static List<PieceSnapshot> ownCandidates(GameSnapshot snapshot, PlayerColor color, List<Integer> candidates) {
        return snapshot.piecesOf(color).stream()
                .filter(p -> candidates.contains(p.number()))
                .collect(Collectors.toList());
    }

    static List<PieceSnapshot> opponentsOnMainPath(GameSnapshot snapshot, PlayerColor color) {
        return snapshot.pieces().stream()
                .filter(p -> p.color() != color)
                .filter(PieceSnapshot::isOnMainPath)
                .collect(Collectors.toList());
    }

    /** Main-path cell this piece would reach with {@code steps}, ignoring effects and the home straight. */
    static int landing(PieceSnapshot piece, int steps) {
        return PathMath.advance(piece.position(), steps, piece.direction());
    }

    static int distanceToApproach(PieceSnapshot piece) {
        return PathMath.stepsToApproach(piece.position(), piece.color(), piece.direction());
    }

    static boolean landsOnOpponent(PieceSnapshot piece, int steps, List<PieceSnapshot> opponents) {
        int target = landing(piece, steps);
        return opponents.stream().anyMatch(op -> op.position() == target);
    }

    /** True when two or more of the given pieces share this piece's main-path cell. */
    static boolean isInBlock(PieceSnapshot piece, List<PieceSnapshot> pieces) {
        return pieces.stream()
                .filter(PieceSnapshot::isOnMainPath)
                .filter(p -> p.position() == piece.position())
                .count() >= 2;
    }

    static PieceChoice choice(PieceSnapshot piece) {
        return piece == null ? PieceChoice.none() : PieceChoice.of(piece.number());
    }
}
