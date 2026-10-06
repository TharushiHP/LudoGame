package ludo.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Clean Architecture dependency rule, checked on every build: the game rules (ludo-core) and the
 * shared types (ludo-shared) must not mention networking, GUI or database classes. Those belong to
 * the outer tiers (server, client, database), which depend on the core, never the other way round.
 */
class DependencyRuleTest {

    // java.net also covers java.net.http; com.sun.net covers com.sun.net.httpserver.
    private static final Pattern FORBIDDEN =
            Pattern.compile("\\b(java\\.net|javax\\.swing|java\\.awt|com\\.sun\\.net|java\\.sql)\\b");

    @Test
    void coreUsesNoNetworkGuiOrDatabaseClasses() {
        assertNoForbiddenMentions(SourceTree.CORE);
    }

    @Test
    void sharedUsesNoNetworkGuiOrDatabaseClasses() {
        assertNoForbiddenMentions(SourceTree.SHARED);
    }

    @Test
    void checkerRecognisesForbiddenPackages() {
        // Guards against a pattern that silently matches nothing.
        assertTrue(FORBIDDEN.matcher("import java.net.http.HttpClient;").find());
        assertTrue(FORBIDDEN.matcher("import javax.swing.JFrame;").find());
        assertTrue(FORBIDDEN.matcher("import java.awt.Color;").find());
        assertTrue(FORBIDDEN.matcher("import com.sun.net.httpserver.HttpServer;").find());
        assertTrue(FORBIDDEN.matcher("java.sql.Connection connection;").find());
        assertFalse(FORBIDDEN.matcher("import java.util.List;").find());
    }

    // Whole file, comments included: "mentions" is deliberately strict.
    private static void assertNoForbiddenMentions(Path root) {
        List<String> violations = new ArrayList<>();
        for (Path file : SourceTree.javaFiles(root)) {
            String[] lines = SourceTree.read(file).split("\\R");
            for (int i = 0; i < lines.length; i++) {
                Matcher m = FORBIDDEN.matcher(lines[i]);
                if (m.find())
                    violations.add(root.relativize(file) + ":" + (i + 1) + " mentions " + m.group(1));
            }
        }
        assertTrue(violations.isEmpty(), "Dependency rule broken:\n" + String.join("\n", violations));
    }
}
