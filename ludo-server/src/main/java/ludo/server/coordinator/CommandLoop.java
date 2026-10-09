package ludo.server.coordinator;

import ludo.server.config.ServerLog;
import ludo.server.coordinator.state.CoordinatorContext;
import ludo.server.coordinator.state.CoordinatorState;
import ludo.server.coordinator.state.Reply;
import ludo.shared.PlayerColor;
import ludo.shared.protocol.AckCommand;
import ludo.shared.protocol.ClientRequest;
import ludo.shared.protocol.DecisionReply;
import ludo.shared.protocol.JoinRequest;
import ludo.shared.protocol.RollCommand;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

final class CommandLoop {

    private static final long SLICE_NANOS = TimeUnit.MILLISECONDS.toNanos(200);

    private final BlockingQueue<Command> queue;
    private final ServerLog log;
    private final CoordinatorContext ctx;
    private final PlayerSeats seats;
    private final Consumer<EventSink> onConnected;
    private final Consumer<EventSink> onLost;
    private final Consumer<String> statePublisher;
    private CoordinatorState state;

    // Kept for 30 s, not for a number of requests: under overload a retry can
    // arrive after
    // thousands of other accepted requests, but always within the client's retry
    // window.
    private final ReplyMemory acceptedReplies;

    CommandLoop(BlockingQueue<Command> queue, ServerLog log, CoordinatorContext ctx, PlayerSeats seats,
            Consumer<EventSink> onConnected, Consumer<EventSink> onLost, Consumer<String> statePublisher) {
        this.queue = queue;
        this.log = log;
        this.acceptedReplies = new ReplyMemory(log::log);
        this.ctx = ctx;
        this.seats = seats;
        this.onConnected = onConnected;
        this.onLost = onLost;
        this.statePublisher = statePublisher;
    }

    /** Switches to the next state and logs the transition. */
    void enter(CoordinatorState next) {
        log.log("state " + (state == null ? "(none)" : state.name()) + " -> " + next.name());
        state = next;
        statePublisher.accept(next.name());
    }

    
    boolean awaitUntil(BooleanSupplier done, long deadlineNanos) throws InterruptedException {
        while (!done.getAsBoolean()) {
            long remaining = deadlineNanos - System.nanoTime();
            if (remaining <= 0)
                return false;
            Command command = queue.poll(Math.min(remaining, SLICE_NANOS), TimeUnit.NANOSECONDS);
            if (command != null)
                process(command);
        }
        return true;
    }

    /**
     * Like {@link #awaitUntil} without a deadline (waiting for players to join).
     */
    void awaitForever(BooleanSupplier done) throws InterruptedException {
        while (!done.getAsBoolean()) {
            Command command = queue.poll(SLICE_NANOS, TimeUnit.NANOSECONDS);
            if (command != null)
                process(command);
        }
    }

    /**
     * Answers every command still queued with {@code reply} (used once the game is
     * over).
     */
    void drain(Reply reply) {
        Command command;
        while ((command = queue.poll()) != null) {
            if (command instanceof Command.Request request) {
                request.reply().complete(reply);
                log.log("drained " + describe(request.request()) + " -> " + reply.summary());
            }
        }
    }

    private void process(Command command) {
        if (command instanceof Command.Request request) {
            Reply reply = handle(request.request());
            request.reply().complete(reply);
        } else if (command instanceof Command.Connected connected) {
            onConnected.accept(connected.sink());
        } else if (command instanceof Command.Lost lost) {
            onLost.accept(lost.sink());
        }
    }

    private Reply handle(ClientRequest request) {
        String requestId = requestIdOf(request);
        Reply reply;
        String note = "";
        Reply stored = requestId == null ? null : acceptedReplies.get(requestId);
        if (stored != null) {
            reply = stored;
            note = " (duplicate requestId: stored reply, not applied again)";
        } else {
            reply = checkSeat(request);
            if (reply == null)
                reply = dispatch(request);
            if (requestId != null && reply.isSuccess())
                acceptedReplies.remember(requestId, reply);
        }
        log.log("queue=" + queue.size() + " " + describe(request) + " in " + state.name() + " -> " + reply.summary()
                + note);
        return reply;
    }

    private Reply checkSeat(ClientRequest request) {
        if (request instanceof JoinRequest)
            return null;
        PlayerColor colour = colourOf(request);
        if (!seats.isTaken(colour))
            return Reply.conflict(colour + " has not joined this game");
        if (!(request instanceof AckCommand) && seats.isSubstituted(colour))
            return Reply.conflict(colour + " is now played by the server (it did not answer in time)");
        return null;
    }

    private Reply dispatch(ClientRequest request) {
        if (request instanceof JoinRequest join)
            return state.onJoin(join, ctx);
        if (request instanceof RollCommand roll)
            return state.onRoll(roll, ctx);
        if (request instanceof DecisionReply decision)
            return state.onDecision(decision, ctx);
        return state.onAck((AckCommand) request, ctx);
    }

    private static String requestIdOf(ClientRequest request) {
        if (request instanceof RollCommand roll)
            return roll.requestId();
        if (request instanceof DecisionReply decision)
            return decision.requestId();
        if (request instanceof AckCommand ack)
            return ack.requestId();
        return null;
    }

    private static PlayerColor colourOf(ClientRequest request) {
        if (request instanceof JoinRequest join)
            return join.colour();
        if (request instanceof RollCommand roll)
            return roll.colour();
        if (request instanceof DecisionReply decision)
            return decision.colour();
        return ((AckCommand) request).colour();
    }

    private static String describe(ClientRequest request) {
        String id = requestIdOf(request);
        return request.type() + " " + colourOf(request) + (id == null ? "" : " req=" + id);
    }
}
