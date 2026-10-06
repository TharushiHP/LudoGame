package ludo.server.coordinator.state;

import ludo.shared.protocol.JoinRequest;

/**
 * State before the game starts: only JOIN is accepted, once per colour.
 * The coordinator leaves this state when all four colours have joined.
 */
public final class WaitingForPlayers implements CoordinatorState {

    @Override
    public String name() {
        return "WaitingForPlayers";
    }

    @Override
    public Reply onJoin(JoinRequest request, CoordinatorContext ctx) {
        if (ctx.isSeatTaken(request.colour()))
            return Reply.conflict(request.colour() + " has already joined this game");
        int joined = ctx.takeSeat(request);
        return Reply.okWith("colour", request.colour().name(), "joined", joined);
    }
}
