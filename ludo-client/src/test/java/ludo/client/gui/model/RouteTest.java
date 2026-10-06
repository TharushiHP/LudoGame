package ludo.client.gui.model;

import ludo.shared.Direction;
import ludo.shared.EffectKind;
import ludo.shared.PieceLocation;
import ludo.shared.PlayerColor;
import ludo.shared.snapshot.GameSnapshot;
import ludo.shared.snapshot.GameStatus;
import ludo.shared.snapshot.MysterySnapshot;
import ludo.shared.snapshot.PieceSnapshot;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Which tokens moved between two snapshots, and the route the spectator window animates. */
class RouteTest {

    private static PieceSnapshot piece(PlayerColor colour, int number, PieceLocation where, int position, Direction direction) {
        return new PieceSnapshot(colour, number, where, position, direction, 0, EffectKind.NONE, 0, false);
    }

    private static PieceSnapshot onCell(PlayerColor colour, int cell, Direction direction) {
        return piece(colour, 1, PieceLocation.MAIN_PATH, cell, direction);
    }

    private static List<Place> cells(int... cells) {
        List<Place> places = new ArrayList<>();
        for (int c : cells)
            places.add(Place.cell(c));
        return places;
    }

    @Test
    void walksClockwiseAcross51To0() {
        Route route = Route.plan(onCell(PlayerColor.BLUE, 50, Direction.CLOCKWISE), onCell(PlayerColor.BLUE, 2, Direction.CLOCKWISE));
        assertEquals(Route.Kind.WALK, route.kind());
        assertEquals(cells(50, 51, 0, 1, 2), route.places());
    }

    @Test
    void walksCounterclockwiseAcross0To51() {
        Route route = Route.plan(onCell(PlayerColor.RED, 1, Direction.COUNTER_CLOCKWISE), onCell(PlayerColor.RED, 49, Direction.COUNTER_CLOCKWISE));
        assertEquals(Route.Kind.WALK, route.kind());
        assertEquals(cells(1, 0, 51, 50, 49), route.places());
    }

    @Test
    void walksPastTheApproachCellIntoTheHomeStraight() {
        // Yellow's approach cell is 50 (BoardConstants); 48 -> 49 -> 50 -> home straight 0 -> 1.
        Route route = Route.plan(onCell(PlayerColor.YELLOW, 48, Direction.CLOCKWISE),
                piece(PlayerColor.YELLOW, 1, PieceLocation.HOME_STRAIGHT, 1, Direction.CLOCKWISE));
        assertEquals(Route.Kind.WALK, route.kind());
        assertEquals(List.of(Place.cell(48), Place.cell(49), Place.cell(50), Place.homeStraight(0), Place.homeStraight(1)),
                route.places());
    }

    @Test
    void walksAlongTheHomeStraightIntoHome() {
        Route route = Route.plan(piece(PlayerColor.GREEN, 2, PieceLocation.HOME_STRAIGHT, 3, Direction.CLOCKWISE),
                piece(PlayerColor.GREEN, 2, PieceLocation.HOME, -1, Direction.CLOCKWISE));
        assertEquals(Route.Kind.WALK, route.kind());
        assertEquals(List.of(Place.homeStraight(3), Place.homeStraight(4), Place.HOME), route.places());
    }

    @Test
    void leavingTheBaseOntoTheXCellIsAnEntry() {
        Route route = Route.plan(piece(PlayerColor.BLUE, 3, PieceLocation.BASE, -1, Direction.CLOCKWISE),
                piece(PlayerColor.BLUE, 3, PieceLocation.MAIN_PATH, 13, Direction.COUNTER_CLOCKWISE));
        assertEquals(Route.Kind.ENTER, route.kind());
        assertEquals(List.of(Place.BASE, Place.cell(13)), route.places());
    }

    @Test
    void sentBackToTheBaseIsACapture() {
        Route route = Route.plan(onCell(PlayerColor.RED, 30, Direction.CLOCKWISE),
                piece(PlayerColor.RED, 1, PieceLocation.BASE, -1, Direction.CLOCKWISE));
        assertEquals(Route.Kind.CAPTURED, route.kind());
        assertEquals(List.of(Place.cell(30), Place.BASE), route.places());
    }

    @Test
    void aJumpThePathCannotExplainIsATeleport() {
        // Mystery cell at 30 sends the piece to Alpha (7): far more than a doubled six either way.
        Route route = Route.plan(onCell(PlayerColor.GREEN, 26, Direction.CLOCKWISE), onCell(PlayerColor.GREEN, 7, Direction.CLOCKWISE));
        assertEquals(Route.Kind.TELEPORT, route.kind());
        assertEquals(List.of(Place.cell(26), Place.cell(7)), route.places());
    }

    @Test
    void anEnergisedTwelveStillWalksButThirteenTeleports() {
        assertEquals(Route.Kind.WALK, Route.plan(onCell(PlayerColor.RED, 0, Direction.CLOCKWISE), onCell(PlayerColor.RED, 12, Direction.CLOCKWISE)).kind());
        assertEquals(12, Route.plan(onCell(PlayerColor.RED, 0, Direction.CLOCKWISE), onCell(PlayerColor.RED, 12, Direction.CLOCKWISE)).steps());
        assertEquals(Route.Kind.TELEPORT, Route.plan(onCell(PlayerColor.RED, 0, Direction.CLOCKWISE), onCell(PlayerColor.RED, 13, Direction.CLOCKWISE)).kind());
    }

    @Test
    void aTurnedRoundPieceWalksTheOtherWay() {
        // Gamma turned it counterclockwise; the old snapshot still says clockwise.
        Route route = Route.plan(onCell(PlayerColor.BLUE, 44, Direction.CLOCKWISE), onCell(PlayerColor.BLUE, 41, Direction.COUNTER_CLOCKWISE));
        assertEquals(Route.Kind.WALK, route.kind());
        assertEquals(cells(44, 43, 42, 41), route.places());
    }

    @Test
    void onlyTokensOnAnotherPlaceAreChanges() {
        PieceSnapshot r1 = onCell(PlayerColor.RED, 30, Direction.CLOCKWISE);
        PieceSnapshot b1 = piece(PlayerColor.BLUE, 1, PieceLocation.MAIN_PATH, 10, Direction.CLOCKWISE);
        PieceSnapshot r1Moved = onCell(PlayerColor.RED, 34, Direction.CLOCKWISE);
        PieceSnapshot b1Sick = new PieceSnapshot(PlayerColor.BLUE, 1, PieceLocation.MAIN_PATH, 10, Direction.CLOCKWISE, 0,
                EffectKind.SICK, 4, false); // same place, new effect: not a move
        List<PieceChange> changes = PieceChange.between(snapshot(r1, b1), snapshot(r1Moved, b1Sick));
        assertEquals(1, changes.size());
        assertEquals(PlayerColor.RED, changes.get(0).colour());
        assertEquals(1, changes.get(0).number());
        assertEquals(r1, changes.get(0).before());
        assertEquals(r1Moved, changes.get(0).after());
        assertTrue(PieceChange.between(null, snapshot(r1)).isEmpty(), "no previous state: nothing to animate");
    }

    private static GameSnapshot snapshot(PieceSnapshot... pieces) {
        return new GameSnapshot(1, 1, PlayerColor.RED, 4, new MysterySnapshot(-1, 0), Map.of(), GameStatus.IN_PROGRESS,
                List.of(pieces), Map.of());
    }
}
