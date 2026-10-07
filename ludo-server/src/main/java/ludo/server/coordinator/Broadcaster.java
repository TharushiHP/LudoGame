package ludo.server.coordinator;

import ludo.server.config.NamedThreadFactory;
import ludo.server.config.ServerLog;
import ludo.shared.PlayerColor;
import ludo.shared.json.JsonWriter;
import ludo.shared.protocol.ServerEvent;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

final class Broadcaster {

    private static final String KEEP_ALIVE = ": keep-alive\n\n";

    private final CopyOnWriteArrayList<EventSink> sinks = new CopyOnWriteArrayList<>();
    private final ExecutorService writer;
    private final AtomicLong eventSeq = new AtomicLong();
    private final ServerLog log;
    private final Consumer<EventSink> onLost;
    private boolean closed; // guarded by this

    Broadcaster(String gameId, ServerLog log, Consumer<EventSink> onLost) {
        // Non-daemon: events already queued (e.g. GAME_OVER) are still written during
        // shutdown.
        this.writer = Executors.newSingleThreadExecutor(NamedThreadFactory.single("sse-writer-" + gameId, false));
        this.log = log;
        this.onLost = onLost;
    }

    /**
     * Adds a stream; false once the game is over (the caller then answers the
     * client itself).
     */
    synchronized boolean add(EventSink sink) {
        if (closed)
            return false;
        sinks.add(sink);
        return true;
    }

    void broadcast(ServerEvent event) {
        String frame = frame(eventSeq.incrementAndGet(), event);
        submit(() -> sinks.forEach(sink -> write(sink, frame)));
    }

    void sendTo(EventSink sink, ServerEvent event) {
        String frame = frame(eventSeq.incrementAndGet(), event);
        submit(() -> write(sink, frame));
    }

    void sendTo(PlayerColor colour, ServerEvent event) {
        String frame = frame(eventSeq.incrementAndGet(), event);
        submit(() -> sinks.stream().filter(s -> s.colour() == colour).forEach(sink -> write(sink, frame)));
    }

    /**
     * A comment line keeps idle connections open and reveals clients that have
     * gone.
     */
    void keepAlive() {
        submit(() -> sinks.forEach(sink -> write(sink, KEEP_ALIVE)));
    }

    /** Safe from any thread: reads the copy-on-write list. */
    boolean isConnected(PlayerColor colour) {
        return sinks.stream().anyMatch(s -> s.colour() == colour);
    }

    int size() {
        return sinks.size();
    }

    /**
     * Writes everything already queued, closes every stream and stops the writer
     * thread.
     */
    void close() throws InterruptedException {
        synchronized (this) {
            closed = true;
        }
        submit(() -> {
            List<EventSink> all = List.copyOf(sinks);
            sinks.clear();
            all.forEach(EventSink::close);
        });
        writer.shutdown();
        if (!writer.awaitTermination(5, TimeUnit.SECONDS))
            writer.shutdownNow();
    }

    static String frame(long id, ServerEvent event) {
        return "id: " + id + "\nevent: " + event.type() + "\ndata: " + JsonWriter.write(event.toJson()) + "\n\n";
    }

    private void write(EventSink sink, String frame) {
        try {
            sink.send(frame);
        } catch (IOException e) {
            if (sinks.remove(sink)) {
                sink.close();
                log.log("event stream " + sink.name() + " lost: " + e.getMessage());
                onLost.accept(sink);
            }
        }
    }

    private void submit(Runnable task) {
        try {
            writer.execute(task);
        } catch (RejectedExecutionException e) {
            // The game is over and the writer has stopped; there is nobody left to send to.
        }
    }
}
