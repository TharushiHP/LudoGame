package ludo.game;

import ludo.board.Board;
import ludo.board.BoardConstants;
import ludo.board.PlayerColor;
import ludo.dice.Coin;
import ludo.dice.Dice;
import ludo.effect.BriefingEffect;
import ludo.effect.EnergizedEffect;
import ludo.effect.SickEffect;
import ludo.output.GameLogger;
import ludo.piece.Direction;
import ludo.piece.Piece;
import ludo.player.Player;
import ludo.player.PlayerFactory;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Runs one LUDO-T simulation: turn order, dice, rules T-1 to T-15 and the mystery cell.
 * Publishes every event to its listeners (Observer pattern, subject side).
 */
public class Game {

    private static final int MYSTERY_OPTIONS = 6;
    private static final int MAX_ROUNDS = 500;

    private final Board board;
    private final Dice dice;
    private final Coin coin;
    private final List<Player> players;
    private final List<GameEventListener> observers;
    private int roundNumber;
    private int turnCount;

    public Game(Board board, Dice dice, Coin coin) {
        this.board = board;
        this.dice = dice;
        this.coin = coin;
        this.observers = new java.util.ArrayList<>(List.of(GameLogger.getInstance()));
        this.players = buildPlayers();
        this.roundNumber = 0;
        this.turnCount = 0;
    }

    public void addObserver(GameEventListener listener) {
        observers.add(listener);
    }

    public int getRoundNumber() {
        return roundNumber;
    }

    public int getTurnCount() {
        return turnCount;
    }

    public boolean isRoundCapReached() {
        return roundNumber >= MAX_ROUNDS && activePlayers() > 1;
    }

    private List<Player> buildPlayers() {
        PlayerFactory factory = new PlayerFactory();
        return Arrays.asList(
                factory.create(PlayerColor.YELLOW),
                factory.create(PlayerColor.BLUE),
                factory.create(PlayerColor.RED),
                factory.create(PlayerColor.GREEN));
    }

    public void run() {
        printIntroduction();
        List<Player> turnOrder = resolveStartingOrder();
        printTurnOrder(turnOrder);
        mainLoop(turnOrder);
    }

    private void printIntroduction() {
        for (Player p : players) {
            char initial = p.getColor().name().charAt(0);
            publish(GameEvent.GAME_START, "The " + p.getColor().display().toLowerCase()
                    + " player has four (04) pieces named "
                    + initial + "1, " + initial + "2, "
                    + initial + "3, and " + initial + "4.");
        }
    }

    private List<Player> resolveStartingOrder() {
        int[] rolls = new int[players.size()];
        for (int i = 0; i < players.size(); i++) {
            rolls[i] = dice.roll();
            publish(GameEvent.DICE_ROLLED, players.get(i).getColor().display() + " rolls " + rolls[i]);
        }
        int highestIndex = 0;
        for (int i = 1; i < rolls.length; i++) {
            if (rolls[i] > rolls[highestIndex])
                highestIndex = i;
        }
        publish(GameEvent.FIRST_PLAYER_CHOSEN, players.get(highestIndex).getColor().display()
                + " player has the highest roll and will begin the game.");
        List<Player> order = new java.util.ArrayList<>();
        for (int i = 0; i < players.size(); i++) {
            order.add(players.get((highestIndex + i) % players.size()));
        }
        return order;
    }

    private void printTurnOrder(List<Player> order) {
        List<String> names = order.stream()
                .map(p -> p.getColor().display())
                .collect(Collectors.toList());
        String joined = String.join(", ", names.subList(0, names.size() - 1))
                + ", and " + names.get(names.size() - 1);
        publish(GameEvent.FIRST_PLAYER_CHOSEN, "The order of a single round is " + joined + ".");
    }

    // Rule 11: the game ends as soon as only one player still has pieces to bring home.
    private void mainLoop(List<Player> order) {
        while (activePlayers() > 1 && roundNumber < MAX_ROUNDS) {
            roundNumber++;
            publish(GameEvent.ROUND_START, "\n=== Round " + roundNumber + " ===");
            for (Player player : order) {
                if (activePlayers() <= 1) {
                    break;
                }
                if (!player.hasAllPiecesHome()) {
                    player.getPieces().forEach(Piece::decrementEffectRound);
                    turnCount++;
                    executeTurn(player, order);
                }
            }
            printRoundStatus(order);
            if (activePlayers() > 1) {
                board.onRoundComplete(allPieces(order));
                printMysteryStatus();
            }
        }
        if (activePlayers() <= 1) {
            rankLastPlayer();
        } else {
            publish(GameEvent.GAME_OVER, "\nWARNING: the safety limit of " + MAX_ROUNDS
                    + " rounds was reached before the game could finish."
                    + " Players with pieces still on the board are not ranked.");
        }
        printFinalStandings();
    }

    private void rankLastPlayer() {
        players.stream()
                .filter(p -> p.getFinishPosition() == 0)
                .forEach(p -> {
                    int rank = rankedPlayers() + 1;
                    p.setFinishPosition(rank);
                    publish(GameEvent.LAST_PLAYER_RANKED, "\n" + p.getColor().display()
                            + " player is the only player left and takes "
                            + positionLabel(rank) + " place.");
                });
    }

    private void printFinalStandings() {
        publish(GameEvent.GAME_OVER, "\n========================================");
        publish(GameEvent.GAME_OVER, "               GAME OVER                ");
        publish(GameEvent.GAME_OVER, "========================================");
        players.stream()
                .filter(p -> p.getFinishPosition() > 0)
                .sorted(Comparator.comparingInt(Player::getFinishPosition))
                .forEach(p -> {
                    String rank = positionLabel(p.getFinishPosition());
                    publish(GameEvent.GAME_OVER, rank + " place: " + p.getColor().display() + " player wins!!!");
                });
        players.stream()
                .filter(p -> p.getFinishPosition() == 0)
                .forEach(p -> publish(GameEvent.GAME_OVER, "Not ranked: " + p.getColor().display() + " player"));
        publish(GameEvent.GAME_OVER, "========================================");
        publish(GameEvent.GAME_OVER, "Game finished after " + roundNumber + " rounds and "
                + turnCount + " turns.");
    }

    private String positionLabel(int position) {
        switch (position) {
            case 1:
                return "1st";
            case 2:
                return "2nd";
            case 3:
                return "3rd";
            default:
                return position + "th";
        }
    }

    private void executeTurn(Player player, List<Player> order) {
        player.resetConsecutiveSixes();
        boolean keepRolling;
        do {
            int roll = dice.roll();
            publish(GameEvent.DICE_ROLLED, "\n" + player.getColor().display() + " player rolled " + roll + ".");
            applyBriefingRule(player, roll);

            if (roll == BoardConstants.MOVE_FROM_BASE_ROLL) {
                player.recordSix();
            } else {
                player.resetConsecutiveSixes();
            }

            if (player.hasTripleConsecutiveSixes()) {
                publish(GameEvent.TURN_SKIPPED, player.getColor().display()
                        + " rolled six three times consecutively. Turn passed.");
                handleTripleSixRule(player);
                return;
            }

            MoveResult result = processRoll(player, order, roll);
            keepRolling = (roll == BoardConstants.MOVE_FROM_BASE_ROLL || result.isCaptured())
                    && !player.hasAllPiecesHome();

        } while (keepRolling);
    }

    private MoveResult processRoll(Player player, List<Player> order, int roll) {
        List<Piece> all = allPieces(order);
        boolean rolledSix = (roll == BoardConstants.MOVE_FROM_BASE_ROLL);
        boolean hasPiecesAtBase = player.countPiecesAtBase() > 0;

        if (rolledSix && hasPiecesAtBase && player.prefersMoveFromBase(all, board)) {
            return activatePieceFromBase(player);
        }

        Piece chosen = player.choosePiece(all, board, roll);

        if (chosen == null) {
            publish(GameEvent.TURN_SKIPPED, player.getColor().display() + " has no piece to move. Turn skipped.");
            return MoveResult.builder().moved(false).build();
        }

        if (chosen.isAtBase()) {
            if (rolledSix)
                return activatePieceFromBase(player);
            publish(GameEvent.TURN_SKIPPED, player.getColor().display() + " has no movable piece. Turn skipped.");
            return MoveResult.builder().moved(false).build();
        }

        if (hasBriefingEffect(chosen)) {
            publish(GameEvent.PIECE_BRIEFING, player.getColor().display() + " piece " + chosen.getName()
                    + " is in briefing and cannot move. Turn skipped.");
            return MoveResult.builder().moved(false).build();
        }

        int effectiveSteps = chosen.applyEffect(roll);
        return executeMove(player, chosen, effectiveSteps, all);
    }

    private MoveResult activatePieceFromBase(Player player) {
        Piece piece = player.getPieces().stream()
                .filter(Piece::isAtBase)
                .findFirst()
                .orElse(null);
        if (piece == null)
            return MoveResult.builder().moved(false).build();

        piece.placeOnStart();
        Direction dir = coin.toss();
        piece.setDirection(dir);

        publish(GameEvent.PIECE_MOVED_TO_START, player.getColor().display() + " player moves piece " + piece.getName()
                + " to the starting point. The coin toss sets its direction to " + dir.display() + ".");
        publish(GameEvent.PIECE_MOVED_TO_START, player.getColor().display() + " player now has "
                + player.countPiecesOnBoard() + "/4 on pieces on the board and "
                + player.countPiecesAtBase() + "/4 pieces on the base.");
        return MoveResult.builder().moved(true).build();
    }

    private MoveResult executeMove(Player player, Piece piece, int steps, List<Piece> all) {
        List<Piece> blockPeers = sameColorBlockPeers(piece, player.getPieces());
        if (!blockPeers.isEmpty() && hasMixedDirections(piece, blockPeers)) {
            return executeMixedBlockMove(player, piece, blockPeers, steps, all);
        }

        MoveTarget target = board.computeMoveTarget(piece, steps);

        if (target.isOvershoot()) {
            publish(GameEvent.TURN_SKIPPED, player.getColor().display() + " piece " + piece.getName()
                    + " cannot move - exact roll required to reach Home.");
            return MoveResult.builder().moved(false).build();
        }

        if (target.isHomeStraight() || target.isHome()) {

            if (piece.getDirection() == Direction.COUNTER_CLOCKWISE) {
                piece.incrementApproachPassCount();
            }

            if (!piece.canEnterHomeStraight()) {
                return bypassApproach(player, piece, steps, all);
            }
            if (target.isHome()) {
                return reachHome(player, piece);
            }
            return landOnHomeStraight(player, piece, target.getPosition());
        }

        return landOnMainPath(player, piece, target.getPosition(), steps, all);
    }

    private MoveResult bypassApproach(Player player, Piece piece, int steps, List<Piece> all) {
        int stepsToApproach = board.stepsToApproach(piece);
        int remainingSteps = steps - stepsToApproach;

        int landingCell;
        if (remainingSteps <= 0) {
            landingCell = piece.approachPosition();
        } else {
            landingCell = board.advance(piece.approachPosition(), remainingSteps, piece.getDirection());
        }

        return landOnMainPath(player, piece, landingCell, steps, all);
    }

    private MoveResult landOnHomeStraight(Player player, Piece piece, int newIndex) {
        String from = piece.positionLabel();
        piece.moveToHomePath(newIndex);
        publish(GameEvent.PIECE_MOVED, player.getColor().display() + " moves piece " + piece.getName()
                + " from location " + from + " to " + piece.positionLabel() + ".");
        return MoveResult.builder().moved(true).build();
    }

    private MoveResult reachHome(Player player, Piece piece) {
        piece.reachHome();
        publish(GameEvent.PIECE_REACHED_HOME, player.getColor().display() + " piece " + piece.getName() + " has reached Home!");
        checkFinish(player);
        return MoveResult.builder().moved(true).reachedHome(true).build();
    }

    private MoveResult landOnMainPath(Player player, Piece piece, int targetCell,
            int steps, List<Piece> all) {
        if (board.pathCrossesBlock(piece, steps, all)) {
            int blockCell = board.findBlockCell(piece, steps, all);
            Piece blocker = board.opponentPiecesAtCell(player.getColor(), blockCell, all)
                    .stream().findFirst().orElse(null);
            String blockerDesc = blocker == null
                    ? "an opponent"
                    : blocker.getColor().display() + " piece " + blocker.getPieceNumber();

            int safeCell = board.findCellBeforeBlock(piece, steps, all);
            publish(GameEvent.PIECE_BLOCKED, player.getColor().display() + " piece " + piece.getName()
                    + " is blocked from moving from " + piece.getMainPathPosition()
                    + " to " + targetCell + " by " + blockerDesc + ".");

            if (safeCell < 0 || safeCell == piece.getMainPathPosition()) {
                publish(GameEvent.PIECE_BLOCKED, player.getColor().display()
                        + " does not have other pieces in the board to move instead of the blocked piece."
                        + " Ignoring the throw and moving on to the next player.");
                return MoveResult.builder().moved(false).build();
            }
            publish(GameEvent.PIECE_BLOCKED, player.getColor().display()
                    + " does not have other pieces in the board to move instead of the blocked piece."
                    + " Moved the piece to square " + safeCell + " which is the cell before the block.");
            piece.setMainPathPosition(safeCell);
            return MoveResult.builder().moved(true).build();
        }

        if (board.hasFriendlyPieceAt(player.getColor(), targetCell, piece, all)) {
            return formBlock(player, piece, targetCell, steps, all);
        }

        String fromLabel = piece.positionLabel();
        piece.setMainPathPosition(targetCell);
        publish(GameEvent.PIECE_MOVED, player.getColor().display() + " moves piece " + piece.getName()
                + " from location " + fromLabel + " to " + targetCell
                + " by " + steps + " units in " + piece.getDirection().display() + " direction.");

        List<Piece> opponents = board.opponentPiecesAtCell(player.getColor(), targetCell, all);
        boolean captured = false;
        if (opponents.size() == 1) {
            captured = captureOpponent(player, piece, opponents.get(0), targetCell);
        } else if (opponents.size() >= 2) {
            captured = captureOpponentBlock(player, piece, opponents, targetCell, all);
        }

        if (board.getMysteryCell().isAt(targetCell)) {
            handleMysteryCell(player, piece, all);
            return MoveResult.builder().moved(true).captured(captured).landedOnMystery(true).build();
        }

        return MoveResult.builder().moved(true).captured(captured).build();
    }

    private List<Piece> sameColorBlockPeers(Piece piece, List<Piece> ownPieces) {
        if (!piece.isOnMainPath())
            return java.util.Collections.emptyList();
        return ownPieces.stream()
                .filter(p -> p != piece)
                .filter(Piece::isOnMainPath)
                .filter(p -> p.getMainPathPosition() == piece.getMainPathPosition())
                .collect(Collectors.toList());
    }

    private boolean hasMixedDirections(Piece piece, List<Piece> peers) {
        return peers.stream().anyMatch(p -> p.getDirection() != piece.getDirection());
    }

    private MoveResult executeMixedBlockMove(Player player, Piece piece,
            List<Piece> peers, int diceValue, List<Piece> all) {
        List<Piece> block = new java.util.ArrayList<>();
        block.add(piece);
        block.addAll(peers);

        int blockSize = block.size();
        int stepsEach = diceValue / blockSize;
        if (stepsEach == 0) {
            publish(GameEvent.PIECE_BLOCKED, player.getColor().display() + " block at cell " + piece.getMainPathPosition()
                    + " cannot move — dice value too small for block of size " + blockSize + ".");
            return MoveResult.builder().moved(false).build();
        }

        Direction blockDir = block.stream()
                .max(Comparator.comparingInt(p -> board.distanceToApproach(p)))
                .map(Piece::getDirection)
                .orElse(piece.getDirection());

        int fromCell = piece.getMainPathPosition();
        int toCell = board.advance(fromCell, stepsEach, blockDir);

        for (Piece p : block) {
            p.setDirection(blockDir);
            p.setMainPathPosition(toCell);
        }

        publish(GameEvent.PIECE_MOVED, player.getColor().display() + " block moves from cell " + fromCell
                + " to cell " + toCell + " by " + stepsEach + " units each in "
                + blockDir.display() + " direction (Rule T-4).");
        return MoveResult.builder().moved(true).build();
    }

    private MoveResult formBlock(Player player, Piece piece, int targetCell, int steps, List<Piece> all) {
        String fromLabel = piece.positionLabel();
        piece.setMainPathPosition(targetCell);
        publish(GameEvent.PIECE_MOVED, player.getColor().display() + " moves piece " + piece.getName()
                + " from location " + fromLabel + " to " + targetCell
                + " by " + steps + " units in " + piece.getDirection().display() + " direction.");
        publish(GameEvent.BLOCK_FORMED, player.getColor().display() + " piece " + piece.getName()
                + " forms a block at cell " + targetCell + ".");
        return MoveResult.builder().moved(true).build();
    }

    private boolean captureOpponent(Player capturer, Piece capturerPiece,
            Piece target, int cell) {
        publish(GameEvent.PIECE_CAPTURED, capturer.getColor().display() + " piece " + capturerPiece.getName()
                + " lands on square " + cell + ", captures "
                + target.getColor().display() + " piece " + target.getName()
                + ", and returns it to the base.");
        target.resetToBase();
        capturerPiece.incrementCaptureCount();
        publish(GameEvent.PIECE_CAPTURED, capturer.getColor().display() + " player now has "
                + capturer.countPiecesOnBoard() + "/4 on pieces on the board and "
                + capturer.countPiecesAtBase() + "/4 pieces on the base.");
        return true;
    }

    private boolean captureOpponentBlock(Player capturer, Piece capturerPiece,
            List<Piece> opponentBlock, int cell, List<Piece> all) {
        boolean capturerIsInBlock = board.hasSameColorBlock(
                capturerPiece.getMainPathPosition(), capturerPiece.getColor(), all);
        if (!capturerIsInBlock)
            return false;

        publish(GameEvent.PIECE_CAPTURED, capturer.getColor().display() + " block captures opponent block at cell " + cell + ".");
        for (Piece target : opponentBlock) {
            target.resetToBase();
            capturerPiece.incrementCaptureCount();
            publish(GameEvent.PIECE_CAPTURED, target.getColor().display() + " piece " + target.getName()
                    + " returned to base.");
        }
        return true;
    }

    private void handleMysteryCell(Player player, Piece piece, List<Piece> all) {
        int option = dice.roll() % MYSTERY_OPTIONS;
        publish(GameEvent.MYSTERY_CELL_TELEPORT, player.getColor().display()
                + " player lands on a mystery cell and is teleported to "
                + teleportName(option) + ".");
        applyTeleport(player, piece, option);
    }

    private String teleportName(int option) {
        switch (option) {
            case 0:
                return "Alpha";
            case 1:
                return "Beta";
            case 2:
                return "Gamma";
            case 3:
                return "Base";
            case 4:
                return "X";
            case 5:
                return "Approach";
            default:
                return "Unknown";
        }
    }

    private void applyTeleport(Player player, Piece piece, int option) {
        switch (option) {
            case 0:
                teleportAlpha(player, piece);
                break;
            case 1:
                teleportBeta(player, piece);
                break;
            case 2:
                teleportGamma(player, piece);
                break;
            case 3:
                teleportBase(player, piece);
                break;
            case 4:
                teleportStart(player, piece);
                break;
            case 5:
                teleportApproach(player, piece);
                break;
        }
    }

    private void teleportAlpha(Player player, Piece piece) {
        piece.setMainPathPosition(BoardConstants.ALPHA);
        publish(GameEvent.MYSTERY_CELL_TELEPORT, player.getColor().display() + " piece " + piece.getName() + " teleported to Alpha.");
        if (coin.toss() == Direction.CLOCKWISE) {
            piece.setActiveEffect(new EnergizedEffect());
            publish(GameEvent.PIECE_ENERGIZED, player.getColor().display() + " piece " + piece.getName()
                    + " feels energized, and movement speed doubles.");
        } else {
            piece.setActiveEffect(new SickEffect());
            publish(GameEvent.PIECE_SICK, player.getColor().display() + " piece " + piece.getName()
                    + " feels sick, and movement speed halves.");
        }
    }

    private void teleportBeta(Player player, Piece piece) {
        piece.setMainPathPosition(BoardConstants.BETA);
        piece.setActiveEffect(new BriefingEffect());
        publish(GameEvent.MYSTERY_CELL_TELEPORT, player.getColor().display() + " piece " + piece.getName() + " teleported to Beta.");
        publish(GameEvent.PIECE_BRIEFING, player.getColor().display() + " piece " + piece.getName()
                + " attends briefing and cannot move for four rounds.");
    }

    private void teleportGamma(Player player, Piece piece) {
        publish(GameEvent.MYSTERY_CELL_TELEPORT, player.getColor().display() + " piece " + piece.getName() + " teleported to Gamma.");
        if (piece.getDirection() == Direction.CLOCKWISE) {
            piece.setMainPathPosition(BoardConstants.GAMMA);
            piece.setDirection(Direction.COUNTER_CLOCKWISE);
            publish(GameEvent.PIECE_DIRECTION_CHANGED, "The " + player.getColor().display() + " piece " + piece.getName()
                    + ", which was moving clockwise, has changed to moving counterclockwise.");
        } else {
            teleportBeta(player, piece);
            publish(GameEvent.MYSTERY_CELL_TELEPORT, "The " + player.getColor().display() + " piece " + piece.getName()
                    + " is moving in a counterclockwise direction. Teleporting to Beta from Gamma.");
        }
    }

    private void teleportBase(Player player, Piece piece) {
        piece.resetToBase();
        publish(GameEvent.MYSTERY_CELL_TELEPORT, player.getColor().display() + " piece " + piece.getName() + " teleported to Base.");
    }

    private void teleportStart(Player player, Piece piece) {
        piece.placeOnStart();
        publish(GameEvent.MYSTERY_CELL_TELEPORT, player.getColor().display() + " piece " + piece.getName() + " teleported to X.");
    }

    private void teleportApproach(Player player, Piece piece) {
        piece.setMainPathPosition(piece.approachPosition());
        publish(GameEvent.MYSTERY_CELL_TELEPORT, player.getColor().display() + " piece " + piece.getName()
                + " teleported to Approach.");
    }

    private boolean hasBriefingEffect(Piece piece) {
        return piece.hasEffect() && piece.getActiveEffect() instanceof BriefingEffect;
    }

    // Rule T-13: checked on every roll the player makes, not only when the briefing piece is chosen.
    private void applyBriefingRule(Player player, int roll) {
        for (Piece piece : player.recordRollForBriefing(roll)) {
            publish(GameEvent.PIECE_BRIEFING_TELEPORT, player.getColor().display() + " piece " + piece.getName()
                    + " is movement-restricted and has rolled three consecutively."
                    + " Teleporting piece " + piece.getName() + " to base.");
        }
    }

    private void handleTripleSixRule(Player player) {
        if (!player.hasBlockade())
            return;
        List<Piece> block = player.getBlockadePieces();
        for (int i = 1; i < block.size(); i++) {
            Piece toDisplace = block.get(i);
            int newPos = board.advance(toDisplace.getMainPathPosition(), 6, toDisplace.getDirection());
            toDisplace.setMainPathPosition(newPos);
            publish(GameEvent.PIECE_MOVED, player.getColor().display() + " blockade broken: piece "
                    + toDisplace.getName() + " moved to cell " + newPos + ".");
        }
    }

    private void checkFinish(Player player) {
        if (player.hasAllPiecesHome() && player.getFinishPosition() == 0) {
            int rank = rankedPlayers() + 1;
            player.setFinishPosition(rank);
            publish(GameEvent.PLAYER_WINS, player.getColor().display() + " player wins!!!");
        }
    }

    private void printRoundStatus(List<Player> order) {
        for (Player p : order) {
            publish(GameEvent.ROUND_STATUS, p.describeState());
            publish(GameEvent.ROUND_STATUS, "============================");
            publish(GameEvent.ROUND_STATUS, "Location of pieces " + p.getColor().display());
            publish(GameEvent.ROUND_STATUS, "============================");
            for (Piece piece : p.getPieces()) {
                publish(GameEvent.ROUND_STATUS, "Piece " + piece.getName() + " -> " + piece.positionLabel());
            }
        }
    }

    // Printed after the board has counted the round, so the number shown is the rounds really left.
    private void printMysteryStatus() {
        if (board.getMysteryCell().wasJustSpawned()) {
            publish(GameEvent.MYSTERY_CELL_SPAWNED, "A mystery cell has spawned in location "
                    + board.getMysteryCell().getPosition()
                    + " and will be at this location for the next four rounds.");
        } else if (board.isMysteryActive()) {
            publish(GameEvent.ROUND_STATUS, "The mystery cell is at " + board.getMysteryCell().getPosition()
                    + " and will be at that location for the next "
                    + board.getMysteryCell().getRoundsRemaining() + " rounds.");
        }
    }

    private List<Piece> allPieces(List<Player> playerList) {
        return playerList.stream()
                .flatMap(p -> p.getPieces().stream())
                .collect(Collectors.toList());
    }

    private int activePlayers() {
        return (int) players.stream().filter(p -> !p.hasAllPiecesHome()).count();
    }

    private int rankedPlayers() {
        return (int) players.stream().filter(p -> p.getFinishPosition() > 0).count();
    }

    private void publish(GameEvent event, String message) {
        observers.forEach(listener -> listener.onEvent(event, message));
    }
}
