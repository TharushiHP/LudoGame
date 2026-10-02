package ludo.player;

import ludo.board.Board;
import ludo.board.BoardConstants;
import ludo.board.PlayerColor;
import ludo.effect.BriefingEffect;
import ludo.piece.Piece;
import ludo.player.strategy.MoveStrategy;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A player: owns four pieces and per-player turn state, and delegates the choice of piece
 * to its {@link MoveStrategy} (Strategy pattern; composition over inheritance).
 */
public abstract class Player {

    protected final PlayerColor color;
    protected final List<Piece> pieces;
    protected final MoveStrategy strategy;

    private int consecutiveSixes;
    private int finishPosition;

    protected Player(PlayerColor color, MoveStrategy strategy) {
        this.color = color;
        this.strategy = strategy;
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

    public Piece choosePiece(List<Piece> allPieces, Board board, int diceValue) {
        return strategy.choosePiece(pieces, allPieces, board, diceValue);
    }

    public boolean prefersMoveFromBase(List<Piece> allPieces, Board board) {
        return strategy.prefersMoveFromBase(pieces, allPieces, board);
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

    public List<Piece> getBlockadePieces() {
        return pieces.stream()
                .filter(Piece::isOnMainPath)
                .collect(Collectors.groupingBy(Piece::getMainPathPosition))
                .values().stream()
                .filter(group -> group.size() >= 2)
                .flatMap(List::stream)
                .collect(Collectors.toList());
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
    public List<Piece> getPieces()         { return pieces; }
    public int getConsecutiveSixes()       { return consecutiveSixes; }

    public String describeState() {
        int onBoard = countPiecesOnBoard();
        int atBase  = countPiecesAtBase();
        return color.display() + " player now has " + onBoard + "/4 on pieces on the board" +
               " and " + atBase + "/4 pieces on the base.";
    }
}
