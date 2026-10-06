package ludo.game;

import ludo.shared.decision.MoveDecider;
import ludo.shared.decision.PieceChoice;
import ludo.shared.snapshot.GameStatus;
import ludo.shared.snapshot.MysterySnapshot;
import ludo.shared.snapshot.PieceSnapshot;
import ludo.shared.snapshot.GameSnapshot;
import ludo.shared.EffectKind;
import ludo.shared.BoardConstants;
import ludo.shared.Direction;
import ludo.shared.PlayerColor;
import ludo.board.Board;
import ludo.board.MoveTarget;
import ludo.dice.Coin;
import ludo.dice.Dice;
import ludo.effect.BriefingEffect;
import ludo.effect.PieceEffect;
import ludo.effect.EnergizedEffect;
import ludo.effect.SickEffect;
import ludo.board.MysteryCell;
import ludo.piece.Piece;
import ludo.player.Player;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.stream.Collectors;

/**
 * Runs one LUDO-T simulation and owns the authoritative game state: turn order, dice, rules
 * T-1 to T-15 and the mystery cell.
 * <ul>
 *   <li>Observer pattern (subject): publishes every event to its {@link GameEventListener}s.</li>
 *   <li>Dependency Inversion: player decisions come through the {@link MoveDecider} port and
 *       turn pacing through the {@link TurnGate} port; Game never knows whether they are local
 *       strategies or remote clients.</li>
 *   <li>Dependency Injection: everything it uses is passed in by {@link GameBuilder};
 *       Game creates none of its collaborators itself.</li>
 *   <li>{@link #snapshot()} returns an immutable {@link GameSnapshot} without changing any state.</li>
 * </ul>
 */
public class Game {

    private static final int MYSTERY_OPTIONS = 6;
    static final int DEFAULT_MAX_ROUNDS = 500;
    // 1.5 x the longest no-progress stretch (80 rounds) measured in 100 normal games, seeds 1-100.
    static final int DEFAULT_STALEMATE_ROUNDS = 120;

    private final Board board;
    private final Dice dice;
    private final Coin coin;
    private final List<Player> players;
    private final List<GameEventListener> observers;
    private final MoveDecider moveDecider;
    private final TurnGate turnGate;
    private final int maxRounds;
    private final int stalemateRounds;
    private final EndCondition endCondition;
    private int roundNumber;
    private int turnCount;
    private int lastProgressRound;
    private GameStatus status;
    private PlayerColor currentPlayer;
    private int lastRoll;
    // Kept for each decider and returned in every snapshot (only Blue uses it: its cycle position).
    private final Map<PlayerColor, Integer> deciderMemo;

    // Package-private: build games through GameBuilder, which wires every collaborator.
    Game(Board board, Dice dice, Coin coin, List<Player> players, MoveDecider moveDecider,
            TurnGate turnGate, List<GameEventListener> listeners, int maxRounds, int stalemateRounds,
            EndCondition endCondition) {
        this.board = board;
        this.dice = dice;
        this.coin = coin;
        this.players = List.copyOf(players);
        this.moveDecider = moveDecider;
        this.turnGate = turnGate;
        this.observers = new java.util.ArrayList<>(listeners);
        this.maxRounds = maxRounds;
        this.stalemateRounds = stalemateRounds;
        this.endCondition = endCondition;
        this.roundNumber = 0;
        this.turnCount = 0;
        this.lastProgressRound = 0;
        this.status = GameStatus.NOT_STARTED;
        this.currentPlayer = null;
        this.lastRoll = 0;
        this.deciderMemo = new EnumMap<>(PlayerColor.class);
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
        return roundNumber >= maxRounds && !isOver() && !isStalemate();
    }

    /** ALL_PLACES: one player (or none) still playing, exactly the A1 test. FIRST_WINNER: someone has won. */
    private boolean isOver() {
        return endCondition == EndCondition.FIRST_WINNER ? rankedPlayers() >= 1 : activePlayers() <= 1;
    }

    private boolean isStalemate() {
        return roundNumber - lastProgressRound >= stalemateRounds;
    }

    // Package-private hooks so tests can set up a board position and play one roll or turn.

    Player playerOf(PlayerColor color) {
        return players.stream().filter(p -> p.getColor() == color).findFirst().orElseThrow();
    }

    MoveResult playRoll(PlayerColor color, int roll) {
        return processRoll(playerOf(color), players, roll);
    }

    void playTurn(PlayerColor color) throws InterruptedException {
        takeTurn(playerOf(color), players);
    }

    /**
     * Immutable picture of the current state. Only reads state: taking a snapshot at any moment,
     * any number of times, never changes the game.
     */
    public GameSnapshot snapshot() {
        Map<PlayerColor, Integer> finishPositions = new EnumMap<>(PlayerColor.class);
        List<PieceSnapshot> pieces = new java.util.ArrayList<>();
        for (Player player : players) {
            finishPositions.put(player.getColor(), player.getFinishPosition());
            for (Piece piece : player.getPieces()) {
                pieces.add(snapshotOf(piece, isInBlock(piece, player)));
            }
        }
        MysteryCell mysteryCell = board.getMysteryCell();
        MysterySnapshot mystery = mysteryCell.isActive()
                ? new MysterySnapshot(mysteryCell.getPosition(), mysteryCell.getRoundsRemaining())
                : new MysterySnapshot(-1, 0);
        return new GameSnapshot(roundNumber, turnCount, currentPlayer, lastRoll, mystery,
                finishPositions, status, pieces, deciderMemo);
    }

    private static PieceSnapshot snapshotOf(Piece piece, boolean inBlock) {
        int position = piece.isOnMainPath() ? piece.getMainPathPosition()
                : piece.isOnHomeStraight() ? piece.getHomePathIndex()
                : -1;
        PieceEffect effect = piece.getActiveEffect();
        return new PieceSnapshot(piece.getColor(), piece.getPieceNumber(), piece.getLocation(), position,
                piece.getDirection(), piece.getCaptureCount(),
                effect == null ? EffectKind.NONE : effect.kind(),
                effect == null ? 0 : effect.getRoundsRemaining(),
                inBlock);
    }

    private boolean isInBlock(Piece piece, Player owner) {
        return piece.isOnMainPath() && owner.getPieces().stream()
                .filter(Piece::isOnMainPath)
                .filter(p -> p.getMainPathPosition() == piece.getMainPathPosition())
                .count() >= 2;
    }

    /**
     * Plays the whole game on the calling thread. If a {@link TurnGate} wait is interrupted
     * (e.g. server shutdown), the game stops with status ABORTED and the interrupt flag is restored.
     */
    public void run() {
        status = GameStatus.IN_PROGRESS;
        try {
            printIntroduction();
            List<Player> turnOrder = resolveStartingOrder();
            printTurnOrder(turnOrder);
            mainLoop(turnOrder);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            status = GameStatus.ABORTED;
            publish(GameEvent.GAME_OVER, "\nThe game was stopped before it finished.");
        }
    }

    private void takeTurn(Player player, List<Player> order) throws InterruptedException {
        currentPlayer = player.getColor();
        executeTurn(player, order);
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

    // Rule 11: the game ends as soon as only one player still has pieces to bring home (ALL_PLACES),
    // or as soon as the first player has (FIRST_WINNER).
    // Stalemate rule (fills a spec gap): it also ends after stalemateRounds rounds without progress.
    private void mainLoop(List<Player> order) throws InterruptedException {
        while (!isOver() && roundNumber < maxRounds && !isStalemate()) {
            roundNumber++;
            publish(GameEvent.ROUND_START, "\n=== Round " + roundNumber + " ===");
            for (Player player : order) {
                if (isOver()) {
                    break;
                }
                if (!player.hasAllPiecesHome()) {
                    player.getPieces().forEach(Piece::decrementEffectRound);
                    turnCount++;
                    takeTurn(player, order);
                }
            }
            printRoundStatus(order);
            if (!isOver()) {
                board.onRoundComplete(allPieces(order));
                printMysteryStatus();
            }
        }
        if (isOver()) {
            if (endCondition == EndCondition.FIRST_WINNER) {
                publish(GameEvent.GAME_OVER, "\nThe game ends with the first winner (Rule 11): the other players are not ranked.");
            } else {
                rankLastPlayer();
            }
            status = GameStatus.FINISHED;
        } else if (isStalemate()) {
            declareStalemate();
        } else {
            publish(GameEvent.GAME_OVER, "\nWARNING: the safety limit of " + maxRounds
                    + " rounds was reached before the game could finish."
                    + " Players with pieces still on the board are not ranked.");
            status = GameStatus.ROUND_CAP_REACHED;
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

    // Remaining players are ranked by pieces Home (more is better), then by cells left (fewer is better).
    void declareStalemate() {
        publish(GameEvent.STALEMATE, "\nNo progress for " + stalemateRounds
                + " rounds: the game is declared a stalemate.");
        List<Player> remaining = players.stream()
                .filter(p -> p.getFinishPosition() == 0)
                .sorted(Comparator.comparingInt(Player::countPiecesHome).reversed()
                        .thenComparingInt(this::cellsLeftFor))
                .collect(Collectors.toList());
        for (Player p : remaining) {
            int rank = rankedPlayers() + 1;
            p.setFinishPosition(rank);
            publish(GameEvent.STALEMATE, p.getColor().display() + " player takes " + positionLabel(rank)
                    + " place (" + p.countPiecesHome() + " pieces Home, " + cellsLeftFor(p) + " cells left).");
        }
        status = GameStatus.STALEMATE;
    }

    // An estimate: it ignores the T-7 capture requirement and extra laps for counterclockwise pieces.
    private int cellsLeftFor(Player player) {
        return player.getPieces().stream().mapToInt(this::cellsLeft).sum();
    }

    private int cellsLeft(Piece piece) {
        int approachToHome = BoardConstants.HOME_STRAIGHT_SIZE + 1;
        if (piece.isHome())
            return 0;
        if (piece.isOnHomeStraight())
            return BoardConstants.HOME_STRAIGHT_SIZE - piece.getHomePathIndex();
        if (piece.isOnMainPath())
            return board.distanceToApproach(piece) + approachToHome;
        // At base: 1 move out to X, then X to the approach cell, then the home straight.
        int startToApproach = BoardConstants.YELLOW_APPROACH - BoardConstants.YELLOW_START;
        return 1 + startToApproach + approachToHome;
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

    // TurnGate is called once before and once after EVERY roll, including bonus rolls.
    private void executeTurn(Player player, List<Player> order) throws InterruptedException {
        player.resetConsecutiveSixes();
        boolean keepRolling;
        do {
            turnGate.beforeRoll(player.getColor());
            int roll = dice.roll();
            lastRoll = roll;
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
                turnGate.afterRoll(snapshot());
                return;
            }

            MoveResult result = processRoll(player, order, roll);
            keepRolling = (roll == BoardConstants.MOVE_FROM_BASE_ROLL || result.isCaptured())
                    && !player.hasAllPiecesHome();
            turnGate.afterRoll(snapshot());

        } while (keepRolling);
    }

    private MoveResult processRoll(Player player, List<Player> order, int roll) {
        List<Piece> all = allPieces(order);
        boolean rolledSix = (roll == BoardConstants.MOVE_FROM_BASE_ROLL);
        boolean hasPiecesAtBase = player.countPiecesAtBase() > 0;

        if (rolledSix && hasPiecesAtBase && moveDecider.prefersMoveFromBase(snapshot(), player.getColor())) {
            return activatePieceFromBase(player);
        }

        // Rule 7: if the chosen piece cannot move, ask the decider again without it.
        List<Rejection> rejections = new java.util.ArrayList<>();
        Piece chosen = askForPiece(player, roll, List.of());
        while (chosen != null) {
            if (chosen.isAtBase()) {
                if (rolledSix)
                    return activatePieceFromBase(player);
            } else {
                Rejection rejection = checkMove(player, chosen, roll, all);
                if (rejection == null)
                    return executeMove(player, chosen, chosen.applyEffect(roll), all);
                publish(rejection.event(), rejection.reason());
                rejections.add(rejection);
            }
            if (!moveDecider.triesOtherPiecesWhenBlocked(player.getColor()))
                break;
            chosen = askForPiece(player, roll, unavailablePieces(player, rejections, rolledSix));
        }
        return moveUpToBlockOrSkip(player, rejections);
    }

    // Asks the MoveDecider port to choose among the player's pieces that are not unavailable.
    private Piece askForPiece(Player player, int roll, List<Piece> unavailable) {
        List<Integer> candidates = player.getPieces().stream()
                .filter(p -> !unavailable.contains(p))
                .map(Piece::getPieceNumber)
                .collect(Collectors.toList());
        PieceChoice answer = moveDecider.choosePiece(snapshot(), player.getColor(), roll, candidates);
        answer.memo().ifPresent(memo -> deciderMemo.put(player.getColor(), memo));
        OptionalInt choice = answer.piece();
        if (choice.isEmpty())
            return null;
        if (!candidates.contains(choice.getAsInt()))
            throw new IllegalStateException(player.getColor().display() + " chose piece " + choice.getAsInt()
                    + ", which is not one of the candidates " + candidates);
        return player.getPieces().get(choice.getAsInt() - 1);
    }

    /** Why a chosen piece cannot make its full move, and where it could stop instead (T-3), if anywhere. */
    private record Rejection(GameEvent event, String reason, List<Piece> movers, int stopCell) {}

    private List<Piece> unavailablePieces(Player player, List<Rejection> rejections, boolean rolledSix) {
        List<Piece> unavailable = new java.util.ArrayList<>();
        rejections.forEach(r -> unavailable.addAll(r.movers()));
        for (Piece piece : player.getPieces()) {
            if (piece.isHome() || (piece.isAtBase() && !rolledSix))
                unavailable.add(piece);
        }
        return unavailable;
    }

    // Returns null when the piece (or the block it belongs to) can make its full move. No side effects.
    private Rejection checkMove(Player player, Piece piece, int roll, List<Piece> all) {
        String name = player.getColor().display() + " piece " + piece.getName();
        if (piece.isHome())
            return new Rejection(GameEvent.PIECE_CANNOT_MOVE, name + " is already Home.", List.of(piece), -1);
        if (hasBriefingEffect(piece))
            return new Rejection(GameEvent.PIECE_BRIEFING, name + " is in briefing and cannot move.", List.of(piece), -1);

        int steps = piece.applyEffect(roll);
        if (steps == 0)
            return new Rejection(GameEvent.PIECE_CANNOT_MOVE, name + " cannot move 0 cells.", List.of(piece), -1);

        List<Piece> block = movingBlock(piece, player.getPieces());
        if (block.size() < 2)
            return checkSingleMove(player, piece, steps, all);

        // T-4 first; if the block cannot move, T-5 lets the chosen piece leave it and move alone.
        Rejection blockRejection = checkBlockMove(player, block, steps, all);
        if (blockRejection == null)
            return null;
        Rejection aloneRejection = checkSingleMove(player, piece, steps, all);
        if (aloneRejection == null)
            return null;
        return blockRejection.stopCell() >= 0 ? blockRejection : aloneRejection;
    }

    private Rejection checkSingleMove(Player player, Piece piece, int steps, List<Piece> all) {
        String name = player.getColor().display() + " piece " + piece.getName();
        MoveTarget target = board.computeMoveTarget(piece, steps);
        boolean entersHomeStraight = !target.isMainPath() && piece.canEnterHomeStraightOnNextPass();
        if (target.isOvershoot() && entersHomeStraight)
            return new Rejection(GameEvent.PIECE_CANNOT_MOVE,
                    name + " cannot move - exact roll required to reach Home.", List.of(piece), -1);
        if (entersHomeStraight || !board.pathCrossesBlock(piece, steps, all))
            return null;

        int landing = board.advance(piece.getMainPathPosition(), steps, piece.getDirection());
        int blockCell = board.findBlockCell(piece, steps, all);
        int stopCell = board.findCellBeforeBlock(piece, steps, all);
        String reason = name + " is blocked from moving from " + piece.getMainPathPosition()
                + " to " + landing + " by " + describeBlocker(player, blockCell, all) + ".";
        return new Rejection(GameEvent.PIECE_BLOCKED, reason, List.of(piece),
                stopCell == piece.getMainPathPosition() ? -1 : stopCell);
    }

    private String describeBlocker(Player player, int blockCell, List<Piece> all) {
        return board.opponentPiecesAtCell(player.getColor(), blockCell, all).stream()
                .findFirst()
                .map(p -> p.getColor().display() + " piece " + p.getPieceNumber())
                .orElse("an opponent");
    }

    // T-3: no other piece can move, so the first blocked piece (or block) moves up to the cell before the block.
    private MoveResult moveUpToBlockOrSkip(Player player, List<Rejection> rejections) {
        String colour = player.getColor().display();
        if (rejections.isEmpty()) {
            publish(GameEvent.TURN_SKIPPED, colour + " has no movable piece. Turn skipped.");
            return MoveResult.builder().moved(false).build();
        }
        String noOtherPiece = moveDecider.triesOtherPiecesWhenBlocked(player.getColor())
                ? " does not have other pieces in the board to move instead of the blocked piece."
                : " keeps to its cycle and does not move another piece instead.";
        for (Rejection rejection : rejections) {
            if (rejection.stopCell() >= 0) {
                rejection.movers().forEach(p -> p.setMainPathPosition(rejection.stopCell()));
                publish(GameEvent.PIECE_BLOCKED, colour + noOtherPiece + " Moved the piece to square "
                        + rejection.stopCell() + " which is the cell before the block.");
                return MoveResult.builder().moved(true).build();
            }
        }
        publish(GameEvent.TURN_SKIPPED, colour + noOtherPiece
                + " Ignoring the throw and moving on to the next player.");
        return MoveResult.builder().moved(false).build();
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

    // Only called after checkMove has confirmed the full move is allowed.
    private MoveResult executeMove(Player player, Piece piece, int steps, List<Piece> all) {
        List<Piece> block = movingBlock(piece, player.getPieces());
        if (block.size() >= 2) {
            if (checkBlockMove(player, block, steps, all) == null) {
                return executeBlockMove(player, block, steps, all);
            }
            publish(GameEvent.PIECE_MOVED, player.getColor().display() + " block at cell "
                    + piece.getMainPathPosition() + " cannot move, so piece " + piece.getName()
                    + " leaves the block and moves " + piece.getDirection().display() + " on its own (Rule T-5).");
        }

        MoveTarget target = board.computeMoveTarget(piece, steps);
        if (target.isMainPath()) {
            return landOnMainPath(player, piece, target.getPosition(), steps, all);
        }

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
        recordProgress();
        publish(GameEvent.PIECE_REACHED_HOME, player.getColor().display() + " piece " + piece.getName() + " has reached Home!");
        checkFinish(player);
        return MoveResult.builder().moved(true).reachedHome(true).build();
    }

    // Blocks in the path were already ruled out by checkMove (partial moves: moveUpToBlockOrSkip).
    private MoveResult landOnMainPath(Player player, Piece piece, int targetCell,
            int steps, List<Piece> all) {
        if (board.hasFriendlyPieceAt(player.getColor(), targetCell, piece, all)) {
            return formBlock(player, piece, targetCell, steps, all);
        }

        String fromLabel = piece.positionLabel();
        piece.setMainPathPosition(targetCell);
        publish(GameEvent.PIECE_MOVED, player.getColor().display() + " moves piece " + piece.getName()
                + " from location " + fromLabel + " to " + targetCell
                + " by " + steps + " units in " + piece.getDirection().display() + " direction.");

        // A single piece never lands on an opposing block: checkMove treats that as blocked.
        List<Piece> opponents = board.opponentPiecesAtCell(player.getColor(), targetCell, all);
        boolean captured = false;
        if (opponents.size() == 1) {
            captured = captureOpponent(player, piece, opponents.get(0), targetCell);
        }

        if (board.getMysteryCell().isAt(targetCell)) {
            handleMysteryCell(player, piece, all);
            return MoveResult.builder().moved(true).captured(captured).landedOnMystery(true).build();
        }

        return MoveResult.builder().moved(true).captured(captured).build();
    }

    /**
     * T-4: the chosen piece plus every own piece on the same main-path cell (chosen piece first).
     * A piece on its own approach cell moves on its own, so a block can reach its home straight.
     */
    private List<Piece> movingBlock(Piece piece, List<Piece> ownPieces) {
        List<Piece> block = new java.util.ArrayList<>(List.of(piece));
        if (!piece.isOnMainPath() || piece.getMainPathPosition() == piece.approachPosition())
            return block;
        ownPieces.stream()
                .filter(p -> p != piece)
                .filter(Piece::isOnMainPath)
                .filter(p -> p.getMainPathPosition() == piece.getMainPathPosition())
                .forEach(block::add);
        return block;
    }

    // T-4: the block follows the piece with the longest distance still to go (first piece on a tie).
    private Direction blockDirection(List<Piece> block) {
        return block.stream()
                .max(Comparator.comparingInt(p -> board.distanceToApproach(p)))
                .map(Piece::getDirection)
                .orElseThrow();
    }

    // Steps each block piece takes: roll / block size, stopping on its own approach cell.
    private int blockStepsEach(List<Piece> block, int steps, Direction direction) {
        Piece lead = block.get(0);
        int toApproach = direction == Direction.CLOCKWISE
                ? Math.floorMod(lead.approachPosition() - lead.getMainPathPosition(), BoardConstants.MAIN_PATH_SIZE)
                : Math.floorMod(lead.getMainPathPosition() - lead.approachPosition(), BoardConstants.MAIN_PATH_SIZE);
        return Math.min(steps / block.size(), toApproach);
    }

    // How far the block can go before an opposing block stops it (T-3). It may land on a same-size block (T-8).
    private int blockFreeSteps(List<Piece> block, int stepsEach, Direction direction, List<Piece> all) {
        Piece lead = block.get(0);
        for (int i = 1; i <= stepsEach; i++) {
            int cell = board.advance(lead.getMainPathPosition(), i, direction);
            int opponents = board.opponentPiecesAtCell(lead.getColor(), cell, all).size();
            boolean capturable = i == stepsEach && opponents == block.size();
            if (opponents >= 2 && !capturable)
                return i - 1;
        }
        return stepsEach;
    }

    private Rejection checkBlockMove(Player player, List<Piece> block, int steps, List<Piece> all) {
        Piece lead = block.get(0);
        String blockName = player.getColor().display() + " block at cell " + lead.getMainPathPosition();
        if (block.stream().anyMatch(this::hasBriefingEffect))
            return new Rejection(GameEvent.PIECE_BRIEFING,
                    blockName + " cannot move because one of its pieces is in briefing.", block, -1);

        Direction direction = blockDirection(block);
        int stepsEach = blockStepsEach(block, steps, direction);
        if (stepsEach == 0)
            return new Rejection(GameEvent.PIECE_CANNOT_MOVE, blockName
                    + " cannot move — dice value too small for block of size " + block.size() + ".", block, -1);

        int freeSteps = blockFreeSteps(block, stepsEach, direction, all);
        if (freeSteps == stepsEach)
            return null;
        int blockCell = board.advance(lead.getMainPathPosition(), freeSteps + 1, direction);
        String reason = blockName + " is blocked from moving to "
                + board.advance(lead.getMainPathPosition(), stepsEach, direction)
                + " by " + describeBlocker(player, blockCell, all) + ".";
        return new Rejection(GameEvent.PIECE_BLOCKED, reason, block,
                freeSteps == 0 ? -1 : board.advance(lead.getMainPathPosition(), freeSteps, direction));
    }

    // T-4 / T-5: the whole block moves; each piece keeps its own direction for when it leaves the block.
    private MoveResult executeBlockMove(Player player, List<Piece> block, int steps, List<Piece> all) {
        Direction direction = blockDirection(block);
        int stepsEach = blockStepsEach(block, steps, direction);
        int fromCell = block.get(0).getMainPathPosition();
        int toCell = board.advance(fromCell, stepsEach, direction);

        block.forEach(p -> p.setMainPathPosition(toCell));
        publish(GameEvent.PIECE_MOVED, player.getColor().display() + " block moves from cell " + fromCell
                + " to cell " + toCell + " by " + stepsEach + " units each in "
                + direction.display() + " direction (Rule T-4).");

        boolean captured = captureWithBlock(player, block, toCell, all);
        return MoveResult.builder().moved(true).captured(captured).build();
    }

    // T-8: a block captures a single piece, or an opposing block of the same size; every capturing piece counts it.
    private boolean captureWithBlock(Player player, List<Piece> block, int cell, List<Piece> all) {
        List<Piece> captured = board.opponentPiecesAtCell(player.getColor(), cell, all);
        if (captured.isEmpty())
            return false;
        publish(GameEvent.PIECE_CAPTURED, player.getColor().display() + " block lands on square " + cell
                + " and captures " + captured.size() + " opponent piece(s).");
        for (Piece target : captured) {
            target.resetToBase();
            publish(GameEvent.PIECE_CAPTURED, target.getColor().display() + " piece " + target.getName()
                    + " returned to base.");
        }
        block.forEach(Piece::incrementCaptureCount);
        recordProgress();
        return true;
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
        recordProgress();
        publish(GameEvent.PIECE_CAPTURED, capturer.getColor().display() + " player now has "
                + capturer.countPiecesOnBoard() + "/4 on pieces on the board and "
                + capturer.countPiecesAtBase() + "/4 pieces on the base.");
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

    // T-6: in every block, all pieces but the first move 6 cells in their own direction (T-5).
    void handleTripleSixRule(Player player) {
        for (List<Piece> block : player.getBlocks()) {
            for (int i = 1; i < block.size(); i++) {
                Piece toDisplace = block.get(i);
                int newPos = board.advance(toDisplace.getMainPathPosition(), 6, toDisplace.getDirection());
                toDisplace.setMainPathPosition(newPos);
                publish(GameEvent.PIECE_MOVED, player.getColor().display() + " blockade broken: piece "
                        + toDisplace.getName() + " moved to cell " + newPos + ".");
            }
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
        if (board.getMysteryCell().isJustSpawned()) {
            board.getMysteryCell().clearJustSpawned();
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

    // Progress = a capture or a piece reaching Home; used by the stalemate rule.
    private void recordProgress() {
        lastProgressRound = roundNumber;
    }

    private int rankedPlayers() {
        return (int) players.stream().filter(p -> p.getFinishPosition() > 0).count();
    }

    private void publish(GameEvent event, String message) {
        observers.forEach(listener -> listener.onEvent(event, message));
    }
}
