package ludo.shared.protocol;

/**
 * Kinds of event the server pushes to clients over the event stream. The name is sent as the
 * stream's "event:" field and the matching record ({@link ServerEvent}) as its data.
 */
public enum EventType {
    /** Full game state after a change: version, snapshot, hash and the log lines since the last STATE. */
    STATE,
    /** The named colour must send ROLL. */
    ROLL_REQUEST,
    /** The named colour must send DECISION for this decisionId. */
    DECISION_REQUEST,
    /** The game is waiting for a client that did not answer in time. */
    PAUSED,
    /** The game continues (the client answered, or the server now plays that colour). */
    RESUMED,
    /** The game has ended; no more events follow. */
    GAME_OVER
}
