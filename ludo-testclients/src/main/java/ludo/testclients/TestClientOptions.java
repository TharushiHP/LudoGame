package ludo.testclients;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;


public record TestClientOptions(String server, String scenario, int games, int clients, int requests, int waveSize,
                                int creates, long turnDelayMs, long seed, long timeoutMs, Path outDir, String watchJar) {

    public static final List<String> SCENARIOS = List.of("play", "burst", "create", "all");

    static final String USAGE = "Usage: java -jar ludo-testclients.jar --server=http://localhost:8080"
            + " --scenario=play|burst|create|all [--games=3] [--clients=2] [--requests=200] [--wave-size=50]"
            + " [--creates=25] [--turn-delay=0] [--seed=1] [--timeout=300000] [--out-dir=logs]"
            + " [--watch=ludo-client/target/ludo-client.jar]";

    public static TestClientOptions defaults() {
        return new TestClientOptions("http://localhost:8080", "all", 3, 2, 200, 50, 25, 0, 1, 300_000,
                Path.of("logs"), null);
    }

    public static TestClientOptions parse(String[] args) {
        TestClientOptions d = defaults();
        String server = d.server, scenario = d.scenario, watchJar = d.watchJar;
        int games = d.games, clients = d.clients, requests = d.requests, waveSize = d.waveSize, creates = d.creates;
        long turnDelayMs = d.turnDelayMs, seed = d.seed, timeoutMs = d.timeoutMs;
        Path outDir = d.outDir;
        for (String arg : args) {
            String[] keyValue = arg.split("=", 2);
            if (keyValue.length != 2 || keyValue[1].isBlank())
                throw new IllegalArgumentException("Unknown option: " + arg);
            String value = keyValue[1].trim();
            switch (keyValue[0]) {
                case "--server" -> server = value;
                case "--scenario" -> scenario = scenario(value);
                case "--games" -> games = positive(arg, value);
                case "--clients" -> clients = positive(arg, value);
                case "--requests" -> requests = positive(arg, value);
                case "--wave-size" -> waveSize = positive(arg, value);
                case "--creates" -> creates = positive(arg, value);
                case "--turn-delay" -> turnDelayMs = number(arg, value);
                case "--seed" -> seed = number(arg, value);
                case "--timeout" -> timeoutMs = positive(arg, value);
                case "--out-dir" -> outDir = Path.of(value);
                case "--watch" -> watchJar = value;
                default -> throw new IllegalArgumentException("Unknown option: " + arg);
            }
        }
        if ((scenario.equals("burst") || scenario.equals("all")) && clients > 4)
            throw new IllegalArgumentException("burst: --clients must be 1 to 4 (one seat each)");
        return new TestClientOptions(server, scenario, games, clients, requests, waveSize, creates, turnDelayMs, seed,
                timeoutMs, outDir, watchJar);
    }

    // --- changed copies, for the tests ---

    public TestClientOptions withServer(String value) {
        return new TestClientOptions(value, scenario, games, clients, requests, waveSize, creates, turnDelayMs, seed,
                timeoutMs, outDir, watchJar);
    }

    public TestClientOptions withGames(int value) {
        return new TestClientOptions(server, scenario, value, clients, requests, waveSize, creates, turnDelayMs, seed,
                timeoutMs, outDir, watchJar);
    }

    public TestClientOptions withClients(int value) {
        return new TestClientOptions(server, scenario, games, value, requests, waveSize, creates, turnDelayMs, seed,
                timeoutMs, outDir, watchJar);
    }

    public TestClientOptions withRequests(int value) {
        return new TestClientOptions(server, scenario, games, clients, value, waveSize, creates, turnDelayMs, seed,
                timeoutMs, outDir, watchJar);
    }

    public TestClientOptions withWaveSize(int value) {
        return new TestClientOptions(server, scenario, games, clients, requests, value, creates, turnDelayMs, seed,
                timeoutMs, outDir, watchJar);
    }

    public TestClientOptions withCreates(int value) {
        return new TestClientOptions(server, scenario, games, clients, requests, waveSize, value, turnDelayMs, seed,
                timeoutMs, outDir, watchJar);
    }

    public TestClientOptions withOutDir(Path value) {
        return new TestClientOptions(server, scenario, games, clients, requests, waveSize, creates, turnDelayMs, seed,
                timeoutMs, value, watchJar);
    }

    private static String scenario(String value) {
        String name = value.toLowerCase(Locale.ROOT);
        if (!SCENARIOS.contains(name))
            throw new IllegalArgumentException("--scenario must be one of " + SCENARIOS + ", not " + value);
        return name;
    }

    private static int positive(String arg, String text) {
        long value = number(arg, text);
        if (value == 0 || value > Integer.MAX_VALUE)
            throw new IllegalArgumentException("Must be at least 1: " + arg);
        return (int) value;
    }

    private static long number(String arg, String text) {
        try {
            long value = Long.parseLong(text);
            if (value < 0)
                throw new IllegalArgumentException("Negative value in " + arg);
            return value;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Not a number in " + arg);
        }
    }
}
