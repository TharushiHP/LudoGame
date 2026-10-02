# Changes from Assignment 1

Every change to Assignment 1 behaviour, design-pattern usage or labelling is recorded here with the rule it relates to and the reason.

## Task 2: gameplay bug fixes

| # | What changed | Rule | Why |
|---|---|---|---|
| 1 | The game now ends as soon as only one player still has pieces out. That player is ranked last automatically (`Game.rankLastPlayer`), and the GAME OVER summary lists all four places. | Rule 11 | Under T-7 a piece needs a capture before it can enter the home straight. Once three players had finished, the last player had nobody left to capture, so its pieces circled until the 500-round cap. |
| 1b | The 500-round cap is now only a safety net. Reaching it prints `WARNING: the safety limit of 500 rounds was reached...`, and unfinished players are listed as `Not ranked`. | Rule 11 | Previously a capped game looked like a normal finish. |
| 1c | The game prints `Game finished after R rounds and T turns.` at the end. A turn is one player's go, including extra rolls after a six or a capture. `Game` exposes `getRoundNumber()`, `getTurnCount()` and `isRoundCapReached()`. | (none) | Needed for the game-length statistics and for tests. |
| 2 | Blue's cycle skips pieces that are Home, or still at base, and picks the next piece in cycle order that is on the board. The cycle then continues after the piece actually chosen. | Blue behaviour | A1 fell back to "the first piece on the board", so after a skip B1 was chosen again and B3/B4 were starved. Behaviour kept: if the cycle's piece is on the board but cannot move, Blue's turn is skipped. |
| 3 | `BriefingEffect` sends the piece to base after **three** consecutive 3s (was two). | T-13 | A1 used `>= 2`, which contradicted both the rule and A1's own log message ("rolled three consecutively"). |
| 3b | The briefing count is updated on **every** roll the player makes (`Player.recordRollForBriefing`, called from `Game.executeTurn`). Choosing a briefing piece now prints `... is in briefing and cannot move. Turn skipped.` | T-13 | A1 only counted the roll when the strategy happened to choose the briefing piece, so most rolls were ignored. |
| 4 | Each `publish` call now passes its real `GameEvent` type. New types: `ROUND_START`, `TURN_SKIPPED`, `BLOCK_FORMED`, `PIECE_REACHED_HOME`, `LAST_PLAYER_RANKED`, `GAME_OVER`. | Observer pattern (corrected) | A1 tagged every event as `DICE_ROLLED`, so the Observer pattern carried no information beyond the text. Listeners such as the future GUI and network broadcast need the real type. |
| 5a | Removed the special case in `Board.computeMoveTarget` that sent a clockwise piece from its approach cell straight to Home on a roll of 5. From the approach cell, rolls 1 to 5 now reach homepath0 to homepath4, and 6 reaches Home. Overshooting is still not allowed. | Rule 10 | The special case skipped homepath4. It also disagreed with moves that start before the approach cell (47 + 8 gave homepath4, 50 + 5 gave Home). The A1 test `exactStepsToHomeFromApproachReachesHome` asserted the wrong value (5); it now uses 6. |
| 5b | Leaving base now prints the coin-toss result, e.g. `... to the starting point. The coin toss sets its direction to counterclockwise.` | T-1 | The direction was decided but never shown. |
| 5c | The mystery-cell status line is printed after the board counts the round, and says `rounds` instead of `values`. It now counts down 3, 2, 1 after the spawn message ("next four rounds"). In a round where the cell spawns, only the spawn message is printed. | Mystery cell | A1 printed the count before decrementing, so it showed 4, 3, 2, 1 when 3, 2, 1, 0 rounds were really left. |
| 5d | No change. Verified by tests that Red brings out another piece on a six only when that six cannot capture, and always brings one out when it has no piece on the path. | Red behaviour | Already correct. |
| 6 | Optional `--seed=<number>` argument (`Main.parseSeed`, `GameBuilder.withSeed`). Without it a random seed is chosen and printed as `Seed: N (replay this game with --seed=N)`. | (none) | Lets any game be replayed exactly, for debugging, statistics and the demo. |

## Javadoc added
`Game`, `GameEvent`, `GameBuilder`, `Main`, `Player`, `BriefingEffect` and `CyclicStrategy` now have class Javadoc (CLAUDE.md working rule). No behaviour change.

## Open issues found (not yet fixed)
- **Mutual blockade deadlock.** Two opposite-direction blocks can sit on neighbouring cells (e.g. Blue block on 2 moving counterclockwise, Yellow block on 1 moving clockwise), each stopping the other. Strategies keep choosing the blocked piece, and `Game` never tries another one, even though the message says "does not have other pieces ... to move". Play freezes until a triple six breaks a block. Blocked throws dominate the late game in 10 of the 11 capped games in the 100-seed run. Seed 4 reproduces it (rounds ~150 to ~400). Fixing it needs a rules decision.
- **Slow circling.** Seed 11 hits the cap without a deadlock: pieces keep moving but never finish, probably because T-7 requires a capture before the home straight.
