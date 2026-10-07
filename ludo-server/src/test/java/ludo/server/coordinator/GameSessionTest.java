package ludo.server.coordinator;

import ludo.server.config.ServerConfig;
import ludo.server.config.ServerLog;
import ludo.server.coordinator.state.Reply;
import ludo.shared.PlayerColor;
import ludo.shared.protocol.JoinRequest;
import ludo.shared.protocol.RollCommand;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** The command queue on its own: bounded (back-pressure), consumed only by the game thread. */
class GameSessionTest {

    private static final ServerConfig CONFIG = ServerConfig.defaults().withQueueCapacity(2).withEchoGameLog(false)
            .withOut(new PrintStream(OutputStream.nullOutputStream()));

    @Test
    void fullQueueRefusesTheNextRequestWith503() {
        // Not started: nothing consumes the queue, as if the game thread were busy.
        GameSession session = new GameSession("t", 1, 0, CONFIG, new ServerLog(CONFIG.out()));
        RollCommand roll = new RollCommand(PlayerColor.RED, 1, 1, "r");

        CompletableFuture<Reply> first = session.submit(roll);
        CompletableFuture<Reply> second = session.submit(roll);
        CompletableFuture<Reply> third = session.submit(roll);

        assertFalse(first.isDone(), "queued, waiting for the game thread");
        assertFalse(second.isDone());
        assertTrue(third.isDone(), "refused at once, without waiting");
        assertEquals(503, third.join().status());
    }

    @Test
    void queueHighWaterMarkAndRefusalsAreCounted() {
        GameSession session = new GameSession("t", 1, 0, CONFIG, new ServerLog(CONFIG.out()));
        assertEquals(0, session.peakQueueDepth());

        session.submit(new RollCommand(PlayerColor.RED, 1, 1, "a"));
        session.submit(new RollCommand(PlayerColor.RED, 1, 1, "b"));
        Reply refused = session.request(new RollCommand(PlayerColor.RED, 1, 1, "c")); // full: answered at once

        assertEquals(503, refused.status());
        assertEquals(2, session.queueCapacity());
        assertEquals(2, session.queueDepth());
        assertEquals(2, session.peakQueueDepth(), "two commands waited at the same time");
        assertEquals(1, session.refused());
        assertEquals(0, session.accepted());
        assertEquals(0, session.rejected());
    }

    @Test
    void repliesAreCountedByStatus() throws Exception {
        GameSession session = new GameSession("t", 1, 0, CONFIG, new ServerLog(CONFIG.out()));
        session.start();
        session.request(new JoinRequest(PlayerColor.RED, "red", true));
        session.request(new RollCommand(PlayerColor.RED, 1, 0, "r")); // too early: 409
        session.shutdown(TimeUnit.SECONDS.toMillis(5));

        assertEquals(1, session.accepted());
        assertEquals(1, session.rejected());
        assertEquals(0, session.refused());
        assertEquals(0, session.otherErrors());
        assertTrue(session.peakQueueDepth() >= 1);
    }

    @Test
    void theGameThreadAnswersQueuedRequestsAndStopsWhenInterrupted() throws Exception {
        GameSession session = new GameSession("t", 1, 0, CONFIG, new ServerLog(CONFIG.out()));
        session.start();
        assertEquals("game-t", session.gameThread().getName());
        assertFalse(session.gameThread().isDaemon(), "the game thread must keep the JVM alive");

        Reply join = session.request(new JoinRequest(PlayerColor.RED, "red", true));
        Reply early = session.request(new RollCommand(PlayerColor.RED, 1, 0, "r"));

        assertEquals(200, join.status());
        assertEquals(409, early.status(), "no ROLL before the game has started");
        session.shutdown(TimeUnit.SECONDS.toMillis(5));
        assertFalse(session.gameThread().isAlive());
        assertTrue(session.isFinished());
        assertEquals(409, session.request(new JoinRequest(PlayerColor.BLUE, "blue", true)).status(), "game is over");
    }
}
