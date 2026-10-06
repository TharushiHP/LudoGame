package ludo.shared.protocol;

import ludo.shared.json.JsonObjects;
import ludo.shared.snapshot.GameSnapshot;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * STATE event: the full authoritative state at {@code version}, its hash, and the game log lines
 * produced since the previous STATE (Value Object). Clients render it and reply with ACK, except
 * for the final STATE of a game (snapshot status no longer IN_PROGRESS), which needs no ACK.
 */
public record StateEvent(long version, GameSnapshot snapshot, String hash, List<String> log) implements ServerEvent {

    public StateEvent {
        log = List.copyOf(log);
    }

    @Override
    public EventType type() {
        return EventType.STATE;
    }

    @Override
    public Map<String, Object> toJson() {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("version", version);
        json.put("hash", hash);
        json.put("snapshot", SnapshotCodec.toJson(snapshot));
        json.put("log", new ArrayList<Object>(log));
        return json;
    }

    public static StateEvent fromJson(Map<String, Object> json) {
        List<String> log = new ArrayList<>();
        for (Object line : JsonObjects.getList(json, "log"))
            log.add(String.valueOf(line));
        return new StateEvent(JsonObjects.getLong(json, "version"),
                SnapshotCodec.fromJson(JsonObjects.getObject(json, "snapshot")),
                JsonObjects.getString(json, "hash"), log);
    }
}
