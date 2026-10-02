package ludo.game;

import ludo.board.PlayerColor;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable picture of the whole game at one moment (Value Object / Memento-style state copy,
 * Java record). It holds no references to live game objects, so it can be handed to other
 * threads, deciders, gates and later the network without any risk of changing the game.
 * Producing one never changes game state.
 *
 * @param currentPlayer   colour whose turn is in progress or last played; null before the first turn
 * @param lastRoll        last dice value rolled during a turn; 0 before the first turn
 * @param finishPositions finishing place per colour, 0 while that player is still playing
 * @param pieces          every piece, in the order Yellow, Blue, Red, Green and piece number 1-4
 */
public record GameSnapshot(int roundNumber,
                           int turnCount,
                           PlayerColor currentPlayer,
                           int lastRoll,
                           MysterySnapshot mystery,
                           Map<PlayerColor, Integer> finishPositions,
                           GameStatus status,
                           List<PieceSnapshot> pieces) {

    /**
     * Defensive copies keep the record immutable even if the caller's collections change later.
     * An EnumMap keeps a fixed iteration order (needed later for state hashing); Map.copyOf would not.
     */
    public GameSnapshot {
        finishPositions = Collections.unmodifiableMap(new EnumMap<>(finishPositions));
        pieces = List.copyOf(pieces);
    }

    public List<PieceSnapshot> piecesOf(PlayerColor color) {
        return pieces.stream().filter(p -> p.color() == color).toList();
    }
}
