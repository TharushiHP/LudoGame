package ludo;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.InputStream;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Golden-master (characterisation) test: the console output of seeds 1-20 must stay
 * byte-identical to the files recorded before the Task 4 refactor. Proves a refactor
 * changed no game behaviour.
 */
class GoldenMasterTest {

    static LongStream seeds() {
        return LongStream.rangeClosed(1, GoldenMaster.SEEDS);
    }

    @ParameterizedTest(name = "seed {0}")
    @MethodSource("seeds")
    void outputMatchesGoldenFile(long seed) throws IOException {
        byte[] expected = golden(seed);
        byte[] actual = GoldenMaster.capture(seed);
        assertArrayEquals(expected, actual, "console output differs from " + GoldenMaster.fileName(seed));
    }

    static byte[] golden(long seed) throws IOException {
        try (InputStream in = GoldenMasterTest.class.getResourceAsStream("/golden/" + GoldenMaster.fileName(seed))) {
            assertNotNull(in, "missing golden file " + GoldenMaster.fileName(seed));
            return in.readAllBytes();
        }
    }
}
