package ludo;

import ludo.game.Game;
import ludo.game.GameBuilder;
import ludo.output.GameLogger;

import java.util.Random;

/**
 * Console entry point: builds and runs one simulation.
 * Optional argument {@code --seed=<number>} replays a game exactly; without it a random
 * seed is chosen and printed so the game can still be replayed.
 */
public class Main {

    private static final String SEED_PREFIX = "--seed=";

    public static void main(String[] args) {
        long seed;
        try {
            seed = parseSeed(args);
        } catch (NumberFormatException e) {
            System.err.println("Invalid seed. Usage: java -jar ludo-twist.jar [--seed=<number>]");
            System.exit(1);
            return;
        }
        GameLogger.getInstance().log("Seed: " + seed + " (replay this game with " + SEED_PREFIX + seed + ")");
        Game game = new GameBuilder().withSeed(seed).build();
        game.run();
    }

    static long parseSeed(String[] args) {
        for (String arg : args) {
            if (arg.startsWith(SEED_PREFIX)) {
                return Long.parseLong(arg.substring(SEED_PREFIX.length()));
            }
        }
        return new Random().nextLong();
    }
}
