package ludo.players;

import ludo.shared.Direction;
import ludo.shared.PlayerColor;
import ludo.shared.decision.PieceChoice;
import ludo.shared.snapshot.GameSnapshot;
import ludo.shared.snapshot.PieceSnapshot;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static ludo.players.SnapshotQueries.*;

/**
 * Blue's behaviour: moves its pieces in turn (B1, B2, B3, B4, B1, ...), aiming for the mystery
 * cell when moving counterclockwise and avoiding it when moving clockwise. A piece without a
 * capture that can capture always goes first. A {@link MoveStrategy} (Strategy pattern).
 * <p>
 * The strategy itself is stateless. Its cycle position travels as the decider memo: it is read
 * from {@link GameSnapshot#deciderMemo()} (0 when absent) and the next position is returned with
 * the choice, so the server keeps it and a reconnecting client resumes exactly where it was.
 * <p>
 * Two cases where the cycle's next piece is not a normal move:
 * <ul>
 *   <li>The next piece is Home (or still at base): it is skipped, and the next piece in
 *       cycle order that is on the board is chosen instead, so finished pieces do not
 *       waste Blue's turns. The cycle then continues after the chosen piece.</li>
 *   <li>The next piece is on the board but cannot move (exact roll needed for Home,
 *       blocked, or in briefing): it is still chosen and Blue's turn is skipped. Blue
 *       sticks to its cycle rather than switching to another piece. This is the one
 *       exception to the Rule 7 fallback ({@link #triesOtherPiecesWhenBlocked()} is false);
 *       a blocked piece may still move up to the cell before the block (T-3).</li>
 * </ul>
 */
public class CyclicStrategy implements MoveStrategy {

    private final PlayerColor color;

    public CyclicStrategy(PlayerColor color) {
        this.color = color;
    }

    @Override
    public PieceChoice choosePiece(GameSnapshot snapshot, int roll, List<Integer> candidates) {
        List<PieceSnapshot> own = ownCandidates(snapshot, color, candidates);

        Optional<PieceSnapshot> mustCapture = findPieceNeedingCapture(own, snapshot, roll);
        if (mustCapture.isPresent()) {
            return choice(mustCapture.get());   // cycle position unchanged
        }

        List<PieceSnapshot> movable = own.stream()
                .filter(PieceSnapshot::isActive)
                .collect(Collectors.toList());
        if (movable.isEmpty()) {
            return PieceChoice.none();
        }

        int cycleIndex = snapshot.deciderMemo().getOrDefault(color, 0);
        int size = own.size();
        for (int offset = 0; offset < size; offset++) {
            int index = (cycleIndex + offset) % size;
            PieceSnapshot candidate = own.get(index);
            if (candidate.isActive()) {
                PieceSnapshot chosen = candidate.isOnMainPath()
                        ? applyMysteryPreference(candidate, movable, snapshot, roll)
                        : candidate;
                return choice(chosen).withMemo((index + 1) % size);
            }
        }
        throw new IllegalStateException("No Blue piece on the board");
    }

    private Optional<PieceSnapshot> findPieceNeedingCapture(List<PieceSnapshot> own, GameSnapshot snapshot, int roll) {
        List<PieceSnapshot> opponents = opponentsOnMainPath(snapshot, color);
        return own.stream()
                .filter(PieceSnapshot::isOnMainPath)
                .filter(p -> !p.hasCapture())
                .filter(p -> landsOnOpponent(p, roll, opponents))
                .findFirst();
    }

    private PieceSnapshot applyMysteryPreference(PieceSnapshot candidate, List<PieceSnapshot> movable,
            GameSnapshot snapshot, int roll) {
        if (!snapshot.mystery().isActive())
            return candidate;
        int mystery = snapshot.mystery().cell();
        boolean lands = landing(candidate, roll) == mystery;

        if (candidate.direction() == Direction.COUNTER_CLOCKWISE) {
            if (lands)
                return candidate;
            return movable.stream()
                    .filter(PieceSnapshot::isOnMainPath)
                    .filter(p -> p.direction() == Direction.COUNTER_CLOCKWISE)
                    .filter(p -> landing(p, roll) == mystery)
                    .findFirst()
                    .orElse(candidate);
        }

        if (!lands)
            return candidate;
        return movable.stream()
                .filter(PieceSnapshot::isOnMainPath)
                .filter(p -> landing(p, roll) != mystery)
                .findFirst()
                .orElse(candidate);
    }

    @Override
    public boolean prefersMoveFromBase(GameSnapshot snapshot) {
        return true;
    }

    @Override
    public boolean triesOtherPiecesWhenBlocked() {
        return false;
    }
}
