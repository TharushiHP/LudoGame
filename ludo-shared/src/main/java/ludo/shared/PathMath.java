package ludo.shared;

/**
 * Pure path arithmetic for the LUDO-T board: start and approach cells per colour, moving along
 * the 52-cell main path in either direction, and the home straight. Stateless static functions
 * with no side effects, so the server's rules (Board, Piece) and the clients' strategies share
 * one definition of "where does this move land" (DRY; Single Source of Truth).
 */
public final class PathMath {

    private PathMath() {}

    public static int startCell(PlayerColor color) {
        switch (color) {
            case YELLOW: return BoardConstants.YELLOW_START;
            case BLUE:   return BoardConstants.BLUE_START;
            case RED:    return BoardConstants.RED_START;
            case GREEN:  return BoardConstants.GREEN_START;
            default:     throw new IllegalStateException("Unknown color: " + color);
        }
    }

    public static int approachCell(PlayerColor color) {
        switch (color) {
            case YELLOW: return BoardConstants.YELLOW_APPROACH;
            case BLUE:   return BoardConstants.BLUE_APPROACH;
            case RED:    return BoardConstants.RED_APPROACH;
            case GREEN:  return BoardConstants.GREEN_APPROACH;
            default:     throw new IllegalStateException("Unknown color: " + color);
        }
    }

    /** The main-path cell reached by moving {@code steps} cells from {@code cell} in {@code direction}. */
    public static int advance(int cell, int steps, Direction direction) {
        if (direction == Direction.CLOCKWISE) {
            return (cell + steps) % BoardConstants.MAIN_PATH_SIZE;
        }
        return (cell - steps + BoardConstants.MAIN_PATH_SIZE) % BoardConstants.MAIN_PATH_SIZE;
    }

    /** Cells from {@code cell} to the colour's approach cell when moving in {@code direction} (0 when on it). */
    public static int stepsToApproach(int cell, PlayerColor color, Direction direction) {
        int approach = approachCell(color);
        if (direction == Direction.CLOCKWISE) {
            return (approach - cell + BoardConstants.MAIN_PATH_SIZE) % BoardConstants.MAIN_PATH_SIZE;
        }
        return (cell - approach + BoardConstants.MAIN_PATH_SIZE) % BoardConstants.MAIN_PATH_SIZE;
    }

    /**
     * Home-straight index after moving {@code steps} cells from {@code homeIndex}
     * (-1 means "on the approach cell"). A result of {@link BoardConstants#HOME_STRAIGHT_SIZE}
     * is Home; anything larger overshoots Home (Rule 10: exact roll needed).
     */
    public static int homeStraightIndexAfter(int homeIndex, int steps) {
        return homeIndex + steps;
    }
}
