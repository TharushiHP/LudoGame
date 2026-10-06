package ludo.players;

import ludo.shared.PlayerColor;
import ludo.shared.decision.PieceChoice;
import ludo.shared.snapshot.GameSnapshot;
import ludo.shared.snapshot.PieceSnapshot;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static ludo.players.SnapshotQueries.*;

/**
 * Green's behaviour: first get a capture for a piece that has none, then form a block, then move
 * a block piece closest to home, otherwise the piece closest to home. Brings a new piece out on a
 * six unless the six would form a block. A {@link MoveStrategy} (Strategy pattern).
 */
public class BlockingStrategy implements MoveStrategy {

    private static final int SIX = 6;

    private final PlayerColor color;

    public BlockingStrategy(PlayerColor color) {
        this.color = color;
    }

    @Override
    public PieceChoice choosePiece(GameSnapshot snapshot, int roll, List<Integer> candidates) {
        List<PieceSnapshot> own = ownCandidates(snapshot, color, candidates);

        Optional<PieceSnapshot> mustCapture = findPieceNeedingCapture(own, snapshot, roll);
        if (mustCapture.isPresent()) {
            return choice(mustCapture.get());
        }

        Optional<PieceSnapshot> blockFormer = findPieceThatCreatesBlock(own, roll);
        if (blockFormer.isPresent()) {
            return choice(blockFormer.get());
        }

        Optional<PieceSnapshot> blockMover = findPieceInBlockClosestToHome(own);
        if (blockMover.isPresent()) {
            return choice(blockMover.get());
        }

        return choice(findPieceClosestToHome(own));
    }

    private Optional<PieceSnapshot> findPieceNeedingCapture(List<PieceSnapshot> own, GameSnapshot snapshot, int roll) {
        List<PieceSnapshot> opponents = opponentsOnMainPath(snapshot, color);
        return own.stream()
                .filter(PieceSnapshot::isOnMainPath)
                .filter(p -> !p.hasCapture())
                .filter(p -> landsOnOpponent(p, roll, opponents))
                .findFirst();
    }

    private Optional<PieceSnapshot> findPieceThatCreatesBlock(List<PieceSnapshot> own, int roll) {
        return own.stream()
                .filter(PieceSnapshot::isOnMainPath)
                .filter(p -> landsOnOwnPiece(p, roll, own))
                .findFirst();
    }

    private Optional<PieceSnapshot> findPieceInBlockClosestToHome(List<PieceSnapshot> own) {
        return own.stream()
                .filter(PieceSnapshot::isOnMainPath)
                .filter(p -> isInBlock(p, own))
                .min(Comparator.comparingInt(SnapshotQueries::distanceToApproach));
    }

    // A piece on its home straight counts as distance -1, so it is preferred.
    private PieceSnapshot findPieceClosestToHome(List<PieceSnapshot> own) {
        return own.stream()
                .filter(PieceSnapshot::isActive)
                .min(Comparator.comparingInt(p -> p.isOnHomeStraight() ? -1 : distanceToApproach(p)))
                .orElse(null);
    }

    private boolean landsOnOwnPiece(PieceSnapshot piece, int steps, List<PieceSnapshot> own) {
        int target = landing(piece, steps);
        return own.stream()
                .filter(PieceSnapshot::isOnMainPath)
                .filter(other -> other.number() != piece.number())
                .anyMatch(other -> other.position() == target);
    }

    @Override
    public boolean prefersMoveFromBase(GameSnapshot snapshot) {
        List<PieceSnapshot> own = snapshot.piecesOf(color);
        boolean canFormBlockWithSix = own.stream()
                .filter(PieceSnapshot::isOnMainPath)
                .anyMatch(p -> landsOnOwnPiece(p, SIX, own));
        return !canFormBlockWithSix;
    }
}
