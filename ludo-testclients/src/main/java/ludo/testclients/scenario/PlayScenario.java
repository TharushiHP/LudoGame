package ludo.testclients.scenario;

import ludo.client.net.GatewayReply;
import ludo.client.net.HttpServerGateway;
import ludo.client.net.RetryPolicy;
import ludo.shared.json.JsonObjects;
import ludo.testclients.TestClientOptions;
import ludo.testclients.client.ServerProbe;
import ludo.testclients.report.Check;
import ludo.testclients.report.RequestLog;
import ludo.testclients.report.ScenarioResult;
import ludo.testclients.report.ServerStats;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Several complete games at the same time, each played by four fast automatic clients (turn delay 0).
 * All games are created at once, all 4N clients start at once, and every request they send
 * (JOIN, ROLL, DECISION, ACK) is asynchronous. Many game threads and many clients therefore load
 * the server together; the checks prove every game still ended consistently.
 */
public final class PlayScenario implements Scenario {

    @Override
    public String name() {
        return "play";
    }

    @Override
    public ScenarioResult run(TestClientOptions options) throws InterruptedException {
        LocalDateTime started = LocalDateTime.now();
        long start = System.nanoTime();
        long deadline = start + TimeUnit.MILLISECONDS.toNanos(options.timeoutMs());
        RequestLog log = new RequestLog();
        List<Check> checks = new ArrayList<>();
        List<ServerStats> stats = new ArrayList<>();
        List<String> notes = new ArrayList<>();

        List<String> gameIds = createGames(options, log, checks);
        if (gameIds.size() == options.games()) {
            notes.add("games " + gameIds + ", " + 4 * gameIds.size() + " clients, each with its own HttpClient");
            Players players = Players.start(options.server(), gameIds, log, options.timeoutMs());
            boolean finished = players.awaitGameOver(deadline);
            players.closeAll();
            if (!finished)
                notes.add("timed out after " + options.timeoutMs() + " ms");
            checks.addAll(players.checks(new ServerProbe(options.server()), warning -> false, stats));
            List<RequestLog.Completed> acks = log.completed().stream().filter(c -> c.type().equals("ack")).toList();
            long acksOk = acks.stream().filter(c -> c.finalStatus() == 200).count();
            checks.add(Check.of("every ACK accepted (200)", !acks.isEmpty() && acksOk == acks.size(),
                    acksOk + "/" + acks.size()));
        }
        checks.add(Check.of("no lost request and no server error", log.unexpected() == 0 && log.final503() == 0,
                log.unexpected() + " unexpected, " + log.final503() + " still 503 after all retries"));

        String shown = "games=" + options.games() + " clients-per-game=4 turn-delay=0 seed=" + options.seed();
        return new ScenarioResult(name(), options.server(), shown, started, Duration.ofNanos(System.nanoTime() - start),
                Map.of("players (" + 4 * options.games() + " clients)", log), stats, checks, notes);
    }

    /** POSTs every game at once (turn delay 0, seeds seed, seed + 1, ...) and waits for all ids. */
    private static List<String> createGames(TestClientOptions options, RequestLog log, List<Check> checks)
            throws InterruptedException {
        HttpServerGateway creator = new HttpServerGateway(HttpClient.newHttpClient(), options.server(),
                RetryPolicy.standard(), log);
        List<CompletableFuture<GatewayReply>> replies = new ArrayList<>();
        for (int i = 0; i < options.games(); i++)
            replies.add(creator.createGame(options.seed() + i, 0L));
        List<String> ids = new ArrayList<>();
        String problem = "";
        for (CompletableFuture<GatewayReply> reply : replies) {
            try {
                GatewayReply answer = reply.get(30, TimeUnit.SECONDS);
                if (answer.status() == 201)
                    ids.add(JsonObjects.getString(answer.body(), "gameId"));
                else
                    problem = answer.error();
            } catch (Exception e) {
                if (e instanceof InterruptedException)
                    throw (InterruptedException) e;
                problem = e.toString();
            }
        }
        checks.add(Check.of("all " + options.games() + " games created (201)", ids.size() == options.games(),
                ids.size() + " created" + (problem.isEmpty() ? "" : ", last problem: " + problem)));
        return ids;
    }
}
