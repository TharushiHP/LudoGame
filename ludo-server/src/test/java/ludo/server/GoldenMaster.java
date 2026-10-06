package ludo.server;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Golden-master support: captures the full console output of one seeded game, exactly as
 * {@link ConsoleSimulation} prints it. Run {@link #main} to (re)generate the files in
 * src/test/resources/golden; only do that when a behaviour change is intended.
 * Line separators are normalised to "\n" so the files compare the same on every OS.
 */
final class GoldenMaster {

    static final int SEEDS = 20;
    private static final Path GOLDEN_DIR = Path.of("src", "test", "resources", "golden");

    private GoldenMaster() {}

    static String fileName(long seed) {
        return "seed-" + seed + ".txt";
    }

    static byte[] capture(long seed) {
        PrintStream original = System.out;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        System.setOut(new PrintStream(buffer, true, StandardCharsets.UTF_8));
        try {
            ConsoleSimulation.main(new String[] {"--seed=" + seed});
        } finally {
            System.setOut(original);
        }
        String output = buffer.toString(StandardCharsets.UTF_8).replace(System.lineSeparator(), "\n");
        return output.getBytes(StandardCharsets.UTF_8);
    }

    public static void main(String[] args) throws IOException {
        Files.createDirectories(GOLDEN_DIR);
        for (long seed = 1; seed <= SEEDS; seed++) {
            Files.write(GOLDEN_DIR.resolve(fileName(seed)), capture(seed));
        }
        System.out.println("Wrote " + SEEDS + " golden files to " + GOLDEN_DIR.toAbsolutePath());
    }
}
