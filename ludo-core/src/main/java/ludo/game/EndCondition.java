package ludo.game;

/**
 * When a game ends (Rule 11: the first player to bring all four pieces Home wins; the game
 * <i>may</i> continue for 2nd to 4th place). Chosen through {@link GameBuilder#withEndCondition}.
 */
public enum EndCondition {
    /** Play on until only one player is left; every player gets a place (the A1 behaviour). */
    ALL_PLACES,
    /** Stop as soon as one player has all four pieces Home; only that player is placed. */
    FIRST_WINNER
}
