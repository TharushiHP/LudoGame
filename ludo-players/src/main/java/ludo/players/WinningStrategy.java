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
 * Yellow's behaviour (speedrunner): get a capture for a piece that has none if it is in range,
 * otherwise move the piece closest to home. Always brings a new piece out on a six.
 * A {@link MoveStrategy} (Strategy pattern).
 */
public class WinningStrategy implements MoveStrategy {

    private final PlayerColor color;

    public WinningStrategy(PlayerColor color) {
        this.color = color;
    }

    @Override
    public PieceChoice choosePiece(GameSnapshot snapshot, int roll, List<Integer> candidates) {
        List<PieceSnapshot> own = ownCandidates(snapshot, color, candidates);
        List<PieceSnapshot> opponents = opponentsOnMainPath(snapshot, color);

        Optional<PieceSnapshot> captureNeeded = own.stream()
                .filter(PieceSnapshot::isOnMainPath)
                .filter(p -> !p.hasCapture())
                .filter(p -> landsOnOpponent(p, roll, opponents))
                .findFirst();
        if (captureNeeded.isPresent()) {
            return choice(captureNeeded.get());
        }
        return choice(findPieceClosestToHome(own));
    }

    private PieceSnapshot findPieceClosestToHome(List<PieceSnapshot> own) {
        return own.stream()
                .filter(PieceSnapshot::isOnMainPath)
                .min(Comparator.comparingInt(SnapshotQueries::distanceToApproach))
                .orElseGet(() -> own.stream()
                        .filter(PieceSnapshot::isOnHomeStraight)
                        .findFirst()
                        .orElse(null));
    }

    @Override
    public boolean prefersMoveFromBase(GameSnapshot snapshot) {
        return true;
    }
}
