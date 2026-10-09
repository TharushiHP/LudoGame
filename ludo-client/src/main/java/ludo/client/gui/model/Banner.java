package ludo.client.gui.model;

import ludo.shared.PlayerColor;
import ludo.shared.snapshot.GameSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public record Banner(Kind kind, String text, PlayerColor colour) {

    public enum Kind { CAPTURE, MYSTERY, EFFECT, BLOCK, HOME, FINISH }

    /** More than this per STATE would only pile up; the rest stays in the log. */
    public static final int MAX_PER_STATE = 3;

    private record Rule(Pattern pattern, Function<Matcher, Banner> make) {
    }

    /** Patterns copied from the game's messages (see the golden files in ludo-server). */
    private static final List<Rule> RULES = List.of(
            rule("^(\\w+) piece (\\w\\d) lands on square \\d+, captures \\w+ piece (\\w\\d), and returns it to the base\\.$",
                    m -> new Banner(Kind.CAPTURE, m.group(2) + " captures " + m.group(3) + "!", colour(m.group(1)))),
            rule("^(\\w+) block lands on square \\d+ and captures (\\d+) opponent piece\\(s\\)\\.$",
                    m -> new Banner(Kind.CAPTURE, m.group(1) + " block captures " + pieces(m.group(2)) + "!", colour(m.group(1)))),
            rule("^(\\w+) piece (\\w\\d) teleported to (\\w+)\\.$",
                    m -> new Banner(Kind.MYSTERY, "Mystery cell! " + m.group(2) + " teleports to " + m.group(3), colour(m.group(1)))),
            rule("^(\\w+) piece (\\w\\d) feels energized, .*$",
                    m -> new Banner(Kind.EFFECT, m.group(2) + " is energised: double speed", colour(m.group(1)))),
            rule("^(\\w+) piece (\\w\\d) feels sick, .*$",
                    m -> new Banner(Kind.EFFECT, m.group(2) + " is sick: half speed", colour(m.group(1)))),
            rule("^(\\w+) piece (\\w\\d) attends briefing and cannot move for four rounds\\.$",
                    m -> new Banner(Kind.EFFECT, m.group(2) + " is in a briefing for 4 rounds", colour(m.group(1)))),
            rule("^The (\\w+) piece (\\w\\d), which was moving clockwise, has changed to moving counterclockwise\\.$",
                    m -> new Banner(Kind.EFFECT, m.group(2) + " turns round: now counterclockwise", colour(m.group(1)))),
            rule("^(\\w+) piece (\\w\\d) forms a block at cell (\\d+)\\.$",
                    m -> new Banner(Kind.BLOCK, m.group(1) + " forms a block on cell " + m.group(3), colour(m.group(1)))),
            rule("^(\\w+) piece (\\w\\d) has reached Home!$",
                    m -> new Banner(Kind.HOME, m.group(2) + " reached Home!", colour(m.group(1)))),
            rule("^(\\w+) player is the only player left and takes (\\d+\\w\\w) place\\.$",
                    m -> new Banner(Kind.FINISH, m.group(1) + " takes " + m.group(2) + " place", colour(m.group(1))))
    );

    /** "Red player wins!!!" is printed when a player finishes; the place comes from the snapshot. */
    private static final Pattern WINS = Pattern.compile("^(\\w+) player wins!!!$");

    /** The banners for one STATE, in log order, at most {@link #MAX_PER_STATE}. */
    public static List<Banner> fromLog(List<String> lines, GameSnapshot snapshot) {
        List<Banner> banners = new ArrayList<>();
        for (String line : lines) {
            find(line.trim(), snapshot).ifPresent(banner -> {
                if (!banners.contains(banner))
                    banners.add(banner);
            });
            if (banners.size() == MAX_PER_STATE)
                break;
        }
        return banners;
    }

    static Optional<Banner> find(String line, GameSnapshot snapshot) {
        try {
            for (Rule rule : RULES) {
                Matcher m = rule.pattern().matcher(line);
                if (m.matches())
                    return Optional.of(rule.make().apply(m));
            }
            Matcher wins = WINS.matcher(line);
            if (wins.matches()) {
                PlayerColor colour = colour(wins.group(1));
                Integer place = snapshot == null ? null : snapshot.finishPositions().get(colour);
                String text = place == null || place == 0 ? colour.display() + " has finished!"
                        : colour.display() + " finishes " + ordinal(place) + "!";
                return Optional.of(new Banner(Kind.FINISH, text, colour));
            }
        } catch (IllegalArgumentException e) {
            // a word that is not a colour: not one of our lines
        }
        return Optional.empty();
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

    private static Rule rule(String regex, Function<Matcher, Banner> make) {
        return new Rule(Pattern.compile(regex), make);
    }

    private static PlayerColor colour(String word) {
        return PlayerColor.valueOf(word.toUpperCase(Locale.ROOT));
    }

    private static String pieces(String count) {
        return count.equals("1") ? "1 piece" : count + " pieces";
    }
}
