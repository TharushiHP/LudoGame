package ludo.client.net;

import java.util.Map;

/**
 * The server's answer to one HTTP request: status code and JSON body (Value Object).
 * Error replies from the server carry {@code {"error": reason}}.
 */
public record GatewayReply(int status, Map<String, Object> body) {

    public GatewayReply {
        body = Map.copyOf(body);
    }

    public boolean isSuccess() {
        return status >= 200 && status < 300;
    }

    /** Status and the server's reason, e.g. "409 stale ACK: ...", for messages on screen. */
    public String error() {
        Object reason = body.get("error");
        return status + " " + (reason != null ? reason : body);
    }
}
