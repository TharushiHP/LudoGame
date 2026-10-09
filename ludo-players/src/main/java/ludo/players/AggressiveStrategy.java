package ludo.players;

import ludo.shared.BoardConstants;
import ludo.shared.PlayerColor;
import ludo.shared.decision.PieceChoice;
import ludo.shared.snapshot.GameSnapshot;
import ludo.shared.snapshot.PieceSnapshot;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static ludo.players.SnapshotQueries.*;


public class AggressiveStrategy implements MoveStrategy {

    private final PlayerColor color;

    public AggressiveStrategy(PlayerColor color) {
        this.color = color;
    }

    @Override
    public PieceChoice choosePiece(GameSnapshot snapshot, int roll, List<Integer> candidates) {
        List<PieceSnapshot> own = ownCandidates(snapshot, color, candidates);
        Optional<PieceSnapshot> capturing = findPieceToCapture(own, snapshot, roll);
        if (capturing.isPresent()) {
            return choice(capturing.get());
        }
        return choice(findActivePiecePreferringNoBlock(own));
    }

    private Optional<PieceSnapshot> findPieceToCapture(List<PieceSnapshot> own, GameSnapshot snapshot, int roll) {
        List<PieceSnapshot> opponents = opponentsOnMainPath(snapshot, color);
        return own.stream()
                .filter(PieceSnapshot::isOnMainPath)
                .filter(p -> landsOnOpponent(p, roll, opponents))
                .min(Comparator.comparingInt(SnapshotQueries::distanceToApproach));
    }

    private PieceSnapshot findActivePiecePreferringNoBlock(List<PieceSnapshot> own) {
        List<PieceSnapshot> active = own.stream()
                .filter(PieceSnapshot::isOnMainPath)
                .collect(Collectors.toList());

        if (active.isEmpty()) {
            PieceSnapshot homePiece = own.stream()
                    .filter(PieceSnapshot::isOnHomeStraight)
                    .findFirst()
                    .orElse(null);
            if (homePiece != null) return homePiece;
            return own.stream().filter(PieceSnapshot::isAtBase).findFirst().orElse(null);
        }

        return active.stream()
                .filter(p -> !isInBlock(p, own))
                .findFirst()
                .orElse(active.get(0));
    }

    @Override
    public boolean prefersMoveFromBase(GameSnapshot snapshot) {
        List<PieceSnapshot> own = snapshot.piecesOf(color);
        long activeCount = own.stream().filter(PieceSnapshot::isOnMainPath).count();
        if (activeCount == 0) {
            return true;
        }
        List<PieceSnapshot> opponents = opponentsOnMainPath(snapshot, color);
        boolean canCapture = own.stream()
                .filter(PieceSnapshot::isOnMainPath)
                .anyMatch(p -> landsOnOpponent(p, BoardConstants.MOVE_FROM_BASE_ROLL, opponents));
        return !canCapture;
    }
}
