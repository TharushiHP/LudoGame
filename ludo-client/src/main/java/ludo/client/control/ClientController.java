package ludo.client.control;

import ludo.client.net.ConnectionState;
import ludo.client.net.EventStreamListener;
import ludo.client.net.GatewayReply;
import ludo.client.net.ServerGateway;
import ludo.shared.PlayerColor;
import ludo.shared.decision.MoveDecider;
import ludo.shared.decision.PieceChoice;
import ludo.shared.protocol.AckCommand;
import ludo.shared.protocol.DecisionKind;
import ludo.shared.protocol.DecisionReply;
import ludo.shared.protocol.DecisionRequest;
import ludo.shared.protocol.GameOverEvent;
import ludo.shared.protocol.JoinRequest;
import ludo.shared.protocol.NewGameEvent;
import ludo.shared.protocol.PausedEvent;
import ludo.shared.protocol.ResumedEvent;
import ludo.shared.protocol.RollCommand;
import ludo.shared.protocol.RollRequest;
import ludo.shared.protocol.ServerEvent;
import ludo.shared.protocol.StateEvent;
import ludo.shared.protocol.StateHasher;
import ludo.shared.snapshot.GameStatus;

import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class ClientController implements EventStreamListener.Callback {

    /**
     * How long to wait for the screen to show a STATE before giving up on that ACK.
     */
    static final long VIEW_TIMEOUT_MS = 5_000;

    private final ServerGateway gateway;
    private final String gameId;
    private final Identity me;
    private final GameView view;
    private final MoveDecider decider;
    private final BlockingQueue<ServerEvent> inbox = new LinkedBlockingQueue<>();
    private final ExecutorService decisionWorker;
    private final CountDownLatch gameOver = new CountDownLatch(1);
    private final Thread thread;

    public ClientController(ServerGateway gateway, String gameId, Identity me, GameView view, MoveDecider decider) {
        this.gameId = gameId;
        this.gateway = gateway;
        this.me = me;
        this.view = view;
        this.decider = decider;
        this.decisionWorker = Executors.newSingleThreadExecutor(task -> {
            Thread worker = new Thread(task, "decision-worker");
            worker.setDaemon(true); // a short CPU task; must not keep the JVM alive
            return worker;
        });
        this.thread = new Thread(this::run, "client-controller");
        this.thread.setDaemon(true); // the GUI (EDT) or the headless main thread keeps the JVM alive
    }

    public void start() {
        thread.start();
    }

    public void stop() {
        thread.interrupt();
        decisionWorker.shutdownNow();
    }

    public CompletableFuture<Void> join() {
        if (me.isSpectator())
            return CompletableFuture.completedFuture(null);
        JoinRequest join = new JoinRequest(me.colour(), me.name(), decider.triesOtherPiecesWhenBlocked(me.colour()));
        return gateway.join(gameId, join).handle((reply, error) -> {
            if (error != null)
                view.showWarning("JOIN failed: " + error.getMessage());
            else if (reply.status() == 409)
                view.showWarning(
                        me.colour().display() + " has already joined this game; continuing as a reconnecting client");
            else if (!reply.isSuccess())
                view.showWarning("JOIN refused: " + reply.error());
            return null;
        });
    }

    public boolean awaitGameOver(long timeoutMs) throws InterruptedException {
        return gameOver.await(timeoutMs, TimeUnit.MILLISECONDS);
    }

    // --- EventStreamListener.Callback (called on the event-stream thread) ---

    @Override
    public void onEvent(ServerEvent event) {
        inbox.add(event);
    }

    @Override
    public void onConnection(ConnectionState state) {
        view.showConnection(state);
        if (state == ConnectionState.CLOSED)
            gameOver.countDown(); // the listener has stopped for good: nothing more will come
    }

    @Override
    public void onProblem(String message) {
        view.showWarning(message);
    }

    // --- the controller thread ---

    private void run() {
        try {
            while (true) {
                ServerEvent event = inbox.take();
                handle(event);
                if (event instanceof GameOverEvent over && !over.hasNextGame())
                    break; // the session's last game is over
            }
        } catch (InterruptedException e) {
            // stop() was called: the window was closed
        } finally {
            decisionWorker.shutdown();
        }
    }

    private void handle(ServerEvent event) throws InterruptedException {
        if (event instanceof StateEvent state) {
            onState(state);
        } else if (event instanceof RollRequest request) {
            view.showRequest(request);
            if (me.plays(request.colour()))
                send("ROLL", gateway.roll(gameId,
                        new RollCommand(me.colour(), request.turnId(), request.version(), newId())));
        } else if (event instanceof DecisionRequest question) {
            view.showRequest(question);
            if (me.plays(question.colour()))
                decisionWorker.execute(() -> decideAndSend(question));
        } else if (event instanceof PausedEvent paused) {
            view.showPaused(paused);
        } else if (event instanceof ResumedEvent resumed) {
            view.showResumed(resumed);
            if (resumed.substituted() && me.plays(resumed.colour()))
                view.showWarning("You did not answer in time: the server now plays " + me.colour().display()
                        + " for the rest of the game. You can still watch.");
        } else if (event instanceof GameOverEvent over) {
            view.showGameOver(over);
            if (!over.hasNextGame())
                gameOver.countDown();
        } else if (event instanceof NewGameEvent newGame) {
            view.showNewGame(newGame);
        }
    }

    private void onState(StateEvent state) throws InterruptedException {
        String myHash = StateHasher.hash(state.snapshot());
        boolean synced = myHash.equals(state.hash());
        if (!synced)
            view.showWarning("STATE v" + state.version() + ": my hash differs from the server's; asking for it again");
        try {
            view.showState(state, synced).get(VIEW_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException | ExecutionException e) {
            view.showWarning("the screen did not show STATE v" + state.version() + " in time; it is not acknowledged");
            return;
        }
        // Only now is the state on screen, so only now may this player acknowledge it.
        if (me.isSpectator() || state.snapshot().status() != GameStatus.IN_PROGRESS)
            return; // spectators never ACK; the final STATE needs no ACK
        send("ACK v" + state.version(),
                gateway.ack(gameId, new AckCommand(me.colour(), state.version(), myHash, newId())));
    }

    /** Runs on the decision-worker thread. */
    private void decideAndSend(DecisionRequest question) {
        try {
            send("DECISION", gateway.decision(gameId, decide(question)));
        } catch (RuntimeException e) {
            view.showWarning("strategy failed on decision " + question.decisionId() + ": " + e);
        }
    }

    DecisionReply decide(DecisionRequest question) {
        PlayerColor colour = me.colour();
        if (question.kind() == DecisionKind.MOVE_FROM_BASE) {
            boolean fromBase = decider.prefersMoveFromBase(question.snapshot(), colour);
            return DecisionReply.moveFromBase(colour, question.decisionId(), question.version(), newId(), fromBase);
        }
        PieceChoice choice = decider.choosePiece(question.snapshot(), colour, question.roll(), question.candidates());
        return DecisionReply.choosePiece(colour, question.decisionId(), question.version(), newId(),
                choice.piece(), choice.memo());
    }

    private void send(String what, CompletableFuture<GatewayReply> reply) {
        reply.whenComplete((answer, error) -> {
            if (error != null)
                view.showWarning(what + " not delivered: " + error.getMessage());
            else if (!answer.isSuccess())
                view.showWarning(what + " refused: " + answer.error());
        });
    }

    private static String newId() {
        return UUID.randomUUID().toString();
    }
}
