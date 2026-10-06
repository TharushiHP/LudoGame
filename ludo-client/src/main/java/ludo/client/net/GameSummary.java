package ludo.client.net;

import ludo.shared.PlayerColor;
import ludo.shared.json.JsonObjects;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * One row of GET /games: a game's id, coordinator state, and how many and which colours have
 * joined (Value Object). The connect window offers only the colours that are not {@code taken}.
 */
public record GameSummary(String gameId, String state, long version, int joined, Set<PlayerColor> taken,
                          long seed, long turnDelayMs) {

    public GameSummary {
        Set<PlayerColor> copy = EnumSet.noneOf(PlayerColor.class);
        copy.addAll(taken);
        taken = Collections.unmodifiableSet(copy);
    }

    public boolean isTaken(PlayerColor colour) {
        return taken.contains(colour);
    }

    public static GameSummary fromJson(Map<String, Object> json) {
        Set<PlayerColor> taken = EnumSet.noneOf(PlayerColor.class);
        if (json.get("taken") != null)
            for (Object name : JsonObjects.getList(json, "taken"))
                taken.add(JsonObjects.toEnum(String.valueOf(name), "taken", PlayerColor.class));
        return new GameSummary(JsonObjects.getString(json, "gameId"), JsonObjects.getString(json, "state"),
                JsonObjects.getLong(json, "version"), JsonObjects.getInt(json, "joined"), taken,
                JsonObjects.getLong(json, "seed"), JsonObjects.getLong(json, "turnDelayMs"));
    }
}
