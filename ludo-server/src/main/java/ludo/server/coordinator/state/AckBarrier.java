package ludo.server.coordinator.state;

import ludo.shared.PlayerColor;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

public final class AckBarrier {

    static final int MAX_RESENDS = 3;

    private final long version;
    private final String hash;
    private final EnumSet<PlayerColor> pending = EnumSet.noneOf(PlayerColor.class);
    private final Map<PlayerColor, Integer> resends = new EnumMap<>(PlayerColor.class);

    public AckBarrier(long version, String hash, Set<PlayerColor> expected) {
        this.version = version;
        this.hash = hash;
        this.pending.addAll(expected);
    }

    public long version() {
        return version;
    }

    public String hash() {
        return hash;
    }

    public Set<PlayerColor> pending() {
        return EnumSet.copyOf(pending);
    }

    void acked(PlayerColor colour) {
        pending.remove(colour);
    }

    /**
     * Counts a re-sent STATE; true while that colour may still be sent it again.
     */
    boolean mayResend(PlayerColor colour) {
        return resends.merge(colour, 1, Integer::sum) <= MAX_RESENDS;
    }

    /**
     * Removes colours that are no longer connected (they rejoin on reconnect, with
     * a fresh STATE)
     * and returns the removed ones so the caller can log them.
     */
    public Set<PlayerColor> dropDisconnected(Predicate<PlayerColor> connected) {
        Set<PlayerColor> dropped = EnumSet.noneOf(PlayerColor.class);
        for (PlayerColor colour : pending)
            if (!connected.test(colour))
                dropped.add(colour);
        pending.removeAll(dropped);
        return dropped;
    }

    public void drop(PlayerColor colour) {
        pending.remove(colour);
    }

    public boolean isComplete() {
        return pending.isEmpty();
    }
}
