package ludo.game;

import ludo.board.PlayerColor;

import java.util.List;
import java.util.OptionalInt;

/**
 * Port through which {@link Game} asks a player for a decision (Dependency Inversion: the game
 * rules depend on this interface, not on where the decision is made). Locally it is backed by
 * the player strategies ({@link LocalStrategyDecider}); in the client-server version a server-side
 * adapter will forward the question to the player's client and wait for its MOVE request.
 * <p>
 * Pieces are identified by their number (1-4) within the player's colour.
 */
public interface MoveDecider {

    /**
     * Chooses which piece to move for this roll.
     *
     * @param candidatePieceIds pieces the player may choose from; pieces already rejected by the
     *                          Rule 7 fallback have been removed
     * @return the chosen piece number, or empty when the player has nothing to choose
     */
    OptionalInt choosePiece(GameSnapshot snapshot, PlayerColor color, int roll, List<Integer> candidatePieceIds);

    /** On a six with pieces at base: should a new piece come out instead of moving one on the board? */
    boolean prefersMoveFromBase(GameSnapshot snapshot, PlayerColor color);

    /** Rule 7: may the game ask again for another piece when the chosen one cannot move? (Blue: no.) */
    boolean triesOtherPiecesWhenBlocked(PlayerColor color);
}
