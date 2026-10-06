package ludo.client.gui;

import ludo.shared.EffectKind;
import ludo.shared.PlayerColor;

import java.awt.Color;
import java.awt.Font;

/**
 * The GUI's colours, fonts and short descriptions in one place, so every panel shows a player in
 * the same colour (Single Source of Truth for the look).
 */
public final class Palette {

    public static final Color BOARD_BACKGROUND = new Color(250, 250, 247);
    public static final Color GRID_LINE = new Color(120, 120, 120);
    public static final Color SPECIAL_CELL = new Color(222, 222, 222);
    public static final Color MYSTERY = new Color(142, 68, 173);
    public static final Color HOVER = new Color(0, 0, 0, 40);
    public static final Color OK = new Color(30, 140, 60);
    public static final Color BAD = new Color(200, 40, 40);
    public static final Color PAUSE = new Color(255, 214, 90);
    public static final Color GAME_OVER = new Color(200, 235, 200);

    public static final Font BASE = new Font(Font.SANS_SERIF, Font.PLAIN, 14);
    public static final Font BOLD = BASE.deriveFont(Font.BOLD);
    public static final Font TITLE = new Font(Font.SANS_SERIF, Font.BOLD, 20);
    public static final Font LOG = new Font(Font.MONOSPACED, Font.PLAIN, 13);

    private Palette() {}

    /** The strong colour of a player: pieces, start cell, titles. */
    public static Color of(PlayerColor colour) {
        return switch (colour) {
            case RED -> new Color(214, 48, 49);
            case GREEN -> new Color(39, 155, 72);
            case YELLOW -> new Color(232, 186, 0);
            case BLUE -> new Color(41, 98, 205);
        };
    }

    /** A light tint of the player's colour: bases and home straights. */
    public static Color light(PlayerColor colour) {
        Color c = of(colour);
        return new Color(mix(c.getRed()), mix(c.getGreen()), mix(c.getBlue()));
    }

    /** Text colour that is readable on {@link #of(PlayerColor)}. */
    public static Color textOn(PlayerColor colour) {
        return colour == PlayerColor.YELLOW ? Color.BLACK : Color.WHITE;
    }

    /** Each player's behaviour, as in the brief. */
    public static String role(PlayerColor colour) {
        return switch (colour) {
            case RED -> "aggressive capturer";
            case GREEN -> "blocker";
            case YELLOW -> "speedrunner";
            case BLUE -> "cyclic, mystery-cell focused";
        };
    }

    public static String effect(EffectKind kind) {
        return switch (kind) {
            case NONE -> "";
            case ENERGIZED -> "energised";
            case SICK -> "sick";
            case BRIEFING -> "briefing";
        };
    }

    /** 1 -> "1st", 2 -> "2nd", ... */
    public static String ordinal(int place) {
        return place + switch (place) {
            case 1 -> "st";
            case 2 -> "nd";
            case 3 -> "rd";
            default -> "th";
        };
    }

    private static int mix(int channel) {
        return channel + (255 - channel) * 62 / 100;
    }
}
