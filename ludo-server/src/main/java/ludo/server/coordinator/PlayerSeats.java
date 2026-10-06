package ludo.server.coordinator;

import ludo.shared.PlayerColor;
import ludo.shared.protocol.JoinRequest;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Who plays each colour: the JOIN each client sent (with its declared Rule 7 behaviour) and which
 * colours the server has taken over after a client stayed away too long. Used only by the game
 * thread (thread confinement), so plain EnumMap/EnumSet are enough.
 */
final class PlayerSeats {

    private final Map<PlayerColor, JoinRequest> joined = new EnumMap<>(PlayerColor.class);
    private final Set<PlayerColor> substituted = EnumSet.noneOf(PlayerColor.class);

    boolean isTaken(PlayerColor colour) {
        return joined.containsKey(colour);
    }

    int take(JoinRequest join) {
        joined.put(join.colour(), join);
        return joined.size();
    }

    /** The colours that have joined (a live view: callers must copy it before publishing). */
    Set<PlayerColor> taken() {
        return joined.keySet();
    }

    int count() {
        return joined.size();
    }

    boolean isFull() {
        return joined.size() == PlayerColor.values().length;
    }

    boolean triesOtherPiecesWhenBlocked(PlayerColor colour) {
        return joined.get(colour).triesOtherPiecesWhenBlocked();
    }

    /** Permanent: the server plays this colour until the game ends. */
    void substitute(PlayerColor colour) {
        substituted.add(colour);
    }

    boolean isSubstituted(PlayerColor colour) {
        return substituted.contains(colour);
    }

    /** Colours whose client is expected to play: joined and not substituted. */
    Set<PlayerColor> activeClients() {
        Set<PlayerColor> active = EnumSet.noneOf(PlayerColor.class);
        active.addAll(joined.keySet());
        active.removeAll(substituted);
        return active;
    }
}
