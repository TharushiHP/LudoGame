package ludo.shared.protocol;

import java.util.Map;

/** A request a client sends to the server (sealed: JOIN, ROLL, DECISION and ACK are the only ones). */
public sealed interface ClientRequest permits JoinRequest, RollCommand, DecisionReply, AckCommand {

    RequestType type();

    Map<String, Object> toJson();
}
