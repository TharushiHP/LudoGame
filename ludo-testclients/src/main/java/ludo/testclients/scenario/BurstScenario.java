package ludo.testclients.scenario;

import ludo.client.net.GatewayReply;
import ludo.client.net.HttpServerGateway;
import ludo.client.net.RetryPolicy;
import ludo.shared.PlayerColor;
import ludo.shared.json.JsonObjects;
import ludo.testclients.TestClientOptions;
import ludo.testclients.client.BurstClient;
import ludo.testclients.client.ServerProbe;
import ludo.testclients.report.Check;
import ludo.testclients.report.RequestLog;
import ludo.testclients.report.ScenarioResult;
import ludo.testclients.report.ServerStats;

import java.io.File;
import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;


public final class BurstScenario implements Scenario {

    private static final long WATCH_DELAY_MS = 4_000;
    private static final long CONNECT_MS = 10_000;

    @Override
    public String name() {
        return "burst";
    }

    @Override
    public ScenarioResult run(TestClientOptions options) throws InterruptedException {
        LocalDateTime started = LocalDateTime.now();
        long start = System.nanoTime();
        long deadline = start + TimeUnit.MILLISECONDS.toNanos(options.timeoutMs());
        RequestLog burstLog = new RequestLog();
        RequestLog playerLog = new RequestLog();
        List<Check> checks = new ArrayList<>();
        List<ServerStats> stats = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        String shown = "clients=" + options.clients() + " requests=" + options.requests() + " wave-size="
                + options.waveSize() + " turn-delay=" + options.turnDelayMs() + " seed=" + options.seed();
        Map<String, RequestLog> logs = new LinkedHashMap<>();
        logs.put("burst clients (" + options.clients() + " HttpClients)", burstLog);
        logs.put("players (4 clients)", playerLog);

        String gameId = createGame(options, playerLog);
        checks.add(Check.of("burst game created (201)", gameId != null, gameId == null ? "POST /games failed" : "game " + gameId));
        if (gameId == null)
            return new ScenarioResult(name(), options.server(), shown, started, since(start), logs, stats, checks, notes);
        if (options.watchJar() != null)
            notes.add(openSpectator(options, gameId));

        List<BurstClient> bursts = new ArrayList<>();
        Set<PlayerColor> seats = EnumSet.noneOf(PlayerColor.class);
        for (int i = 0; i < options.clients(); i++)
            seats.add(PlayerColor.values()[i]);
        for (int i = 0; i < options.clients(); i++) {
            BurstClient burst = new BurstClient(i + 1, options.server(), gameId, PlayerColor.values()[i], seats,
                    options.requests(), options.waveSize(), burstLog);
            burst.connect(CONNECT_MS);
            bursts.add(burst);
        }
        Players players = Players.start(options.server(), List.of(gameId), playerLog, options.timeoutMs());
        for (BurstClient burst : bursts)
            burst.awaitLaunched(remainingMs(deadline));
        boolean allAnswered = true;
        for (BurstClient burst : bursts)
            allAnswered &= burst.awaitReplies(remainingMs(deadline));
        boolean over = players.awaitGameOver(deadline);
        players.closeAll();
        bursts.forEach(BurstClient::close);
        if (!over)
            notes.add("the game did not end within " + options.timeoutMs() + " ms");

        // A burst ROLL may win the race against the real player: then the player's own ROLL gets 409.
        checks.addAll(players.checks(new ServerProbe(options.server()), w -> w.startsWith("ROLL refused: 409"), stats));
        checks.addAll(burstChecks(options, bursts, allAnswered, notes));
        int peak = stats.isEmpty() ? 0 : stats.get(0).peakQueueDepth();
        checks.add(Check.of("requests waited in the server's queue (peakQueueDepth > 1)", peak > 1,
                "peakQueueDepth " + peak + " of " + (stats.isEmpty() ? "?" : stats.get(0).queueCapacity())));
        checks.add(Check.of("no lost request and no server error other than 503",
                burstLog.unexpected() == 0 && playerLog.unexpected() == 0,
                (burstLog.unexpected() + playerLog.unexpected()) + " unexpected"));
        notes.add("503 on first attempt: " + (burstLog.firstAttempt503() + playerLog.firstAttempt503())
                + ", retries: " + (burstLog.retries() + playerLog.retries())
                + ", still 503 after all retries: " + (burstLog.final503() + playerLog.final503()));
        return new ScenarioResult(name(), options.server(), shown, started, since(start), logs, stats, checks, notes);
    }

    private static List<Check> burstChecks(TestClientOptions options, List<BurstClient> bursts, boolean allAnswered,
                                           List<String> notes) {
        List<Check> checks = new ArrayList<>();
        List<BurstClient.Sent> sent = new ArrayList<>();
        int launched = 0;
        int waves = 0;
        long early = 0;
        for (BurstClient burst : bursts) {
            sent.addAll(burst.sent());
            launched += burst.launched();
            waves += burst.waves();
            early += burst.wavesLaunchedBeforeFirstReply();
            notes.add("burst client " + (bursts.indexOf(burst) + 1) + " ("+ burst.seat().display() + "): "
                    + burst.launched() + " requests in " + burst.waves() + " waves");
        }
        notes.add("waves fully launched before their first reply arrived: " + early + "/" + waves);
        int expected = options.clients() * options.requests();
        checks.add(Check.of("every burst request launched", launched == expected, launched + "/" + expected));

        long answered = sent.stream().filter(s -> s.status() == 200 || s.status() == 409 || s.status() == 503).count();
        checks.add(Check.of("every burst request answered with 200, 409 or 503", allAnswered && answered == launched
                && sent.size() == launched, answered + "/" + launched + " answered, " + count(sent)));

        // Repeated requestIds: one status per id, and identical bodies for an accepted one. Only copies
        // that reached the game count: a copy still refused with 503 after all retries never entered
        // the queue, so the game never saw it (reported in the notes instead).
        Map<String, List<BurstClient.Sent>> byId = sent.stream().collect(Collectors.groupingBy(BurstClient.Sent::requestId));
        int groups = 0;
        int copies = 0;
        int gaveUp = 0;
        long acceptedDuplicates = 0;
        List<String> broken = new ArrayList<>();
        for (Map.Entry<String, List<BurstClient.Sent>> group : byId.entrySet()) {
            if (group.getValue().size() < 2)
                continue;
            List<BurstClient.Sent> reached = group.getValue().stream().filter(s -> s.status() != 503).toList();
            gaveUp += group.getValue().size() - reached.size();
            if (reached.size() < 2)
                continue;
            groups++;
            copies += reached.size();
            BurstClient.Sent first = reached.get(0);
            if (first.status() == 200)
                acceptedDuplicates++;
            boolean same = reached.stream().allMatch(s -> s.status() == first.status()
                    && (first.status() != 200 || s.body().equals(first.body())));
            if (!same)
                broken.add(group.getKey());
        }
        notes.add(gaveUp + " duplicate copies gave up after all retries; never applied");
        checks.add(Check.of("a repeated requestId gets the identical stored reply", groups > 0 && broken.isEmpty(),
                groups + " repeated ids reached the game more than once (" + copies + " copies; "
                        + acceptedDuplicates + " ids accepted)"
                        + (broken.isEmpty() ? "" : ", different replies for " + broken)));

        // At most one accepted ROLL (one requestId) per turn.
        Map<Long, Set<String>> acceptedRolls = new TreeMap<>();
        for (BurstClient.Sent s : sent)
            if (s.type().equals("roll") && s.status() == 200)
                acceptedRolls.computeIfAbsent(s.turnId(), t -> new HashSet<>()).add(s.requestId());
        long twice = acceptedRolls.values().stream().filter(ids -> ids.size() > 1).count();
        checks.add(Check.of("at most one ROLL accepted per turnId", twice == 0,
                acceptedRolls.size() + " turns won by a burst ROLL, " + twice + " turns with two"));

        long staleOk = sent.stream().filter(s -> s.kind() == BurstClient.Kind.ROLL_STALE && s.status() == 200).count();
        checks.add(Check.of("every stale ROLL rejected", staleOk == 0, staleOk + " stale ROLLs accepted"));
        return checks;
    }

    private static String count(List<BurstClient.Sent> sent) {
        Map<Integer, Long> byStatus = new TreeMap<>(sent.stream()
                .collect(Collectors.groupingBy(BurstClient.Sent::status, Collectors.counting())));
        return "by status " + byStatus;
    }

    private static String createGame(TestClientOptions options, RequestLog log) throws InterruptedException {
        HttpServerGateway creator = new HttpServerGateway(HttpClient.newHttpClient(), options.server(),
                RetryPolicy.standard(), log);
        try {
            GatewayReply reply = creator.createGame(options.seed(), options.turnDelayMs()).get(30, TimeUnit.SECONDS);
            return reply.status() == 201 ? JsonObjects.getString(reply.body(), "gameId") : null;
        } catch (InterruptedException e) {
            throw e;
        } catch (Exception e) {
            return null;
        }
    }

    /** Starts the spectator GUI as its own process, then gives it a few seconds to open its window. */
    private static String openSpectator(TestClientOptions options, String gameId) throws InterruptedException {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        try {
            new ProcessBuilder(java, "-jar", options.watchJar(), "--server=" + options.server(), "--game=" + gameId,
                    "--colour=SPECTATOR", "--name=Burst viewer")
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
        } catch (IOException e) {
            return "could not open the spectator window (" + new File(options.watchJar()).getAbsolutePath() + "): " + e.getMessage();
        }
        Thread.sleep(WATCH_DELAY_MS); // only so the window shows the game from its first move
        return "spectator window opened on game " + gameId;
    }

    private static long remainingMs(long deadline) {
        return Math.max(0, TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime()));
    }

    private static Duration since(long start) {
        return Duration.ofNanos(System.nanoTime() - start);
    }
}
