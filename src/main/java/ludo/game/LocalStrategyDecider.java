package ludo.game;

import ludo.board.Board;
import ludo.board.PlayerColor;
import ludo.piece.Piece;
import ludo.player.Player;

import java.util.List;
import java.util.OptionalInt;
import java.util.stream.Collectors;

/**
 * Makes the existing {@link ludo.player.strategy.MoveStrategy} classes (through each
 * {@link Player}) usable as a {@link MoveDecider}, so the console game plays exactly as before
 * (Adapter pattern: adapts the strategy interface to the port Game expects).
 * <p>
 * For now the strategies still read the live board and pieces, and the snapshot argument is not
 * used. In a later task the strategies move to the client and will work from the
 * {@link GameSnapshot} directly, because a client never has the server's live objects.
 */
public class LocalStrategyDecider implements MoveDecider {

    private final List<Player> players;
    private final Board board;

    public LocalStrategyDecider(List<Player> players, Board board) {
        this.players = List.copyOf(players);
        this.board = board;
    }

    @Override
    public OptionalInt choosePiece(GameSnapshot snapshot, PlayerColor color, int roll, List<Integer> candidatePieceIds) {
        Player player = playerOf(color);
        List<Piece> excluded = player.getPieces().stream()
                .filter(p -> !candidatePieceIds.contains(p.getPieceNumber()))
                .collect(Collectors.toList());
        Piece chosen = excluded.isEmpty()
                ? player.choosePiece(allPieces(), board, roll)
                : player.choosePiece(allPieces(), board, roll, excluded);
        return chosen == null ? OptionalInt.empty() : OptionalInt.of(chosen.getPieceNumber());
    }

    @Override
    public boolean prefersMoveFromBase(GameSnapshot snapshot, PlayerColor color) {
        return playerOf(color).prefersMoveFromBase(allPieces(), board);
    }

    @Override
    public boolean triesOtherPiecesWhenBlocked(PlayerColor color) {
        return playerOf(color).triesOtherPiecesWhenBlocked();
    }

    private Player playerOf(PlayerColor color) {
        return players.stream().filter(p -> p.getColor() == color).findFirst().orElseThrow();
    }

    private List<Piece> allPieces() {
        return players.stream().flatMap(p -> p.getPieces().stream()).collect(Collectors.toList());
    }
}
