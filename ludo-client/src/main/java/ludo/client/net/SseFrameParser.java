package ludo.client.net;

/**
 * Turns the lines of a text/event-stream into {@link SseFrame}s, following the Server-Sent Events
 * format: {@code field: value} lines build up a frame, and a blank line ends it.
 * <ul>
 *   <li>Several {@code data:} lines are joined with '\n'.</li>
 *   <li>Lines starting with ':' are comments (the server's keep-alives) and are ignored.</li>
 *   <li>{@code id:} is remembered across frames: a reconnect sends it back as Last-Event-ID.</li>
 *   <li>The space after the colon is optional; {@code retry:} and unknown fields are ignored.</li>
 * </ul>
 * Pure and used by one thread only (one parser per stream), so it is tested without a network.
 */
public final class SseFrameParser {

    private String lastEventId;
    private String event;
    private final StringBuilder data = new StringBuilder();
    private boolean hasData;

    /** Feeds one line (without its line ending). Returns the finished frame on a blank line, else null. */
    public SseFrame accept(String line) {
        if (line.isEmpty())
            return dispatch();
        if (line.startsWith(":"))
            return null;
        int colon = line.indexOf(':');
        String field = colon < 0 ? line : line.substring(0, colon);
        String value = colon < 0 ? "" : line.substring(colon + 1);
        if (value.startsWith(" "))
            value = value.substring(1);
        switch (field) {
            case "event" -> event = value;
            case "data" -> {
                if (hasData)
                    data.append('\n');
                data.append(value);
                hasData = true;
            }
            case "id" -> {
                if (value.indexOf('\0') < 0)
                    lastEventId = value;
            }
            default -> { } // "retry" and unknown fields: nothing to do
        }
        return null;
    }

    /** The last event id received, or null: what a reconnect sends as Last-Event-ID. */
    public String lastEventId() {
        return lastEventId;
    }

    private SseFrame dispatch() {
        SseFrame frame = hasData ? new SseFrame(lastEventId, event == null ? "message" : event, data.toString()) : null;
        event = null;
        data.setLength(0);
        hasData = false;
        return frame;
    }
}
