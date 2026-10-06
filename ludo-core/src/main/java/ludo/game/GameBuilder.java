package ludo.game;

import ludo.shared.decision.MoveDecider;
import ludo.shared.PlayerColor;
import ludo.board.Board;
import ludo.dice.Coin;
import ludo.dice.Dice;
import ludo.dice.RandomSource;
import ludo.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * Builds a {@link Game} and wires every collaborator into it (Builder pattern; Dependency
 * Injection / composition root for the game): random source, board, dice, coin, the four
 * players, the {@link MoveDecider}, the {@link TurnGate} and the event listeners.
 * A {@link MoveDecider} is required: the core module does not know any player behaviour, so the
 * caller (console simulation, server or test) supplies one. Defaults: {@link NoOpTurnGate}, no listeners,
 * {@link EndCondition#ALL_PLACES}.
 * A fixed seed makes a game replay exactly.
 */
public class GameBuilder {

    private RandomSource randomSource;
    private MoveDecider moveDecider;
    private TurnGate turnGate = new NoOpTurnGate();
    private final List<GameEventListener> listeners = new ArrayList<>();
    private int maxRounds = Game.DEFAULT_MAX_ROUNDS;
    private int stalemateRounds = Game.DEFAULT_STALEMATE_ROUNDS;
    private EndCondition endCondition = EndCondition.ALL_PLACES;

    public GameBuilder withRandomSource(RandomSource source) {
        this.randomSource = source;
        return this;
    }

    public GameBuilder withSeed(long seed) {
        this.randomSource = new JavaRandom(seed);
        return this;
    }

    /** Required: who chooses the pieces (e.g. the snapshot strategies, or a remote-client decider). */
    public GameBuilder withMoveDecider(MoveDecider decider) {
        this.moveDecider = decider;
        return this;
    }

    public GameBuilder withTurnGate(TurnGate gate) {
        this.turnGate = gate;
        return this;
    }

    public GameBuilder withListener(GameEventListener listener) {
        this.listeners.add(listener);
        return this;
    }

    public GameBuilder withMaxRounds(int rounds) {
        this.maxRounds = rounds;
        return this;
    }

    public GameBuilder withStalemateRounds(int rounds) {
        this.stalemateRounds = rounds;
        return this;
    }

    /** When the game ends (Rule 11); default {@link EndCondition#ALL_PLACES}, the A1 behaviour. */
    public GameBuilder withEndCondition(EndCondition condition) {
        this.endCondition = Objects.requireNonNull(condition, "endCondition");
        return this;
    }

    public Game build() {
        RandomSource source = randomSource != null ? randomSource : new JavaRandom();
        Board board = new Board(source);
        Dice dice = new Dice(source);
        Coin coin = new Coin(source);
        List<Player> players = createPlayers();
        MoveDecider decider = Objects.requireNonNull(moveDecider, "GameBuilder needs a MoveDecider: call withMoveDecider(...)");
        return new Game(board, dice, coin, players, decider, turnGate, listeners, maxRounds, stalemateRounds, endCondition);
    }

    // The order Yellow, Blue, Red, Green is the A1 order used for the starting roll-off.
    private List<Player> createPlayers() {
        return List.of(
                new Player(PlayerColor.YELLOW),
                new Player(PlayerColor.BLUE),
                new Player(PlayerColor.RED),
                new Player(PlayerColor.GREEN));
    }

    private static class JavaRandom implements RandomSource {
        private final Random random;

        JavaRandom() {
            this.random = new Random();
        }

        JavaRandom(long seed) {
            this.random = new Random(seed);
        }

        @Override
        public int nextInt(int bound) {
            return random.nextInt(bound);
        }
    }
}
