package ludo.shared.protocol;

/**
 * The two questions the game asks a player, matching the two {@code MoveDecider} decisions:
 * which piece to move, and whether to bring a new piece out of base on a six.
 */
public enum DecisionKind {
    CHOOSE_PIECE,
    MOVE_FROM_BASE
}
