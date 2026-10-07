package ludo.testclients.report;

/**
 * One thing a scenario verifies, whether it held, and the numbers behind it (Value Object).
 * The summary prints each check as PASS or FAIL; the tests assert them.
 */
public record Check(String name, boolean passed, String detail) {

    public static Check of(String name, boolean passed, String detail) {
        return new Check(name, passed, detail);
    }
}
