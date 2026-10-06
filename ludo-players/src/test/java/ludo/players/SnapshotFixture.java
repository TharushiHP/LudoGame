package ludo.players;

import ludo.shared.Direction;
import ludo.shared.EffectKind;
import ludo.shared.PieceLocation;
import ludo.shared.PlayerColor;
import ludo.shared.snapshot.GameSnapshot;
import ludo.shared.snapshot.GameStatus;
import ludo.shared.snapshot.MysterySnapshot;
import ludo.shared.snapshot.PieceSnapshot;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Test helper that builds a {@link GameSnapshot} position by position (Test Data Builder).
 * Every piece starts at base, clockwise, with no captures; tests only describe what differs.
 */
final class SnapshotFixture {

    private static final List<PlayerColor> SNAPSHOT_ORDER =
            List.of(PlayerColor.YELLOW, PlayerColor.BLUE, PlayerColor.RED, PlayerColor.GREEN);

    private final Map<String, PieceSnapshot> pieces = new LinkedHashMap<>();
    private final Map<PlayerColor, Integer> memo = new EnumMap<>(PlayerColor.class);
    private MysterySnapshot mystery = new MysterySnapshot(-1, 0);

    SnapshotFixture() {
        for (PlayerColor color : SNAPSHOT_ORDER) {
            for (int number = 1; number <= 4; number++) {
                put(new PieceSnapshot(color, number, PieceLocation.BASE, -1, Direction.CLOCKWISE,
                        0, EffectKind.NONE, 0, false));
            }
        }
    }

    SnapshotFixture onMainPath(PlayerColor color, int number, int cell) {
        return onMainPath(color, number, cell, Direction.CLOCKWISE);
    }

    SnapshotFixture onMainPath(PlayerColor color, int number, int cell, Direction direction) {
        PieceSnapshot old = get(color, number);
        put(new PieceSnapshot(color, number, PieceLocation.MAIN_PATH, cell, direction,
                old.captureCount(), EffectKind.NONE, 0, false));
        return this;
    }

    SnapshotFixture onHomeStraight(PlayerColor color, int number, int index) {
        PieceSnapshot old = get(color, number);
        put(new PieceSnapshot(color, number, PieceLocation.HOME_STRAIGHT, index, old.direction(),
                old.captureCount(), EffectKind.NONE, 0, false));
        return this;
    }

    SnapshotFixture home(PlayerColor color, int number) {
        PieceSnapshot old = get(color, number);
        put(new PieceSnapshot(color, number, PieceLocation.HOME, -1, old.direction(),
                old.captureCount(), EffectKind.NONE, 0, false));
        return this;
    }

    SnapshotFixture withCaptures(PlayerColor color, int number, int captures) {
        PieceSnapshot old = get(color, number);
        put(new PieceSnapshot(color, number, old.location(), old.position(), old.direction(),
                captures, old.effect(), old.effectRoundsLeft(), old.inBlock()));
        return this;
    }

    SnapshotFixture mysteryAt(int cell) {
        mystery = new MysterySnapshot(cell, 4);
        return this;
    }

    SnapshotFixture memo(PlayerColor color, int value) {
        memo.put(color, value);
        return this;
    }

    GameSnapshot build() {
        return new GameSnapshot(1, 0, null, 0, mystery, new EnumMap<>(PlayerColor.class),
                GameStatus.IN_PROGRESS, new ArrayList<>(pieces.values()), memo);
    }

    private PieceSnapshot get(PlayerColor color, int number) {
        return pieces.get(color + "-" + number);
    }

    private void put(PieceSnapshot piece) {
        pieces.put(piece.color() + "-" + piece.number(), piece);
    }
}
