package ludo.board;

/**
 * Immutable value object: where a move would end (main-path cell, home-straight index,
 * Home, or overshoot). Created through static factory methods.
 * Lives in the board package (moved from ludo.game) so that board no longer depends on game,
 * which removes a package cycle (Acyclic Dependencies Principle).
 */
public class MoveTarget {

    public enum Type {
        MAIN_PATH,
        HOME_STRAIGHT,
        HOME,
        OVERSHOOT
    }

    private final Type type;
    private final int position;

    private MoveTarget(Type type, int position) {
        this.type = type;
        this.position = position;
    }

    public static MoveTarget mainPath(int cell) {
        return new MoveTarget(Type.MAIN_PATH, cell);
    }

    public static MoveTarget homeStraight(int index) {
        return new MoveTarget(Type.HOME_STRAIGHT, index);
    }

    public static MoveTarget home() {
        return new MoveTarget(Type.HOME, -1);
    }

    public static MoveTarget overshoot() {
        return new MoveTarget(Type.OVERSHOOT, -1);
    }

    public boolean isMainPath()     { return type == Type.MAIN_PATH; }
    public boolean isHomeStraight() { return type == Type.HOME_STRAIGHT; }
    public boolean isHome()         { return type == Type.HOME; }
    public boolean isOvershoot()    { return type == Type.OVERSHOOT; }

    public int getPosition() { return position; }

    @Override
    public String toString() {
        return type + ":" + position;
    }
}
