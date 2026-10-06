package ludo.client.gui;

import ludo.shared.PlayerColor;

import java.awt.Color;
import java.awt.Font;

/**
 * The game's colours and fonts in one place (Single Source of Truth for the look).
 * The board colours are the bright, saturated ones of Figure 1 in the brief; the tokens use a
 * deeper shade of the same colour so they stand out even on their own base.
 */
public final class Palette {

    /** Round the board: a calm table colour. */
    public static final Color TABLE = new Color(236, 239, 243);
    public static final Color TABLE_EDGE = new Color(214, 219, 226);
    public static final Color GRID_LINE = new Color(30, 30, 30);
    public static final Color MYSTERY = new Color(150, 60, 220);
    public static final Color TOAST = new Color(30, 34, 42, 225);
    public static final Color SHADOW = new Color(0, 0, 0, 60);

    public static final Font BASE = new Font(Font.SANS_SERIF, Font.PLAIN, 14);
    public static final Font BOLD = BASE.deriveFont(Font.BOLD);
    public static final Font TITLE = new Font(Font.SANS_SERIF, Font.BOLD, 22);
    public static final Font LOG = new Font(Font.MONOSPACED, Font.PLAIN, 12);
    /** "Base" and "Home" on the board are in a bold monospaced font, as in Figure 1. */
    public static final Font BOARD_LABEL = new Font(Font.MONOSPACED, Font.BOLD, 14);

    private Palette() {}

    /** The board colour of a player (bases, X cells, home straights, Home triangles): Figure 1. */
    public static Color board(PlayerColor colour) {
        return switch (colour) {
            case RED -> new Color(255, 18, 18);
            case GREEN -> new Color(0, 225, 0);
            case YELLOW -> new Color(255, 236, 0);
            case BLUE -> new Color(16, 32, 255);
        };
    }

    /** A token's fill: a deeper shade, so a token is visible on its own base. */
    public static Color token(PlayerColor colour) {
        return switch (colour) {
            case RED -> new Color(205, 20, 30);
            case GREEN -> new Color(10, 150, 40);
            case YELLOW -> new Color(240, 180, 0);
            case BLUE -> new Color(20, 60, 200);
        };
    }

    /** The colour used for text and outlines about a player (banners, player boxes). */
    public static Color of(PlayerColor colour) {
        return token(colour);
    }

    /** A light tint of the player's colour: the approach circle, banner backgrounds. */
    public static Color light(PlayerColor colour) {
        return mix(board(colour), Color.WHITE, 0.45);
    }

    /** Text colour that is readable on {@link #token(PlayerColor)}. */
    public static Color textOn(PlayerColor colour) {
        return colour == PlayerColor.YELLOW ? Color.BLACK : Color.WHITE;
    }

    /** {@code a} moved towards {@code b} by {@code amount} (0 = a, 1 = b). */
    public static Color mix(Color a, Color b, double amount) {
        return new Color(channel(a.getRed(), b.getRed(), amount), channel(a.getGreen(), b.getGreen(), amount),
                channel(a.getBlue(), b.getBlue(), amount), channel(a.getAlpha(), b.getAlpha(), amount));
    }

    public static Color withAlpha(Color c, double alpha) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), (int) Math.round(Math.max(0, Math.min(1, alpha)) * 255));
    }

    private static int channel(int a, int b, double amount) {
        return (int) Math.round(a + (b - a) * amount);
    }
}
