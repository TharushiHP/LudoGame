package ludo.shared;

/**
 * Fixed numbers of the LUDO-T board and rules (cell numbers, sizes, dice and timing constants).
 * Shared so the server rules and the clients agree on one definition (Single Source of Truth).
 */
public final class BoardConstants {

    public static final int MAIN_PATH_SIZE = 52;
    public static final int HOME_STRAIGHT_SIZE = 5;
    public static final int PIECES_PER_PLAYER = 4;
    public static final int MAX_PLAYERS = 4;

    // Starting squares (X positions) on the main path, clockwise from Yellow X
    public static final int YELLOW_START = 0;
    public static final int BLUE_START = 13;
    public static final int RED_START = 26;
    public static final int GREEN_START = 39;

    // Approach cells (colored circles): 50 steps clockwise from each X
    public static final int YELLOW_APPROACH = 50;
    public static final int BLUE_APPROACH = 11;
    public static final int RED_APPROACH = 24;
    public static final int GREEN_APPROACH = 37;

    // Special named cells (9th, 27th, 46th from Yellow approach cell)
    public static final int ALPHA = 7;
    public static final int BETA = 25;
    public static final int GAMMA = 44;

    // Dice rules
    public static final int MOVE_FROM_BASE_ROLL = 6;
    public static final int MAX_CONSECUTIVE_SIXES = 3;

    // Mystery cell timing
    public static final int MYSTERY_SPAWN_AFTER_ROUNDS = 2;
    public static final int MYSTERY_DURATION_ROUNDS = 4;

    // Effects duration (in rounds)
    public static final int EFFECT_DURATION_ROUNDS = 4;

    private BoardConstants() {}
}
