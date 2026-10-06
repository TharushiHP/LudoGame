package ludo.player;

import ludo.shared.BoardConstants;
import ludo.shared.PlayerColor;
import ludo.effect.BriefingEffect;
import ludo.piece.Piece;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * A player's side of the board on the server: its four pieces and per-player turn state
 * (consecutive sixes, finishing place). It makes no decisions: which piece to move is asked of
 * the game's {@code MoveDecider}, which in the console game is backed by the strategies in
 * ludo.players. A1's Red/Green/Yellow/BluePlayer subclasses only attached a strategy, so they
 * were replaced by composition and this class became concrete (see docs/CHANGES_FROM_A1.md).
 */
public class Player {

    private final PlayerColor color;
    private final List<Piece> pieces;

    private int consecutiveSixes;
    private int finishPosition;

    public Player(PlayerColor color) {
        this.color = color;
        this.pieces = createPieces();
        this.consecutiveSixes = 0;
        this.finishPosition = 0;
    }

    private List<Piece> createPieces() {
        List<Piece> created = new ArrayList<>();
        for (int i = 1; i <= BoardConstants.PIECES_PER_PLAYER; i++) {
            created.add(new Piece(color, i));
        }
        return created;
    }

    /**
     * Rule T-13: records a roll by this player against every piece in briefing, whichever
     * piece the roll is then used for. Pieces that reach three consecutive 3s go to base.
     *
     * @return the pieces sent back to base by this roll (usually empty)
     */
    public List<Piece> recordRollForBriefing(int diceValue) {
        List<Piece> sentToBase = new ArrayList<>();
        for (Piece piece : pieces) {
            if (piece.getActiveEffect() instanceof BriefingEffect) {
                BriefingEffect briefing = (BriefingEffect) piece.getActiveEffect();
                briefing.recordRoll(diceValue);
                if (briefing.shouldTeleportToBase()) {
                    piece.resetToBase();
                    sentToBase.add(piece);
                }
            }
        }
        return sentToBase;
    }

    public void recordSix() {
        consecutiveSixes++;
    }

    public void resetConsecutiveSixes() {
        consecutiveSixes = 0;
    }

    public boolean hasTripleConsecutiveSixes() {
        return consecutiveSixes >= BoardConstants.MAX_CONSECUTIVE_SIXES;
    }

    public boolean hasBlockade() {
        return pieces.stream()
                .filter(Piece::isOnMainPath)
                .collect(Collectors.groupingBy(Piece::getMainPathPosition))
                .values().stream()
                .anyMatch(group -> group.size() >= 2);
    }

    /** Each block (two or more own pieces on one main-path cell) as its own group. */
    public List<List<Piece>> getBlocks() {
        return pieces.stream()
                .filter(Piece::isOnMainPath)
                .collect(Collectors.groupingBy(Piece::getMainPathPosition, TreeMap::new, Collectors.toList()))
                .values().stream()
                .filter(group -> group.size() >= 2)
                .collect(Collectors.toList());
    }

    public int countPiecesHome() {
        return (int) pieces.stream().filter(Piece::isHome).count();
    }

    public boolean hasAllPiecesHome() {
        return pieces.stream().allMatch(Piece::isHome);
    }

    public int countPiecesOnBoard() {
        return (int) pieces.stream().filter(Piece::isActive).count();
    }

    public int countPiecesAtBase() {
        return (int) pieces.stream().filter(Piece::isAtBase).count();
    }

    public void setFinishPosition(int position) {
        this.finishPosition = position;
    }

    public int getFinishPosition() {
        return finishPosition;
    }

    public PlayerColor getColor()          { return color; }
    /** Read-only view: callers can move pieces but cannot add, remove or reorder them (encapsulation). */
    public List<Piece> getPieces()         { return Collections.unmodifiableList(pieces); }
    public int getConsecutiveSixes()       { return consecutiveSixes; }

    public String describeState() {
        int onBoard = countPiecesOnBoard();
        int atBase  = countPiecesAtBase();
        return color.display() + " player now has " + onBoard + "/4 on pieces on the board" +
               " and " + atBase + "/4 pieces on the base.";
    }
}
