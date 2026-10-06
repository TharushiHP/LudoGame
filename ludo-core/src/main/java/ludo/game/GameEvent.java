package ludo.game;

/**
 * The kinds of event the {@link Game} publishes to its {@link GameEventListener}s
 * (Observer pattern). Each published message carries the type that describes it.
 */
public enum GameEvent {
    GAME_START,
    FIRST_PLAYER_CHOSEN,
    ROUND_START,
    DICE_ROLLED,
    TURN_SKIPPED,
    PIECE_MOVED_TO_START,
    PIECE_MOVED,
    PIECE_BLOCKED,
    PIECE_CANNOT_MOVE,
    BLOCK_FORMED,
    PIECE_CAPTURED,
    PIECE_REACHED_HOME,
    MYSTERY_CELL_SPAWNED,
    MYSTERY_CELL_TELEPORT,
    PIECE_ENERGIZED,
    PIECE_SICK,
    PIECE_BRIEFING,
    PIECE_BRIEFING_TELEPORT,
    PIECE_DIRECTION_CHANGED,
    ROUND_STATUS,
    PLAYER_WINS,
    LAST_PLAYER_RANKED,
    STALEMATE,
    GAME_OVER
}
