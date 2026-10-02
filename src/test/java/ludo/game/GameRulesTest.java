package ludo.game;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Whole-game rule checks. Each test plays complete seeded games and inspects
 * the events the Game publishes to an observer.
 */
class GameRulesTest {

    private static final int SEEDS = 20;

    private static final Pattern PLAYER_WIN = Pattern.compile("^(Red|Green|Yellow|Blue) player wins!!!$");
    private static final Pattern PLAYER_ROLL = Pattern.compile("^(Red|Green|Yellow|Blue) player rolled \\d+\\.$");
    private static final Pattern MYSTERY_SPAWN =
            Pattern.compile("^A mystery cell has spawned in location (\\d+)");
    private static final Pattern MYSTERY_STATUS =
            Pattern.compile("^The mystery cell is at (\\d+) and will be at that location for the next (\\d+) ");

    private PrintStream originalOut;

    // The console logger prints every event; silence it so many games stay fast.
    @BeforeEach
    void muteConsole() {
        originalOut = System.out;
        System.setOut(new PrintStream(OutputStream.nullOutputStream()));
    }

    @AfterEach
    void restoreConsole() {
        System.setOut(originalOut);
    }

    // Rule 11: the game ends as soon as only one player still has pieces out.
    // Games that hit the safety cap (see roundCapPrintsWarning...) are skipped here.
    @Test
    void gameEndsAsSoonAsOnlyOnePlayerIsLeft_andRanksAllFourPlayers() {
        int gamesChecked = 0;
        for (long seed = 1; seed <= SEEDS; seed++) {
            List<String> messages = play(seed).messages;
            if (messages.stream().anyMatch(m -> m.startsWith("WARNING"))) {
                continue;
            }
            gamesChecked++;

            assertTrue(messages.stream().anyMatch(m -> m.startsWith("4th place:")),
                    "seed " + seed + ": no 4th place in the GAME OVER summary");

            int thirdWin = indexOfNthMatch(messages, PLAYER_WIN, 3);
            assertTrue(thirdWin >= 0, "seed " + seed + ": fewer than three players finished");
            assertTrue(messages.subList(thirdWin + 1, messages.size()).stream()
                            .noneMatch(m -> PLAYER_ROLL.matcher(m).matches()),
                    "seed " + seed + ": dice were still rolled after the third player finished");
        }
        assertTrue(gamesChecked >= SEEDS / 2, "only " + gamesChecked + " games finished normally");
    }

    // Every game must end normally or by stalemate; the round cap is only a safety net.
    @Test
    void noGameReachesTheSafetyCap() {
        for (long seed = 1; seed <= SEEDS; seed++) {
            assertTrue(play(seed).messages.stream().noneMatch(m -> m.startsWith("WARNING")),
                    "seed " + seed + " reached the round cap");
        }
    }

    // A tiny cap forces the safety net to trigger.
    @Test
    void roundCapPrintsWarningAndLeavesUnfinishedPlayersUnranked() {
        Game game = new GameBuilder().withSeed(1).withMaxRounds(5).build();
        RecordingListener recorded = listen(game);
        game.run();

        assertTrue(game.isRoundCapReached());
        assertTrue(recorded.messages.stream().anyMatch(m -> m.startsWith("WARNING: the safety limit of 5 rounds")));
        assertTrue(recorded.messages.stream().anyMatch(m -> m.startsWith("Not ranked:")));
    }

    // Stalemate rule: a tiny threshold forces it to trigger early in a real game.
    @Test
    void stalemateEndsTheGameAndRanksEveryRemainingPlayer() {
        Game game = new GameBuilder().withSeed(1).withStalemateRounds(3).build();
        RecordingListener recorded = listen(game);
        game.run();

        assertTrue(recorded.messages.contains("No progress for 3 rounds: the game is declared a stalemate."));
        assertTrue(recorded.messages.stream().anyMatch(m -> m.startsWith("4th place:")));
        assertTrue(recorded.messages.stream().noneMatch(m -> m.startsWith("WARNING") || m.startsWith("Not ranked")));
        assertFalse(game.isRoundCapReached());
    }

    @Test
    void stalemateRanksByPiecesHomeThenByCellsLeft() {
        Game game = new GameBuilder().withSeed(1).build();
        RecordingListener recorded = listen(game);
        sendHome(game, ludo.board.PlayerColor.BLUE, 3);
        sendHome(game, ludo.board.PlayerColor.RED, 2);
        sendHome(game, ludo.board.PlayerColor.YELLOW, 2);
        sendHome(game, ludo.board.PlayerColor.GREEN, 1);
        // Red's third piece is on its home straight, Yellow's pieces are all still at base
        game.playerOf(ludo.board.PlayerColor.RED).getPieces().get(2).moveToHomePath(3);

        game.declareStalemate();

        assertEquals(1, game.playerOf(ludo.board.PlayerColor.BLUE).getFinishPosition());
        assertEquals(2, game.playerOf(ludo.board.PlayerColor.RED).getFinishPosition());
        assertEquals(3, game.playerOf(ludo.board.PlayerColor.YELLOW).getFinishPosition());
        assertEquals(4, game.playerOf(ludo.board.PlayerColor.GREEN).getFinishPosition());
        assertTrue(recorded.messages.contains("Red player takes 2nd place (2 pieces Home, 59 cells left)."));
    }

    // Observer pattern: each event must carry its own type, not always DICE_ROLLED.
    @Test
    void publishedEventsCarryTheirRealType() {
        RecordingListener recorded = play(1);
        Set<GameEvent> types = EnumSet.copyOf(recorded.events);

        assertTrue(types.containsAll(EnumSet.of(
                        GameEvent.GAME_START,
                        GameEvent.FIRST_PLAYER_CHOSEN,
                        GameEvent.DICE_ROLLED,
                        GameEvent.PIECE_MOVED_TO_START,
                        GameEvent.PIECE_MOVED,
                        GameEvent.ROUND_STATUS,
                        GameEvent.PLAYER_WINS)),
                "event types seen: " + types);

        for (int i = 0; i < recorded.events.size(); i++) {
            if (recorded.events.get(i) == GameEvent.DICE_ROLLED) {
                assertTrue(recorded.messages.get(i).contains("roll"),
                        "DICE_ROLLED event with unrelated message: " + recorded.messages.get(i));
            }
        }
    }

    // Rule T-1: the coin toss decides the direction when a piece leaves the base.
    @Test
    void coinTossDirectionIsPrintedWhenPieceLeavesBase() {
        List<String> messages = play(1).messages;
        int piecesLeavingBase = 0;

        for (String message : messages) {
            if (message.contains("to the starting point")) {
                piecesLeavingBase++;
                assertTrue(message.contains("clockwise"),
                        "no coin toss direction in: " + message);
            }
        }
        assertTrue(piecesLeavingBase > 0);
    }

    // The mystery cell status line must count down the rounds really left at that location.
    @Test
    void mysteryCellStatusCountsDownRealRoundsRemaining() {
        for (long seed = 1; seed <= SEEDS; seed++) {
            int position = -1;
            int expected = 0;
            int statusLinesChecked = 0;

            for (String message : play(seed).messages) {
                Matcher spawn = MYSTERY_SPAWN.matcher(message);
                Matcher status = MYSTERY_STATUS.matcher(message);
                if (spawn.find()) {
                    position = Integer.parseInt(spawn.group(1));
                    expected = ludo.board.BoardConstants.MYSTERY_DURATION_ROUNDS;
                } else if (status.find() && Integer.parseInt(status.group(1)) == position) {
                    expected--;
                    assertEquals(expected, Integer.parseInt(status.group(2)),
                            "seed " + seed + ": wrong rounds remaining in: " + message);
                    statusLinesChecked++;
                }
            }
            assertTrue(statusLinesChecked > 0, "seed " + seed + ": no mystery status lines");
        }
    }

    // Same seed, same game: needed to replay and compare runs.
    @Test
    void sameSeedReplaysTheSameGame() {
        assertEquals(playWithSeed(7).messages, playWithSeed(7).messages);
        assertNotEquals(playWithSeed(7).messages, playWithSeed(8).messages);
    }

    @Test
    void gameReportsRoundsAndTurnsAndDoesNotHitTheCap() {
        Game game = new GameBuilder().withSeed(3).build();
        game.run();
        assertFalse(game.isRoundCapReached());
        assertTrue(game.getRoundNumber() > 0);
        assertTrue(game.getTurnCount() >= game.getRoundNumber());
    }

    // Helpers

    private RecordingListener listen(Game game) {
        RecordingListener listener = new RecordingListener();
        game.addObserver(listener);
        return listener;
    }

    private void sendHome(Game game, ludo.board.PlayerColor color, int count) {
        for (int i = 0; i < count; i++) {
            game.playerOf(color).getPieces().get(i).reachHome();
        }
    }

    private RecordingListener playWithSeed(long seed) {
        Game game = new GameBuilder().withSeed(seed).build();
        RecordingListener listener = new RecordingListener();
        game.addObserver(listener);
        game.run();
        return listener;
    }

    private RecordingListener play(long seed) {
        Random random = new Random(seed);
        Game game = new GameBuilder().withRandomSource(random::nextInt).build();
        RecordingListener listener = new RecordingListener();
        game.addObserver(listener);
        game.run();
        return listener;
    }

    private int indexOfNthMatch(List<String> messages, Pattern pattern, int n) {
        int found = 0;
        for (int i = 0; i < messages.size(); i++) {
            if (pattern.matcher(messages.get(i)).matches() && ++found == n) {
                return i;
            }
        }
        return -1;
    }

    private static final class RecordingListener implements GameEventListener {
        private final List<GameEvent> events = new ArrayList<>();
        private final List<String> messages = new ArrayList<>();

        @Override
        public void onEvent(GameEvent event, String message) {
            events.add(event);
            messages.add(message.trim());
        }
    }
}
