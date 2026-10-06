package ludo.shared.protocol;

import java.util.Map;

/**
 * An event the server pushes to clients (sealed: the seven protocol events are the only ones).
 * Each record converts itself to JSON; {@link #fromJson} turns received JSON back into a record.
 */
public sealed interface ServerEvent
        permits StateEvent, RollRequest, DecisionRequest, PausedEvent, ResumedEvent, GameOverEvent, NewGameEvent {

    EventType type();

    Map<String, Object> toJson();

    static ServerEvent fromJson(EventType type, Map<String, Object> json) {
        return switch (type) {
            case STATE -> StateEvent.fromJson(json);
            case ROLL_REQUEST -> RollRequest.fromJson(json);
            case DECISION_REQUEST -> DecisionRequest.fromJson(json);
            case PAUSED -> PausedEvent.fromJson(json);
            case RESUMED -> ResumedEvent.fromJson(json);
            case GAME_OVER -> GameOverEvent.fromJson(json);
            case NEW_GAME -> NewGameEvent.fromJson(json);
        };
    }
}
