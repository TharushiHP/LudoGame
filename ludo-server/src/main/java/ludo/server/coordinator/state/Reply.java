package ludo.server.coordinator.state;

import java.util.LinkedHashMap;
import java.util.Map;


public record Reply(int status, Map<String, Object> body) {

    public Reply {
        body = new LinkedHashMap<>(body);
    }

    public boolean isSuccess() {
        return status >= 200 && status < 300;
    }

    public static Reply ok(Map<String, Object> body) {
        return new Reply(200, body);
    }

    /** 200 with a body built from alternating field names and values, kept in that order. */
    public static Reply okWith(Object... namesAndValues) {
        Map<String, Object> body = new LinkedHashMap<>();
        for (int i = 0; i + 1 < namesAndValues.length; i += 2)
            body.put((String) namesAndValues[i], namesAndValues[i + 1]);
        return ok(body);
    }

    /** 400: the request itself is malformed. */
    public static Reply badRequest(String reason) {
        return error(400, reason);
    }

    /** 404: no such game or path. */
    public static Reply notFound(String reason) {
        return error(404, reason);
    }

    /** 409: well-formed, but not valid now (wrong colour, wrong state, stale version). */
    public static Reply conflict(String reason) {
        return error(409, reason);
    }

    /** 503: the server cannot take the request now (queue full, shutting down). */
    public static Reply unavailable(String reason) {
        return error(503, reason);
    }

    /** 504: the game thread did not reply in time. */
    public static Reply timeout(String reason) {
        return error(504, reason);
    }

    private static Reply error(int status, String reason) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", reason);
        return new Reply(status, body);
    }

    public String summary() {
        return status + " " + (isSuccess() ? body : body.get("error"));
    }
}
