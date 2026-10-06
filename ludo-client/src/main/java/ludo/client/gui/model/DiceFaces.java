package ludo.client.gui.model;

import ludo.shared.PlayerColor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The dice as pure data: where the pips of each face go, which faces a tumbling dice shows before
 * it lands on the rolled value, and which rolls a STATE's log reports. The tumble is only for
 * show; the value always comes from the server.
 */
public final class DiceFaces {

    /** A pip centre on a dice face of size 1 x 1 (0,0 top-left). */
    public record Pip(double x, double y) {
    }

    private static final double L = 0.27, M = 0.5, H = 0.73;

    private DiceFaces() {}

    public static List<Pip> pips(int value) {
        return switch (value) {
            case 1 -> List.of(p(M, M));
            case 2 -> List.of(p(L, L), p(H, H));
            case 3 -> List.of(p(L, L), p(M, M), p(H, H));
            case 4 -> List.of(p(L, L), p(H, L), p(L, H), p(H, H));
            case 5 -> List.of(p(L, L), p(H, L), p(M, M), p(L, H), p(H, H));
            case 6 -> List.of(p(L, L), p(H, L), p(L, M), p(H, M), p(L, H), p(H, H));
            default -> throw new IllegalArgumentException("a dice has no face " + value);
        };
    }

    /**
     * {@code frames} faces for a tumble that ends on {@code value}: the same seed always gives the
     * same faces, and no face shows twice in a row, so the dice visibly turns every frame.
     */
    public static int[] tumble(int value, int frames, long seed) {
        if (frames < 1)
            throw new IllegalArgumentException("frames must be at least 1");
        pips(value); // checks the value
        Random random = new Random(seed);
        int[] faces = new int[frames];
        faces[frames - 1] = value;
        for (int i = frames - 2; i >= 0; i--) {
            int face;
            do {
                face = 1 + random.nextInt(6);
            } while (face == faces[i + 1]);
            faces[i] = face;
        }
        return faces;
    }

    /**
     * The rolls in a STATE's log lines, in order: "Red player rolled 4." during the game and
     * "Yellow rolls 5" when the players roll for who begins. A colour that rolled twice keeps its last roll.
     */
    public static Map<PlayerColor, Integer> rollsIn(List<String> lines) {
        Map<PlayerColor, Integer> rolls = new LinkedHashMap<>();
        for (String line : lines) {
            Matcher m = ROLL.matcher(line.trim());
            if (m.matches()) {
                PlayerColor colour = PlayerColor.valueOf(m.group(1).toUpperCase(Locale.ROOT));
                rolls.remove(colour);
                rolls.put(colour, Integer.parseInt(m.group(2)));
            }
        }
        return rolls;
    }

    private static final Pattern ROLL = Pattern.compile("^(Red|Green|Yellow|Blue)(?: player rolled| rolls) ([1-6])\\.?$");

    private static Pip p(double x, double y) {
        return new Pip(x, y);
    }
}
