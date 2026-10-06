package ludo.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Acyclic Dependencies Principle for ludo-core: the package dependency graph must have no cycles,
 * so each package can be understood, tested and changed starting from the ones it depends on.
 * Edges come from imports and fully qualified names in code (comments are ignored).
 */
class PackageCycleTest {

    // A ludo.* package name followed by a class name or a wildcard: ludo.board.Board, ludo.board.*
    private static final Pattern REFERENCE = Pattern.compile("\\b(ludo(?:\\.[a-z]\\w*)+)\\.(?:[A-Z]|\\*)");

    @Test
    void corePackagesHaveNoCycles() {
        Map<String, Set<String>> graph = dependencyGraph(SourceTree.CORE);

        // Guards against a scanner that finds nothing: this edge certainly exists.
        assertTrue(graph.get("ludo.game").contains("ludo.board"), "expected ludo.game -> ludo.board in " + graph);

        List<String> cycle = findCycle(graph);
        assertTrue(cycle.isEmpty(), "Package cycle in ludo-core: " + String.join(" -> ", cycle)
                + "\nGraph: " + graph);
    }

    @Test
    void cycleFinderDetectsACycle() {
        Map<String, Set<String>> graph = new TreeMap<>();
        graph.put("a", Set.of("b"));
        graph.put("b", Set.of("c"));
        graph.put("c", Set.of("a"));
        assertEquals(List.of("a", "b", "c", "a"), findCycle(graph));
    }

    /** Package -> packages of the same source tree it uses (outside packages cannot lead back in). */
    private static Map<String, Set<String>> dependencyGraph(Path root) {
        Map<String, String> codeByPackageFile = new TreeMap<>();
        Map<String, Set<String>> graph = new TreeMap<>();
        for (Path file : SourceTree.javaFiles(root)) {
            String source = SourceTree.read(file);
            String pkg = SourceTree.packageOf(source);
            graph.putIfAbsent(pkg, new TreeSet<>());
            codeByPackageFile.put(pkg + " " + file, SourceTree.code(source));
        }
        codeByPackageFile.forEach((key, code) -> {
            String pkg = key.substring(0, key.indexOf(' '));
            Matcher m = REFERENCE.matcher(code);
            while (m.find()) {
                String used = m.group(1);
                if (!used.equals(pkg) && graph.containsKey(used))
                    graph.get(pkg).add(used);
            }
        });
        return graph;
    }

    /** Depth-first search; returns one cycle as a closed path (first == last), or empty if none. */
    private static List<String> findCycle(Map<String, Set<String>> graph) {
        Set<String> finished = new HashSet<>();
        for (String start : graph.keySet()) {
            List<String> cycle = visit(start, graph, new ArrayList<>(), finished);
            if (!cycle.isEmpty())
                return cycle;
        }
        return List.of();
    }

    private static List<String> visit(String pkg, Map<String, Set<String>> graph,
            List<String> path, Set<String> finished) {
        if (path.contains(pkg)) {
            List<String> cycle = new ArrayList<>(path.subList(path.indexOf(pkg), path.size()));
            cycle.add(pkg);
            return cycle;
        }
        if (finished.contains(pkg))
            return List.of();
        path.add(pkg);
        for (String next : graph.getOrDefault(pkg, Set.of())) {
            List<String> cycle = visit(next, graph, path, finished);
            if (!cycle.isEmpty())
                return cycle;
        }
        path.remove(path.size() - 1);
        finished.add(pkg);
        return List.of();
    }
}
