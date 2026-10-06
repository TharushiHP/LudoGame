package ludo.architecture;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reads a module's Java source files for the architecture tests. Maven runs each module's tests
 * with the module folder as working directory, so paths are relative to ludo-core.
 */
final class SourceTree {

    static final Path CORE = Path.of("src", "main", "java");
    static final Path SHARED = Path.of("..", "ludo-shared", "src", "main", "java");

    private static final Pattern PACKAGE = Pattern.compile("^package\\s+([\\w.]+)\\s*;", Pattern.MULTILINE);
    private static final Pattern BLOCK_COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
    private static final Pattern LINE_COMMENT = Pattern.compile("//[^\\n]*");

    private SourceTree() {}

    /** Every .java file under the root; fails if there are none, so a wrong path cannot pass. */
    static List<Path> javaFiles(Path root) {
        assertTrue(Files.isDirectory(root), "source folder not found: " + root.toAbsolutePath());
        try (Stream<Path> files = Files.walk(root)) {
            List<Path> found = files.filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList());
            assertFalse(found.isEmpty(), "no Java files under " + root.toAbsolutePath());
            return found;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static String packageOf(String source) {
        Matcher m = PACKAGE.matcher(source);
        return m.find() ? m.group(1) : "";
    }

    /** The source with comments removed, so only real code (imports and names) is checked. */
    static String code(String source) {
        return LINE_COMMENT.matcher(BLOCK_COMMENT.matcher(source).replaceAll(" ")).replaceAll(" ");
    }
}
