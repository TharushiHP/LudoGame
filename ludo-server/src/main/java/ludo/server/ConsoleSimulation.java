package ludo.server;

import ludo.game.Game;
import ludo.game.GameBuilder;
import ludo.output.GameLogger;
import ludo.players.SnapshotStrategyDecider;

import java.util.Random;

/**
 * Console entry point and composition root (formerly A1's Main): creates the console listener and
 * the snapshot strategies (ludo-players), wires them into the
 * game through the GameBuilder (Dependency Injection) and runs one simulation.
 * Optional argument {@code --seed=<number>} replays a game exactly; without it a random
 * seed is chosen and printed so the game can still be replayed.
 */
public class ConsoleSimulation {

    private static final String SEED_PREFIX = "--seed=";

    public static void main(String[] args) {
        long seed;
        try {
            seed = parseSeed(args);
        } catch (NumberFormatException e) {
            System.err.println("Invalid seed. Usage: java -jar ludo-server.jar [--seed=<number>]");
            System.exit(1);
            return;
        }
        GameLogger console = new GameLogger();
        console.log("Seed: " + seed + " (replay this game with " + SEED_PREFIX + seed + ")");
        Game game = new GameBuilder()
                .withSeed(seed)
                .withMoveDecider(new SnapshotStrategyDecider())
                .withListener(console)
                .build();
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
