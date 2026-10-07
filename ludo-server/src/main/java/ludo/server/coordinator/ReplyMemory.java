package ludo.server.coordinator;

import ludo.server.coordinator.state.Reply;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * The game's memory of accepted replies, by requestId (the store behind the Idempotent Receiver in
 * {@link CommandLoop}). A repeated requestId gets the stored reply again instead of being applied
 * twice.
 * <p>
 * Each reply is kept for a fixed <b>time</b> ({@link #RETENTION_NANOS}, 30 s), well above the
 * client's retry window (about 5.75 s, see the client's {@code RetryPolicy}), so a retry always
 * finds its reply however many other requests the game accepted in the meantime. A count cap
 * ({@link #MAX_ENTRIES}) is only a memory safety net; if it ever pushes out a reply younger than
 * the retention time, that is logged once so the limit is visible.
 * <p>
 * Thread confinement: only the game thread uses it, so a plain {@link LinkedHashMap} without
 * locks is enough. Insertion order is age order, so expired entries are removed from the oldest
 * end on every call; no extra thread is needed. The clock is injected so tests need not sleep.
 */
final class ReplyMemory {

    static final long RETENTION_NANOS = TimeUnit.SECONDS.toNanos(30);
    static final int MAX_ENTRIES = 50_000;
    static final String FULL_WARNING = "idempotency memory full: dropping replies younger than the retention window";

    private record Entry(Reply reply, long storedAt) {}

    private final Map<String, Entry> replies = new LinkedHashMap<>();
    private final long retentionNanos;
    private final int maxEntries;
    private final LongSupplier clock;
    private final Consumer<String> warn;
    private boolean warned;

    /** The real one: 30 s, 50,000 entries, {@code System::nanoTime}. */
    ReplyMemory(Consumer<String> warn) {
        this(RETENTION_NANOS, MAX_ENTRIES, System::nanoTime, warn);
    }

    ReplyMemory(long retentionNanos, int maxEntries, LongSupplier clock, Consumer<String> warn) {
        this.retentionNanos = retentionNanos;
        this.maxEntries = maxEntries;
        this.clock = clock;
        this.warn = warn;
    }

    /** The stored reply for this requestId, or null if there is none (any more). */
    Reply get(String requestId) {
        expire(clock.getAsLong());
        Entry entry = replies.get(requestId);
        return entry == null ? null : entry.reply();
    }

    /** Remembers an accepted reply. */
    void remember(String requestId, Reply reply) {
        long now = clock.getAsLong();
        expire(now);
        replies.put(requestId, new Entry(reply, now));
        if (replies.size() > maxEntries) {
            Iterator<Entry> oldest = replies.values().iterator();
            Entry dropped = oldest.next();
            oldest.remove();
            if (!warned && now - dropped.storedAt() < retentionNanos) {
                warned = true;
                warn.accept(FULL_WARNING);
            }
        }
    }

    int size() {
        return replies.size();
    }

    /** Removes every entry older than the retention time; they are at the start of the map. */
    private void expire(long now) {
        Iterator<Entry> oldest = replies.values().iterator();
        while (oldest.hasNext() && now - oldest.next().storedAt() >= retentionNanos)
            oldest.remove();
    }
}
