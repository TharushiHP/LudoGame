package ludo.game;

import ludo.players.SnapshotStrategyDecider;
import ludo.shared.decision.MoveDecider;
import ludo.shared.snapshot.GameStatus;
import ludo.shared.snapshot.PieceSnapshot;
import ludo.shared.snapshot.GameSnapshot;
import ludo.shared.PieceLocation;
import ludo.shared.Direction;
import ludo.shared.PlayerColor;
import ludo.piece.Piece;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import ludo.shared.decision.PieceChoice;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the server-facing ports: GameSnapshot, MoveDecider and TurnGate.
 * Fakes stand in for the server so each port can be checked on its own.
 */
class GamePortsTest {

    // GameBuilder

    @Test
    void builderRefusesToBuildWithoutAMoveDecider() {
        // ludo-core knows no player behaviour, so the caller must always supply one.
        assertThrows(NullPointerException.class, () -> new GameBuilder().withSeed(1).build());
    }

    // GameSnapshot

    @Test
    void snapshotsTakenBackToBackAreEqual() {
        Game game = new GameBuilder().withMoveDecider(new SnapshotStrategyDecider()).withSeed(1).build();
        assertEquals(game.snapshot(), game.snapshot());

        SnapshottingGate gate = new SnapshottingGate();
        Game running = new GameBuilder().withMoveDecider(new SnapshotStrategyDecider()).withSeed(1).withTurnGate(gate).build();
        gate.game = running;
        running.run();

        assertTrue(gate.turnsChecked > 0);
    }

    @Test
    void takingSnapshotsEveryTurnDoesNotChangeTheGame() {
        List<String> plain = new ArrayList<>();
        new GameBuilder().withMoveDecider(new SnapshotStrategyDecider()).withSeed(1).withListener((e, m) -> plain.add(m)).build().run();

        List<String> observed = new ArrayList<>();
        SnapshottingGate gate = new SnapshottingGate();
        Game game = new GameBuilder().withMoveDecider(new SnapshotStrategyDecider()).withSeed(1).withTurnGate(gate).withListener((e, m) -> observed.add(m)).build();
        gate.game = game;
        game.run();

        assertEquals(plain, observed);
    }

    @Test
    void snapshotDescribesTheGameAndCannotBeModified() {
        Game game = new GameBuilder().withMoveDecider(new SnapshotStrategyDecider()).withSeed(1).build();
        GameSnapshot before = game.snapshot();

        assertEquals(GameStatus.NOT_STARTED, before.status());
        assertNull(before.currentPlayer());
        assertEquals(16, before.pieces().size());
        assertTrue(before.pieces().stream().allMatch(p -> p.location() == PieceLocation.BASE));
        assertFalse(before.mystery().isActive());
        assertThrows(UnsupportedOperationException.class, () -> before.pieces().clear());
        assertThrows(UnsupportedOperationException.class, () -> before.finishPositions().put(PlayerColor.RED, 1));

        game.run();
        GameSnapshot after = game.snapshot();

        assertEquals(GameStatus.FINISHED, after.status());
        assertEquals(List.of(1, 2, 3, 4), after.finishPositions().values().stream().sorted().toList());
        assertEquals(game.getTurnCount(), after.turnCount());
        assertEquals(GameStatus.NOT_STARTED, before.status(), "an old snapshot must not change");
    }

    @Test
    void snapshotMarksPiecesInABlock() {
        Game game = new GameBuilder().withMoveDecider(new SnapshotStrategyDecider()).withSeed(1).build();
        place(game, PlayerColor.GREEN, 1, 10);
        place(game, PlayerColor.GREEN, 2, 10);
        place(game, PlayerColor.GREEN, 3, 20);

        List<PieceSnapshot> green = game.snapshot().piecesOf(PlayerColor.GREEN);

        assertTrue(green.get(0).inBlock());
        assertTrue(green.get(1).inBlock());
        assertFalse(green.get(2).inBlock());
        assertEquals(20, green.get(2).position());
    }

    // MoveDecider

    @Test
    void moveDeciderIsAskedWithCandidatesAndAgainWithoutTheBlockedPiece() {
        RecordingDecider decider = new RecordingDecider(true, 1);
        Game game = new GameBuilder().withSeed(1).withMoveDecider(decider).build();
        Piece y2 = blockedYellowPosition(game);

        game.playRoll(PlayerColor.YELLOW, 3);

        assertEquals(List.of("YELLOW roll 3 candidates [1, 2, 3, 4]", "YELLOW roll 3 candidates [2]"), decider.calls);
        assertEquals(40, decider.snapshots.get(0).piecesOf(PlayerColor.YELLOW).get(0).position());
        assertEquals(13, y2.getMainPathPosition());
    }

    @Test
    void moveDeciderThatDoesNotTryOtherPiecesIsAskedOnlyOnce() {
        RecordingDecider decider = new RecordingDecider(false, 1);
        Game game = new GameBuilder().withSeed(1).withMoveDecider(decider).build();
        Piece y2 = blockedYellowPosition(game);

        game.playRoll(PlayerColor.YELLOW, 3);

        assertEquals(List.of("YELLOW roll 3 candidates [1, 2, 3, 4]"), decider.calls);
        assertEquals(10, y2.getMainPathPosition());
    }

    @Test
    void deciderChoosingAPieceThatIsNotACandidateIsRejected() {
        RecordingDecider decider = new RecordingDecider(true, 1);
        Game game = new GameBuilder().withSeed(1).withMoveDecider(decider).build();
        blockedYellowPosition(game);
        decider.answerAfterFirst = 3;   // piece 3 is at base, so not a candidate on a roll of 3

        assertThrows(IllegalStateException.class, () -> game.playRoll(PlayerColor.YELLOW, 3));
    }

    // TurnGate

    // One beforeRoll/afterRoll pair per roll, bonus rolls included; the starting roll-off is not gated.
    @Test
    void turnGateIsCalledBeforeAndAfterEveryRollInOrder() {
        RecordingGate gate = new RecordingGate();
        List<String> rolls = new ArrayList<>();
        Pattern roll = Pattern.compile("^(Red|Green|Yellow|Blue) player rolled (\\d)\\.$");
        Game game = new GameBuilder().withMoveDecider(new SnapshotStrategyDecider()).withSeed(2).withTurnGate(gate).withListener((event, message) -> {
            Matcher m = roll.matcher(message.trim());
            if (m.matches())
                rolls.add(m.group(1).toUpperCase() + " rolled " + m.group(2));
        }).build();

        game.run();

        assertTrue(rolls.size() > game.getTurnCount(), "expected some bonus rolls");
        assertEquals(2 * rolls.size(), gate.calls.size());
        for (int i = 0; i < rolls.size(); i++) {
            String colour = rolls.get(i).split(" ")[0];
            assertEquals("beforeRoll " + colour, gate.calls.get(2 * i));
            assertEquals("afterRoll " + rolls.get(i), gate.calls.get(2 * i + 1));
        }
    }

    // Helpers

    // Y1 on 40 with a Red block right in front of it on 41; Y2 free on 10; Y3, Y4 at base.
    private Piece blockedYellowPosition(Game game) {
        place(game, PlayerColor.YELLOW, 1, 40);
        place(game, PlayerColor.RED, 1, 41);
        place(game, PlayerColor.RED, 2, 41);
        return place(game, PlayerColor.YELLOW, 2, 10);
    }

    private Piece place(Game game, PlayerColor color, int number, int cell) {
        Piece piece = game.playerOf(color).getPieces().get(number - 1);
        piece.placeOnStart();
        piece.setMainPathPosition(cell);
        piece.setDirection(Direction.CLOCKWISE);
        return piece;
    }

    /** Fake decider: answers piece {@code firstAnswer} first, then {@code answerAfterFirst} or the first candidate. */
    private static final class RecordingDecider implements MoveDecider {
        private final boolean triesOtherPieces;
        private final int firstAnswer;
        private Integer answerAfterFirst;
        private final List<String> calls = new ArrayList<>();
        private final List<GameSnapshot> snapshots = new ArrayList<>();

        RecordingDecider(boolean triesOtherPieces, int firstAnswer) {
            this.triesOtherPieces = triesOtherPieces;
            this.firstAnswer = firstAnswer;
        }

        @Override
        public PieceChoice choosePiece(GameSnapshot snapshot, PlayerColor color, int roll, List<Integer> candidates) {
            calls.add(color + " roll " + roll + " candidates " + candidates);
            snapshots.add(snapshot);
            if (calls.size() == 1)
                return PieceChoice.of(firstAnswer);
            if (answerAfterFirst != null)
                return PieceChoice.of(answerAfterFirst);
            return candidates.isEmpty() ? PieceChoice.none() : PieceChoice.of(candidates.get(0));
        }

        @Override
        public boolean prefersMoveFromBase(GameSnapshot snapshot, PlayerColor color) {
            return false;
        }

        @Override
        public boolean triesOtherPiecesWhenBlocked(PlayerColor color) {
            return triesOtherPieces;
        }
    }

    /** Fake gate: records the order of calls. */
    private static final class RecordingGate implements TurnGate {
        private final List<String> calls = new ArrayList<>();

        @Override
        public void beforeRoll(PlayerColor color) {
            calls.add("beforeRoll " + color);
        }

        @Override
        public void afterRoll(GameSnapshot snapshot) {
            calls.add("afterRoll " + snapshot.currentPlayer() + " rolled " + snapshot.lastRoll());
        }
    }

    /** Gate that takes extra snapshots after every roll and checks they all agree. */
    private static final class SnapshottingGate implements TurnGate {
        private Game game;
        private int turnsChecked;

        @Override
        public void beforeRoll(PlayerColor color) {
            game.snapshot();
        }

        @Override
        public void afterRoll(GameSnapshot snapshot) {
            GameSnapshot first = game.snapshot();
            GameSnapshot second = game.snapshot();
            assertEquals(first, second);
            assertEquals(snapshot, first);
            turnsChecked++;
        }
    }
}
