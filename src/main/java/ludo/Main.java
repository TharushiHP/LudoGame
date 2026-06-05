package ludo;

import ludo.game.Game;
import ludo.game.GameBuilder;

public class Main {

    public static void main(String[] args) {
        Game game = new GameBuilder().build();
        game.run();
    }
}
