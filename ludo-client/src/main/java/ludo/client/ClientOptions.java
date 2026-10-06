package ludo.client;

import ludo.client.control.Identity;
import ludo.shared.PlayerColor;

/**
 * The client's command-line options (Value Object):
 * {@code --server=URL --game=ID --colour=RED|GREEN|YELLOW|BLUE|SPECTATOR --name=TEXT --headless}.
 * All are optional; without --game and --colour the connect window opens.
 *
 * @param identity null when no --colour was given
 */
public record ClientOptions(String server, String gameId, Identity identity, boolean headless) {

    public static final String DEFAULT_SERVER = "http://localhost:8080";

    public static ClientOptions parse(String... args) {
        String server = DEFAULT_SERVER;
        String gameId = null;
        String colour = null;
        String name = null;
        boolean headless = false;
        for (String arg : args) {
            if (arg.equals("--headless")) {
                headless = true;
                continue;
            }
            int eq = arg.indexOf('=');
            if (!arg.startsWith("--") || eq < 0)
                throw new IllegalArgumentException("unknown option: " + arg);
            String key = arg.substring(2, eq);
            String value = arg.substring(eq + 1);
            switch (key) {
                case "server" -> server = value;
                case "game" -> gameId = value;
                case "colour", "color" -> colour = value.toUpperCase();
                case "name" -> name = value;
                default -> throw new IllegalArgumentException("unknown option: " + arg);
            }
        }
        Identity identity = null;
        if (colour != null) {
            if (colour.equals("SPECTATOR")) {
                identity = Identity.spectator(name);
            } else {
                try {
                    identity = Identity.player(PlayerColor.valueOf(colour), name);
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("--colour must be RED, GREEN, YELLOW, BLUE or SPECTATOR, not " + colour);
                }
            }
        }
        if (headless && (gameId == null || identity == null))
            throw new IllegalArgumentException("--headless needs --game and --colour");
        return new ClientOptions(server, gameId, identity, headless);
    }

    /** True when the connect window can be skipped. */
    public boolean skipsConnectWindow() {
        return gameId != null && identity != null;
    }
}
