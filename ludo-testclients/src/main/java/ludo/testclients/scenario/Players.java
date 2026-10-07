package ludo.testclients.scenario;

import ludo.client.net.RequestObserver;
import ludo.shared.PlayerColor;
import ludo.shared.snapshot.GameStatus;
import ludo.testclients.client.HeadlessPlayer;
import ludo.testclients.client.ServerProbe;
import ludo.testclients.report.Check;
import ludo.testclients.report.ServerStats;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

/**
 * The automatic players of one or more games: starts them all at the same time, waits for their
 * GAME_OVER and checks that every client and the server ended with the same state. Shared by the
 * play and burst scenarios.
 */
final class Players {

    private final Map<String, List<HeadlessPlayer>> byGame = new LinkedHashMap<>();

    /**
     * Four players (Red, Green, Yellow, Blue) per game, all started at once on daemon threads
     * {@code play-starter-N}: {@code ClientSession.start} blocks while its event stream opens.
     */
    static Players start(String server, List<String> gameIds, RequestObserver observer, long timeoutMs)
            throws InterruptedException {
        Players players = new Players();
        List<HeadlessPlayer> all = new ArrayList<>();
        for (String gameId : gameIds) {
            List<HeadlessPlayer> four = new ArrayList<>();
            for (PlayerColor colour : PlayerColor.values())
                four.add(new HeadlessPlayer(server, gameId, colour, observer));
            players.byGame.put(gameId, four);
            all.addAll(four);
        }
        ExecutorService starters = Executors.newFixedThreadPool(all.size(), daemon("play-starter-"));
        for (HeadlessPlayer player : all)
            starters.execute(() -> {
                try {
                    player.start();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        starters.shutdown();
        starters.awaitTermination(timeoutMs, TimeUnit.MILLISECONDS);
        return players;
    }

    /** Daemon threads named prefix1, prefix2, ...: a test client never keeps the JVM alive by itself. */
    static ThreadFactory daemon(String prefix) {
        AtomicInteger count = new AtomicInteger();
        return task -> {
            Thread thread = new Thread(task, prefix + count.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    /** Waits until every player has seen a GAME_OVER, or the deadline passes. */
    boolean awaitGameOver(long deadlineNanos) throws InterruptedException {
        for (List<HeadlessPlayer> four : byGame.values())
            for (HeadlessPlayer player : four)
                if (!player.awaitGameOver(Math.max(0, TimeUnit.NANOSECONDS.toMillis(deadlineNanos - System.nanoTime()))))
                    return false;
        return true;
    }

    void closeAll() {
        byGame.values().forEach(four -> four.forEach(HeadlessPlayer::close));
    }

    /**
     * The checks every scenario with real players makes, and the queue statistics of each game.
     *
     * @param allowedWarning warnings that are expected in this scenario (e.g. a ROLL that lost the race)
     */
    List<Check> checks(ServerProbe probe, Predicate<String> allowedWarning, List<ServerStats> stats) {
        List<Check> checks = new ArrayList<>();
        Map<String, Integer> statuses = new TreeMap<>();
        int over = 0;
        int states = 0;
        int mismatches = 0;
        int agreeing = 0;
        List<String> disagreements = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        for (Map.Entry<String, List<HeadlessPlayer>> game : byGame.entrySet()) {
            boolean gameOver = true;
            for (HeadlessPlayer player : game.getValue()) {
                states += player.view().states();
                mismatches += player.view().mismatches();
                for (String warning : player.view().warnings())
                    if (!allowedWarning.test(warning))
                        warnings.add(player.colour().display() + ": " + warning);
                if (player.view().result() == null || player.view().result().status() == GameStatus.ABORTED)
                    gameOver = false;
            }
            HeadlessPlayer first = game.getValue().get(0);
            if (first.view().result() != null)
                statuses.merge(first.view().result().status().name(), 1, Integer::sum);
            if (gameOver)
                over++;
            try {
                stats.add(probe.stats(game.getKey()));
                ServerProbe.FinalState last = probe.state(game.getKey());
                boolean allSame = last.hashMatches()
                        && game.getValue().stream().allMatch(p -> last.serverHash().equals(p.view().lastHash()));
                if (allSame)
                    agreeing++;
                else
                    disagreements.add("game " + game.getKey() + " v" + last.version());
            } catch (IOException e) {
                disagreements.add("game " + game.getKey() + ": " + e.getMessage());
            }
        }
        int games = byGame.size();
        checks.add(Check.of("every game reached GAME_OVER", over == games,
                over + "/" + games + " games, statuses " + statuses));
        checks.add(Check.of("every ACK hash matched (no client saw a different state)", mismatches == 0,
                mismatches + " mismatches in " + states + " STATEs received"));
        checks.add(Check.of("all 4 clients of each game and GET /state end with the same hash", agreeing == games,
                disagreements.isEmpty() ? agreeing + "/" + games + " games" : String.join(", ", disagreements)));
        checks.add(Check.of("no unexpected client warnings", warnings.isEmpty(),
                warnings.isEmpty() ? "" : warnings.size() + ", first: " + warnings.get(0)));
        return checks;
    }
}
