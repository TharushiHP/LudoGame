package ludo.game;

import ludo.players.SnapshotStrategyDecider;
import ludo.dice.RandomSource;
import org.junit.jupiter.api.Test;

import java.util.LinkedList;
import java.util.Queue;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GameIntegrationTest {

    @Test
    void gameRunsWithoutExceptionOnDeterministicSequence() {
        Queue<Integer> sequence = buildDeterministicSequence();
        RandomSource src = bound -> sequence.isEmpty() ? 0 : sequence.poll() % bound;

        Game game = new GameBuilder().withMoveDecider(new SnapshotStrategyDecider()).withRandomSource(src).build();

        assertDoesNotThrow(game::run);
    }

    @Test
    void gameBuilderCreatesRunableGame() {
        assertDoesNotThrow(() -> {
            Game game = new GameBuilder().withMoveDecider(new SnapshotStrategyDecider()).build();
            assertNotNull(game);
        });
    }

    // Communication-based: verify Game notifies its observer (GameEventListener).
    @Test
    void game_notifiesObserver_withDiceRolledEvent() {
        //Arrange
        GameEventListener mockListener = mock(GameEventListener.class);
        Queue<Integer> sequence = buildDeterministicSequence();
        RandomSource src = bound -> sequence.isEmpty() ? 0 : sequence.poll() % bound;
        Game game = new GameBuilder().withMoveDecider(new SnapshotStrategyDecider()).withRandomSource(src).build();
        game.addObserver(mockListener);

        //Act
        game.run();

        //Assert
        verify(mockListener, atLeastOnce())
                .onEvent(eq(GameEvent.DICE_ROLLED), anyString());
    }

    private Queue<Integer> buildDeterministicSequence() {
        Queue<Integer> q = new LinkedList<>();
        q.add(4); q.add(2); q.add(1); q.add(0); // roll-off: Yellow wins
        q.add(0);                                  // coin toss
        for (int round = 0; round < 200; round++) {
            q.add(5); q.add(0); q.add(4);          // six, coin, five
        }
        return q;
    }
}
