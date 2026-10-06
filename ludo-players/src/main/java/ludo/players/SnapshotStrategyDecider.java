package ludo.players;

import ludo.shared.PlayerColor;
import ludo.shared.decision.MoveDecider;
import ludo.shared.decision.PieceChoice;
import ludo.shared.snapshot.GameSnapshot;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Makes the four {@link MoveStrategy} behaviours available through the game's {@link MoveDecider}
 * port (Adapter pattern), one strategy per colour from the {@link StrategyFactory}.
 * Every decision is made from the {@link GameSnapshot} alone, so the same class can later run
 * inside each client. It holds no game state: Blue's cycle position travels in the snapshot memo.
 */
public class SnapshotStrategyDecider implements MoveDecider {

    private final Map<PlayerColor, MoveStrategy> strategies = new EnumMap<>(PlayerColor.class);

    public SnapshotStrategyDecider() {
        StrategyFactory factory = new StrategyFactory();
        for (PlayerColor color : PlayerColor.values()) {
            strategies.put(color, factory.create(color));
        }
    }

    @Override
    public PieceChoice choosePiece(GameSnapshot snapshot, PlayerColor color, int roll, List<Integer> candidatePieceIds) {
        return strategies.get(color).choosePiece(snapshot, roll, candidatePieceIds);
    }

    @Override
    public boolean prefersMoveFromBase(GameSnapshot snapshot, PlayerColor color) {
        return strategies.get(color).prefersMoveFromBase(snapshot);
    }

    @Override
    public boolean triesOtherPiecesWhenBlocked(PlayerColor color) {
        return strategies.get(color).triesOtherPiecesWhenBlocked();
    }
}
