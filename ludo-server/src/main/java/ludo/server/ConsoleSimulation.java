package ludo.server;

import ludo.game.Game;
import ludo.game.GameBuilder;
import ludo.output.GameLogger;
import ludo.players.SnapshotStrategyDecider;

import java.util.Random;


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
