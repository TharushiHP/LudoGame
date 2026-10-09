package ludo.client.gui;

import ludo.client.gui.model.PieceChange;
import ludo.client.gui.model.Place;
import ludo.client.gui.model.Route;
import ludo.shared.PlayerColor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;


final class Animator {

    /** Longest time per walked cell; long walks go faster so a move fits the 500 ms pacing. */
    static final double MAX_STEP_MS = 70;
    static final double WALK_BUDGET_MS = 300;
    static final long ENTER_MS = 200;
    static final long TELEPORT_MS = 260;
    static final long CAPTURE_MS = 320;

    /**
     * How to draw a token now: between places {@code from} and {@code to} at fraction {@code t},
     * with opacity, size factor and a flash ring strength (0 = none).
     */
    record Pose(Place from, Place to, double t, double alpha, double scale, double flash) {
    }

    private record Track(Route route, long start, double stepMs, long duration) {
        long end() {
            return start + duration;
        }
    }

    private final Map<String, Track> tracks = new HashMap<>();

    /** Drops every running animation: all tokens are drawn where the snapshot says. */
    void snap() {
        tracks.clear();
    }

    /** Starts the animations for these changes, {@code delayMs} from {@code now} (the dice tumble first). */
    void start(List<PieceChange> changes, long now, long delayMs) {
        long begin = now + delayMs;
        long movesEnd = begin;
        for (PieceChange change : changes) {
            Route route = Route.plan(change.before(), change.after());
            if (route.kind() == Route.Kind.CAPTURED)
                continue;
            Track track = switch (route.kind()) {
                case WALK -> {
                    double step = Math.min(MAX_STEP_MS, WALK_BUDGET_MS / route.steps());
                    yield new Track(route, begin, step, Math.round(step * route.steps()));
                }
                case ENTER -> new Track(route, begin, ENTER_MS, ENTER_MS);
                default -> new Track(route, begin, TELEPORT_MS, TELEPORT_MS);
            };
            tracks.put(key(change.colour(), change.number()), track);
            movesEnd = Math.max(movesEnd, track.end());
        }
        // A captured token goes home only once the capturer has arrived.
        for (PieceChange change : changes) {
            Route route = Route.plan(change.before(), change.after());
            if (route.kind() == Route.Kind.CAPTURED)
                tracks.put(key(change.colour(), change.number()), new Track(route, movesEnd, CAPTURE_MS, CAPTURE_MS));
        }
    }

    /** True while any token is still moving. */
    boolean busy(long now) {
        return tracks.values().stream().anyMatch(t -> now < t.end());
    }

    /** The pose of a moving token, or empty when it simply stands where the snapshot says. */
    Optional<Pose> pose(PlayerColor colour, int number, long now) {
        Track track = tracks.get(key(colour, number));
        if (track == null || now >= track.end())
            return Optional.empty();
        Route route = track.route();
        Place from = route.from(), to = route.to();
        if (now < track.start())
            return Optional.of(new Pose(from, from, 0, 1, 1, 0)); // still waiting for the dice
        double elapsed = now - track.start();
        double t = Math.min(1, elapsed / track.duration());
        return Optional.of(switch (route.kind()) {
            case WALK -> {
                double k = elapsed / track.stepMs();
                int i = (int) Math.min(Math.floor(k), route.steps() - 1);
                double f = Math.min(1, k - i);
                yield new Pose(route.places().get(i), route.places().get(i + 1), ease(f), 1,
                        1 + 0.18 * Math.sin(Math.PI * f), 0);
            }
            case ENTER -> new Pose(from, to, ease(t), 1, 1 + 0.25 * Math.sin(Math.PI * t), 0);
            case TELEPORT -> t < 0.5
                    ? new Pose(from, from, 0, 1 - 2 * t, 1 - 0.5 * t, 0)
                    : new Pose(to, to, 0, 2 * t - 1, 1.4 - 0.4 * (2 * t - 1), 2 - 2 * t);
            case CAPTURED -> t < 0.5
                    ? new Pose(from, from, 0, 1 - 2 * t, 1 + 0.3 * t, 1 - 2 * t)
                    : new Pose(to, to, 0, 2 * t - 1, 0.6 + 0.4 * (2 * t - 1), 0);
        });
    }

    private static double ease(double f) {
        return f * f * (3 - 2 * f);
    }

    private static String key(PlayerColor colour, int number) {
        return colour.name() + number;
    }
}
