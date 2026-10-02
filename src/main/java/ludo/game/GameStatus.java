package ludo.game;

/**
 * Lifecycle of one game, carried in every {@link GameSnapshot}.
 * The three end states match the three ways a game can finish (Rule 11, stalemate, safety cap).
 */
public enum GameStatus {
    NOT_STARTED,
    IN_PROGRESS,
    FINISHED,
    STALEMATE,
    ROUND_CAP_REACHED,
    ABORTED
}
