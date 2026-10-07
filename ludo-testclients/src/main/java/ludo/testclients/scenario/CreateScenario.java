package ludo.testclients.scenario;

import ludo.client.net.GatewayReply;
import ludo.client.net.HttpServerGateway;
import ludo.client.net.RetryPolicy;
import ludo.testclients.TestClientOptions;
import ludo.testclients.report.Check;
import ludo.testclients.report.RequestLog;
import ludo.testclients.report.ScenarioResult;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * K clients each create M games at the same time ({@code POST /games}, all asynchronous, released
 * together by a start latch). Many HTTP threads then call the server's registry at once; every
 * game must still get its own id (the registry's AtomicLong and ConcurrentHashMap).
 * The new games wait for players until the server stops; that is expected.
 */
public final class CreateScenario implements Scenario {

    @Override
    public String name() {
        return "create";
    }

    @Override
    public ScenarioResult run(TestClientOptions options) throws InterruptedException {
        LocalDateTime started = LocalDateTime.now();
        long start = System.nanoTime();
        RequestLog log = new RequestLog();
        ConcurrentLinkedQueue<CompletableFuture<GatewayReply>> replies = new ConcurrentLinkedQueue<>();
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService clients = Executors.newFixedThreadPool(options.clients(), Players.daemon("create-client-"));
        for (int c = 0; c < options.clients(); c++) {
            HttpServerGateway gateway = new HttpServerGateway(HttpClient.newHttpClient(), options.server(),
                    RetryPolicy.standard(), log);
            clients.execute(() -> {
                try {
                    go.await(); // every client starts at the same moment
                    for (int i = 0; i < options.creates(); i++)
                        replies.add(gateway.createGame(null, options.turnDelayMs()));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        go.countDown();
        clients.shutdown();
        clients.awaitTermination(options.timeoutMs(), TimeUnit.MILLISECONDS);

        List<String> ids = new ArrayList<>();
        int failed = 0;
        for (CompletableFuture<GatewayReply> reply : replies) {
            try {
                GatewayReply answer = reply.get(options.timeoutMs(), TimeUnit.MILLISECONDS);
                if (answer.status() == 201)
                    ids.add(String.valueOf(answer.body().get("gameId")));
                else
                    failed++;
            } catch (InterruptedException e) {
                throw e;
            } catch (Exception e) {
                failed++;
            }
        }
        int expected = options.clients() * options.creates();
        Set<String> unique = new HashSet<>(ids);
        List<Check> checks = List.of(
                Check.of("every POST /games answered 201", ids.size() == expected,
                        ids.size() + "/" + expected + (failed == 0 ? "" : ", " + failed + " failed")),
                Check.of("every game id is unique", unique.size() == ids.size() && !ids.isEmpty(),
                        unique.size() + " distinct ids"));
        String shown = "clients=" + options.clients() + " creates=" + options.creates();
        return new ScenarioResult(name(), options.server(), shown, started, Duration.ofNanos(System.nanoTime() - start),
                Map.of("creating clients (" + options.clients() + " HttpClients)", log), List.of(), checks,
                List.of("ids " + range(ids)));
    }

    private static String range(List<String> ids) {
        List<Long> numbers = new ArrayList<>();
        for (String id : ids) {
            try {
                numbers.add(Long.parseLong(id));
            } catch (NumberFormatException e) {
                return ids.size() + " ids";
            }
        }
        return numbers.isEmpty() ? "none"
                : numbers.stream().min(Long::compare).get() + " to " + numbers.stream().max(Long::compare).get();
    }
}
