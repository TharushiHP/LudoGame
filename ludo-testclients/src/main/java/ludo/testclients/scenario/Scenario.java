package ludo.testclients.scenario;

import ludo.testclients.TestClientOptions;
import ludo.testclients.report.ScenarioResult;


public interface Scenario {

    /** "play", "burst" or "create": used in the summary and its file name. */
    String name();

    /** Runs against {@code options.server()} and returns the numbers and checks; never throws for a failed check. */
    ScenarioResult run(TestClientOptions options) throws InterruptedException;
}
