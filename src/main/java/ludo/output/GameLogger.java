package ludo.output;

import ludo.game.GameEvent;
import ludo.game.GameEventListener;

/**
 * Console listener: prints every game event message to standard output
 * (Observer pattern, concrete observer). {@link ludo.Main} creates one and registers it through
 * the GameBuilder (Dependency Injection); the Game no longer registers it itself.
 * It was a lazy Singleton in A1; that was removed because nothing needs one shared instance
 * and the unsynchronised lazy initialisation was not thread-safe.
 */
public class GameLogger implements GameEventListener {

    @Override
    public void onEvent(GameEvent event, String message) {
        System.out.println(message);
    }

    public void log(String message) {
        System.out.println(message);
    }
}
