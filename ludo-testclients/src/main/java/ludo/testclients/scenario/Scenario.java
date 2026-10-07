package ludo.testclients.scenario;

import ludo.testclients.TestClientOptions;
import ludo.testclients.report.ScenarioResult;

/**
 * One way of loading the server with fast automatic clients, plus the checks that prove it stayed
 * consistent (Strategy pattern: {@code TestClientMain} runs whichever scenarios were asked for,
 * through this one interface).
 */
public interface Scenario {

    /** "play", "burst" or "create": used in the summary and its file name. */
    String name();

    /** Runs against {@code options.server()} and returns the numbers and checks; never throws for a failed check. */
    ScenarioResult run(TestClientOptions options) throws InterruptedException;
}
