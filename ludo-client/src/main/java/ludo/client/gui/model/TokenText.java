package ludo.client.gui.model;

import ludo.shared.BoardConstants;
import ludo.shared.EffectKind;
import ludo.shared.PathMath;
import ludo.shared.PlayerColor;
import ludo.shared.snapshot.MysterySnapshot;
import ludo.shared.snapshot.PieceSnapshot;

import java.util.ArrayList;
import java.util.List;

/**
 * The hover texts of the board (pure, unit-tested): what a token is doing and what a cell means.
 * Special cells come from {@link BoardConstants} and {@link PathMath}, never typed in here.
 */
public final class TokenText {

    private TokenText() {}

    /** "R1", "B4": the piece names the game log uses. */
    public static String name(PieceSnapshot piece) {
        return piece.color().name().charAt(0) + String.valueOf(piece.number());
    }

    /** E.g. "R1 · cell 25 (Beta) · clockwise · 2 captures · energised, 3 rounds left". */
    public static String piece(PieceSnapshot piece) {
        List<String> parts = new ArrayList<>();
        parts.add(name(piece) + " (" + piece.color().display() + ")");
        parts.add(location(piece));
        if (piece.isActive())
            parts.add(piece.direction().display());
        if (!piece.isHome()) {
            parts.add(piece.captureCount() == 0 ? "no captures yet (cannot enter its home straight)"
                    : piece.captureCount() + (piece.captureCount() == 1 ? " capture" : " captures"));
        }
        if (piece.effect() != EffectKind.NONE)
            parts.add(effect(piece.effect()) + ", " + piece.effectRoundsLeft()
                    + (piece.effectRoundsLeft() == 1 ? " round left" : " rounds left"));
        if (piece.inBlock())
            parts.add("in a block");
        return String.join(" · ", parts);
    }

    public static String location(PieceSnapshot piece) {
        return switch (piece.location()) {
            case BASE -> "in base";
            case HOME -> "Home";
            case HOME_STRAIGHT -> "home straight, square " + (piece.position() + 1) + " of " + BoardConstants.HOME_STRAIGHT_SIZE;
            case MAIN_PATH -> "cell " + piece.position() + special(piece.position()).map(s -> " (" + s + ")").orElse("");
        };
    }

    /** E.g. "Cell 13 · Blue start (X)" or "Cell 25 · Beta · mystery cell, 2 rounds left". */
    public static String cell(int cell, MysterySnapshot mystery) {
        List<String> parts = new ArrayList<>();
        parts.add("Cell " + cell);
        for (PlayerColor colour : PlayerColor.values()) {
            if (PathMath.startCell(colour) == cell)
                parts.add(colour.display() + " start (X)");
            if (PathMath.approachCell(colour) == cell)
                parts.add(colour.display() + " approach (turn into the home straight)");
        }
        special(cell).ifPresent(s -> parts.add(s + " (" + effectAt(cell) + ")"));
        if (mystery != null && mystery.isActive() && mystery.cell() == cell)
            parts.add("mystery cell, " + mystery.roundsRemaining() + (mystery.roundsRemaining() == 1 ? " round left" : " rounds left"));
        return String.join(" · ", parts);
    }

    public static String homeStraight(PlayerColor colour, int index) {
        return colour.display() + " home straight, square " + (index + 1) + " of " + BoardConstants.HOME_STRAIGHT_SIZE;
    }

    public static java.util.Optional<String> special(int cell) {
        if (cell == BoardConstants.ALPHA) return java.util.Optional.of("Alpha");
        if (cell == BoardConstants.BETA) return java.util.Optional.of("Beta");
        if (cell == BoardConstants.GAMMA) return java.util.Optional.of("Gamma");
        return java.util.Optional.empty();
    }

    /** "α", "β" or "γ" for the three special cells, otherwise empty. */
    public static String greek(int cell) {
        if (cell == BoardConstants.ALPHA) return "α";
        if (cell == BoardConstants.BETA) return "β";
        if (cell == BoardConstants.GAMMA) return "γ";
        return "";
    }

    public static String effect(EffectKind kind) {
        return switch (kind) {
            case NONE -> "";
            case ENERGIZED -> "energised";
            case SICK -> "sick";
            case BRIEFING -> "briefing";
        };
    }

    private static String effectAt(int cell) {
        // As in Game.teleportAlpha / teleportBeta / teleportGamma.
        if (cell == BoardConstants.ALPHA) return "coin toss: energised or sick";
        if (cell == BoardConstants.BETA) return "briefing for four rounds";
        return "clockwise pieces turn round; counterclockwise go on to Beta";
    }
}
