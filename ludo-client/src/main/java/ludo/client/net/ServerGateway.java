package ludo.client.net;

import ludo.shared.protocol.AckCommand;
import ludo.shared.protocol.DecisionReply;
import ludo.shared.protocol.JoinRequest;
import ludo.shared.protocol.RollCommand;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Everything the client asks the coordinator server (Proxy pattern: this is the subject interface,
 * {@link HttpServerGateway} is the remote proxy that sends it over HTTP, and tests use a fake).
 * Every call is asynchronous: it returns at once and the future completes with the server's reply.
 * The caller puts a fresh requestId into ROLL, DECISION and ACK. A retry resends that same request,
 * which is safe because the server answers a repeated requestId from its idempotency map.
 */
public interface ServerGateway {

    CompletableFuture<List<GameSummary>> listGames();

    /** POST /games. {@code seed} and {@code turnDelayMs} may be null (the server's defaults). */
    CompletableFuture<GatewayReply> createGame(Long seed, Long turnDelayMs);

    CompletableFuture<GatewayReply> join(String gameId, JoinRequest join);

    CompletableFuture<GatewayReply> roll(String gameId, RollCommand roll);

    CompletableFuture<GatewayReply> decision(String gameId, DecisionReply decision);

    CompletableFuture<GatewayReply> ack(String gameId, AckCommand ack);
}
