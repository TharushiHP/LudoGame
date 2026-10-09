package ludo.output;

import ludo.game.GameEvent;
import ludo.game.GameEventListener;


public class GameLogger implements GameEventListener {

    @Override
    public void onEvent(GameEvent event, String message) {
        System.out.println(message);
    }

    public void log(String message) {
        System.out.println(message);
    }
}
