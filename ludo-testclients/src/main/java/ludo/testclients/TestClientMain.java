package ludo.testclients;

import ludo.testclients.report.ScenarioResult;
import ludo.testclients.report.SummaryReport;
import ludo.testclients.scenario.BurstScenario;
import ludo.testclients.scenario.CreateScenario;
import ludo.testclients.scenario.PlayScenario;
import ludo.testclients.scenario.Scenario;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;


public final class TestClientMain {

    private TestClientMain() {}

    public static void main(String[] args) throws InterruptedException {
        TestClientOptions options;
        try {
            options = TestClientOptions.parse(args);
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            System.err.println(TestClientOptions.USAGE);
            System.exit(2);
            return;
        }
        boolean allPassed = true;
        for (Scenario scenario : scenarios(options.scenario())) {
            System.out.println("Running scenario " + scenario.name() + " against " + options.server() + " ...");
            ScenarioResult result = scenario.run(options);
            System.out.println(SummaryReport.render(result));
            try {
                Path file = SummaryReport.write(result, options.outDir());
                System.out.println("Summary saved to " + file.toAbsolutePath());
            } catch (IOException e) {
                System.err.println("Could not save the summary: " + e.getMessage());
            }
            System.out.println();
            allPassed &= result.passed();
        }
        System.exit(allPassed ? 0 : 1);
    }

    static List<Scenario> scenarios(String name) {
        return switch (name) {
            case "play" -> List.of(new PlayScenario());
            case "burst" -> List.of(new BurstScenario());
            case "create" -> List.of(new CreateScenario());
            default -> List.of(new BurstScenario(), new PlayScenario(), new CreateScenario());
        };
    }
}
