package ludo.shared.decision;

import ludo.shared.PlayerColor;
import ludo.shared.snapshot.GameSnapshot;

import java.util.List;

/**
 * Port through which the game asks a player for a decision (Dependency Inversion: the game rules
 * depend on this interface, not on where the decision is made). The console simulation backs it
 * with the snapshot-based strategies; in the client-server version a server-side adapter will
 * forward the question to the player's client and wait for its MOVE request.
 * <p>
 * It lives in ludo-shared, not ludo-core: its signature uses only shared types, and this lets
 * ludo-players (and later the client) implement it without depending on the game rules at all.
 * Pieces are identified by their number (1-4) within the player's colour.
 */
public interface MoveDecider {

    /**
     * Chooses which piece to move for this roll.
     *
     * @param candidatePieceIds pieces the player may choose from; pieces already rejected by the
     *                          Rule 7 fallback have been removed
     */
    PieceChoice choosePiece(GameSnapshot snapshot, PlayerColor color, int roll, List<Integer> candidatePieceIds);

    /** On a six with pieces at base: should a new piece come out instead of moving one on the board? */
    boolean prefersMoveFromBase(GameSnapshot snapshot, PlayerColor color);

    /** Rule 7: may the game ask again for another piece when the chosen one cannot move? (Blue: no.) */
    boolean triesOtherPiecesWhenBlocked(PlayerColor color);
}
