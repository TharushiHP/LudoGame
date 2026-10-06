package ludo.client.net;

/** State of the client's event stream, shown in the status bar. */
public enum ConnectionState {
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    CLOSED;

    public String display() {
        return switch (this) {
            case CONNECTING -> "Connecting...";
            case CONNECTED -> "Connected";
            case RECONNECTING -> "Reconnecting...";
            case CLOSED -> "Disconnected";
        };
    }
}
