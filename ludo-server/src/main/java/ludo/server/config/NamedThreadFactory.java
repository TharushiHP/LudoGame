package ludo.server.config;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Creates threads with a readable name and an explicit daemon flag, so the server log and a
 * thread dump show exactly which pool did what (see docs/THREADS.md for every thread and why it
 * is daemon or not).
 */
public final class NamedThreadFactory implements ThreadFactory {

    private final String prefix;
    private final boolean daemon;
    private final boolean numbered;
    private final AtomicInteger count = new AtomicInteger();

    private NamedThreadFactory(String prefix, boolean daemon, boolean numbered) {
        this.prefix = prefix;
        this.daemon = daemon;
        this.numbered = numbered;
    }

    /** Threads named prefix-1, prefix-2, ... */
    public static NamedThreadFactory numbered(String prefix, boolean daemon) {
        return new NamedThreadFactory(prefix, daemon, true);
    }

    /** Every thread gets exactly this name (for single-thread executors). */
    public static NamedThreadFactory single(String name, boolean daemon) {
        return new NamedThreadFactory(name, daemon, false);
    }

    @Override
    public Thread newThread(Runnable task) {
        String name = numbered ? prefix + "-" + count.incrementAndGet() : prefix;
        Thread thread = new Thread(task, name);
        thread.setDaemon(daemon);
        return thread;
    }
}
