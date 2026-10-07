package ludo.server.coordinator;

import ludo.server.coordinator.state.Reply;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/** The idempotency memory, with a fake clock: time-based retention and the count cap. */
class ReplyMemoryTest {

    private final AtomicLong now = new AtomicLong(1_000);
    private final List<String> warnings = new ArrayList<>();

    private ReplyMemory memory(int maxEntries) {
        return new ReplyMemory(ReplyMemory.RETENTION_NANOS, maxEntries, now::get, warnings::add);
    }

    private static Reply ok(int n) {
        return Reply.okWith("acked", true, "version", n);
    }

    private void advanceSeconds(long seconds) {
        now.addAndGet(TimeUnit.SECONDS.toNanos(seconds));
    }

    @Test
    void aReplyIsStillThereAfterManyMoreThan1000OtherAcceptedRequestsWithinTheWindow() {
        ReplyMemory memory = new ReplyMemory(ReplyMemory.RETENTION_NANOS, ReplyMemory.MAX_ENTRIES, now::get, warnings::add);
        memory.remember("first", ok(1));
        for (int i = 0; i < 5_000; i++) { // what the overload test produced within 5 s
            now.addAndGet(TimeUnit.MILLISECONDS.toNanos(1));
            memory.remember("other-" + i, ok(i));
        }

        assertEquals(ok(1), memory.get("first"), "a retry 5 s later gets the stored reply");
        assertTrue(warnings.isEmpty());
    }

    @Test
    void aReplyExpiresAfterTheRetentionWindow() {
        ReplyMemory memory = memory(ReplyMemory.MAX_ENTRIES);
        memory.remember("old", ok(1));
        advanceSeconds(29);
        assertEquals(ok(1), memory.get("old"), "still kept just inside the window");
        memory.remember("newer", ok(2));

        advanceSeconds(1);

        assertNull(memory.get("old"), "30 s old: removed");
        assertEquals(ok(2), memory.get("newer"));
        assertEquals(1, memory.size(), "expired entries are removed, not only hidden");
    }

    @Test
    void theCapBoundsMemoryAndWarnsOnceWhenItDropsAYoungReply() {
        ReplyMemory memory = memory(3);
        for (int i = 0; i < 10; i++)
            memory.remember("r" + i, ok(i));

        assertEquals(3, memory.size(), "never more than the cap");
        assertNull(memory.get("r0"), "the oldest went first");
        assertEquals(ok(9), memory.get("r9"));
        assertEquals(List.of(ReplyMemory.FULL_WARNING), warnings, "logged once, not for every drop");
    }

    @Test
    void theCapDoesNotWarnWhenOnlyExpiredRepliesWouldHaveGone() {
        ReplyMemory memory = memory(3);
        for (int i = 0; i < 3; i++)
            memory.remember("r" + i, ok(i));
        advanceSeconds(31);

        memory.remember("fresh", ok(4)); // the three old ones expire first

        assertEquals(1, memory.size());
        assertTrue(warnings.isEmpty());
    }
}
