package ludo.shared.snapshot;

import ludo.shared.PlayerColor;

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
 * @param deciderMemo     per-colour number each player's decider asked the server to keep
 *                        (see {@code PieceChoice.memo}); only Blue has an entry, for its cycle position
 */
public record GameSnapshot(int roundNumber,
                           int turnCount,
                           PlayerColor currentPlayer,
                           int lastRoll,
                           MysterySnapshot mystery,
                           Map<PlayerColor, Integer> finishPositions,
                           GameStatus status,
                           List<PieceSnapshot> pieces,
                           Map<PlayerColor, Integer> deciderMemo) {

    /**
     * Defensive copies keep the record immutable even if the caller's collections change later.
     * The maps are EnumMaps, so they always iterate Red, Green, Yellow, Blue: state hashes are
     * then identical on every machine (Map.copyOf would iterate in a random order).
     */
    public GameSnapshot {
        finishPositions = orderedCopy(finishPositions);
        pieces = List.copyOf(pieces);
        deciderMemo = orderedCopy(deciderMemo);
    }

    public List<PieceSnapshot> piecesOf(PlayerColor color) {
        return pieces.stream().filter(p -> p.color() == color).toList();
    }

    private static Map<PlayerColor, Integer> orderedCopy(Map<PlayerColor, Integer> source) {
        Map<PlayerColor, Integer> copy = new EnumMap<>(PlayerColor.class);
        copy.putAll(source);
        return Collections.unmodifiableMap(copy);
    }
}
