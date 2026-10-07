package ludo.server;

import ludo.server.config.ServerConfig;

import java.io.IOException;

public final class ServerMain {

    static final String USAGE = "Usage: java -jar ludo-server.jar [--port=8080] [--turn-delay=500] [--move-timeout=10000]"
            + " [--end-condition=FIRST_WINNER|ALL_PLACES] [--rematch-delay=10000] [--queue-capacity=64]";

    private ServerMain() {
    }

    public static void main(String[] args) {
        ServerConfig config;
        try {
            config = parse(args);
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            System.err.println(USAGE);
            System.exit(1);
            return;
        }
        try {
            LudoServer server = LudoServer.start(config);
            Runtime.getRuntime().addShutdownHook(new Thread(server::stop, "shutdown-hook"));
            // main returns now; the non-daemon HTTP dispatcher and worker threads keep the
            // JVM alive.
        } catch (IOException e) {
            System.err.println("Could not start the server on port " + config.port() + ": " + e.getMessage());
            System.exit(1);
        }
    }

    static ServerConfig parse(String[] args) {
        ServerConfig config = ServerConfig.defaults();
        for (String arg : args) {
            String[] keyValue = arg.split("=", 2);
            if (keyValue.length != 2)
                throw new IllegalArgumentException("Unknown option: " + arg);
            if (keyValue[0].equals("--end-condition")) {
                config = config.withEndCondition(ServerConfig.parseEndCondition(keyValue[1]));
                continue;
            }
            long value = number(arg, keyValue[1]);
            switch (keyValue[0]) {
                case "--port" -> {
                    if (value < 0 || value > 65_535)
                        throw new IllegalArgumentException("Port out of range: " + value);
                    config = config.withPort((int) value);
                }
                case "--turn-delay" -> config = config.withTurnDelayMs(value);
                case "--rematch-delay" -> config = config.withRematchDelayMs(value);
                case "--queue-capacity" -> {
                    if (value == 0 || value > Integer.MAX_VALUE)
                        throw new IllegalArgumentException(
                                "--queue-capacity must be between 1 and " + Integer.MAX_VALUE);
                    config = config.withQueueCapacity((int) value);
                }
                case "--move-timeout" -> {
                    if (value == 0)
                        throw new IllegalArgumentException("--move-timeout must be positive");
                    config = config.withMoveTimeoutMs(value);
                }
                default -> throw new IllegalArgumentException("Unknown option: " + arg);
            }
        }
        return config;
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
