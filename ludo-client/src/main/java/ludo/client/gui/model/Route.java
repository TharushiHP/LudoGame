package ludo.client.gui.model;

import ludo.shared.BoardConstants;
import ludo.shared.Direction;
import ludo.shared.PathMath;
import ludo.shared.PieceLocation;
import ludo.shared.PlayerColor;
import ludo.shared.snapshot.PieceSnapshot;

import java.util.ArrayList;
import java.util.List;


public record Route(Kind kind, List<Place> places) {

    /** How the move is shown. */
    public enum Kind {
        /** Cell by cell along the path (and on into the home straight and Home). */
        WALK,
        /** Out of the base onto its X cell: pops in. */
        ENTER,
        /** A jump the path cannot explain (mystery cell, Alpha/Beta/Gamma): flash. */
        TELEPORT,
        /** Sent back to the base: fades out where it was, appears on its base arm. */
        CAPTURED
    }

    /** The longest walk shown cell by cell: a 6, doubled by the energised effect. */
    static final int MAX_WALK = 2 * 6;

    public Route {
        places = List.copyOf(places);
    }

    public Place from() {
        return places.get(0);
    }

    public Place to() {
        return places.get(places.size() - 1);
    }

    /** Steps in the route (0 for a route that does not move). */
    public int steps() {
        return places.size() - 1;
    }

    public static Route plan(PieceSnapshot before, PieceSnapshot after) {
        Place from = Place.of(before);
        Place to = Place.of(after);
        if (to.location() == PieceLocation.BASE)
            return new Route(Kind.CAPTURED, List.of(from, to));
        if (from.location() == PieceLocation.BASE)
            return to.location() == PieceLocation.MAIN_PATH && to.position() == PathMath.startCell(after.color())
                    ? new Route(Kind.ENTER, List.of(from, to))
                    : new Route(Kind.TELEPORT, List.of(from, to));
        List<Place> walk = walk(after.color(), from, to, before.direction());
        if (walk == null)
            walk = walk(after.color(), from, to, before.direction().opposite());
        return walk == null ? new Route(Kind.TELEPORT, List.of(from, to)) : new Route(Kind.WALK, walk);
    }

    /** The places walked from {@code from} to {@code to} in {@code direction}, or null if no short walk gets there. */
    private static List<Place> walk(PlayerColor colour, Place from, Place to, Direction direction) {
        List<Place> places = new ArrayList<>();
        places.add(from);
        int homeIndex; // where the walk is on the home straight; -1 = still on the main path
        if (from.location() == PieceLocation.MAIN_PATH) {
            int cell = from.position();
            if (to.location() == PieceLocation.MAIN_PATH) {
                int steps = (direction == Direction.CLOCKWISE ? to.position() - cell : cell - to.position())
                        + BoardConstants.MAIN_PATH_SIZE;
                steps %= BoardConstants.MAIN_PATH_SIZE;
                if (steps == 0 || steps > MAX_WALK)
                    return null;
                for (int i = 1; i <= steps; i++)
                    places.add(Place.cell(PathMath.advance(cell, i, direction)));
                return places;
            }
            int toApproach = PathMath.stepsToApproach(cell, colour, direction);
            if (toApproach > MAX_WALK)
                return null;
            for (int i = 1; i <= toApproach; i++)
                places.add(Place.cell(PathMath.advance(cell, i, direction)));
            homeIndex = -1;
        } else if (from.location() == PieceLocation.HOME_STRAIGHT) {
            homeIndex = from.position();
        } else {
            return null; // from Home nothing moves
        }
        int last = to.location() == PieceLocation.HOME ? BoardConstants.HOME_STRAIGHT_SIZE
                : to.location() == PieceLocation.HOME_STRAIGHT ? to.position() : Integer.MIN_VALUE;
        if (last <= homeIndex)
            return null;
        for (int i = homeIndex + 1; i <= last; i++)
            places.add(i == BoardConstants.HOME_STRAIGHT_SIZE ? Place.HOME : Place.homeStraight(i));
        return places.size() - 1 > MAX_WALK ? null : places;
    }
}
