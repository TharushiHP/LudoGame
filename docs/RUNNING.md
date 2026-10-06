# Running LUDO-T

LUDO-T runs as one **coordinator server** and four **thick clients** (Red, Green, Yellow, Blue). The server owns the only real board. Each client runs its own player's strategy and shows the board the server sends. It is still a simulation: nobody plays by hand. The windows only let you pick a game, a colour and a name, and then watch.

Requirements on every PC: **Java 17** (`java -version`). Building needs no Maven install, because the Maven Wrapper (`mvnw.cmd`) downloads it.

## 1. Build

On the PC that has the source code:

```
.\mvnw.cmd clean package
```

This runs all the tests and produces two runnable jars:

| Jar | What it is |
|---|---|
| `ludo-server\target\ludo-server.jar` | the coordinator server |
| `ludo-client\target\ludo-client.jar` | the thick client (Swing window, or console with `--headless`) |

The other PCs only need a copy of `ludo-client.jar`. It contains everything (ludo-shared and ludo-players are shaded in).

## 2. Demo on one PC

```
scripts\start-demo.bat
```

The script:
1. builds the jars if they are missing (tests skipped),
2. starts the server in its own window, "LUDO-T server" (`--turn-delay=500`), and also saves its output to `logs\server.log`,
3. waits until `GET http://localhost:8080/health` answers,
4. creates game 1 with seed 7 (`POST /games {"seed":7}`),
5. opens **the game window**, a maximised spectator view of game 1 (it opens first, so it shows the game from the first move),
6. starts the four players, Red, Green, Yellow and Blue, as separate client applications **without a window** (`--headless`). Each runs in its own minimised console ("LUDO-T Red player" and so on), which prints that player's log.

It refuses to start if a server is already running on port 8080, because the new game would then not be game 1. To stop the demo, close the game window and the four player consoles, then press Ctrl+C in the server window.

Where to look:
- **Game window:** the game as a player of a real Ludo app sees it (section 4).
- **Server console** (and `logs\server.log`): the technical record. Every request, state change and ACK, which thread handled it, the move timeouts and the game's own console messages.
- **Player consoles:** each player's own copy of the log. All four are identical, because they apply the same STATEs in the same order.

## 3. Several PCs (LAN)

### PC 1: the server

1. Find the PC's LAN address: run `ipconfig` and read the **IPv4 Address** of the Wi-Fi or Ethernet adapter, e.g. `192.168.1.20`.
2. Allow other PCs to connect to port 8080. In an **administrator** command prompt:
   ```
   netsh advfirewall firewall add rule name="LUDO-T 8080" dir=in action=allow protocol=TCP localport=8080
   ```
   (Or use *Windows Defender Firewall → Advanced settings → Inbound Rules → New Rule → Port → TCP 8080 → Allow*.) If Windows shows a firewall pop-up the first time Java listens, allow it for **private** networks.
3. Start the server:
   ```
   java -jar ludo-server\target\ludo-server.jar --port=8080 --turn-delay=500 --move-timeout=10000
   ```
   All options are optional (values in ms). The log shows every request, state change and ACK, with the name of the thread that handled it.

You can create a game here with `curl.exe -X POST -H "Content-Type: application/json" -d "{\"seed\":7}" http://localhost:8080/games`, or from any client's connect window ("New game...").

### PCs 2 to 5: the clients

```
java -jar ludo-client.jar
```

The **connect window** opens:
1. **Server:** enter `http://<LAN-IP>:8080` (e.g. `http://192.168.1.20:8080`) and press **Refresh**. The table lists the server's games (state, how many joined, which colours are taken, seed, turn delay).
2. No game yet? Press **New game...** (seed and turn delay are optional).
3. Optionally enter **Your name** (default "Player Red" and so on).
4. Pick the game, then one of the two choices:
   - **Watch game**: opens the game window as a spectator, with animations.
   - **Play as Red / Green / Yellow / Blue**: joins that colour. Colours already taken are pale and disabled. Once a game has started, you can only watch.

The game starts as soon as all four colours have joined.

**A player with a window** (e.g. one laptop per player) sees the same game window as a spectator, with **YOU** on its own player box. One difference: every state is drawn **at once**, with no token walk and no dice tumble. That player ACKs each state as soon as it is on screen, so its screen must never lag behind the game. Banners and toasts still appear. Players can also run without a window (`--headless`), as in the demo.

To skip the connect window, give the game and colour on the command line:

```
java -jar ludo-client.jar --server=http://192.168.1.20:8080 --game=1 --colour=RED --name="Player A"
```

### Client options

| Option | Meaning |
|---|---|
| `--server=URL` | server address (default `http://localhost:8080`) |
| `--game=ID` | game to join |
| `--colour=RED\|GREEN\|YELLOW\|BLUE\|SPECTATOR` | colour to play (not case-sensitive); `SPECTATOR` only watches |
| `--name=TEXT` | name shown in the server log |
| `--headless` | no window: prints the game log and status to the console. Needs `--game` and `--colour`. |

Examples:

```
java -jar ludo-client.jar --game=1 --colour=SPECTATOR
java -jar ludo-client.jar --game=1 --colour=BLUE --headless
```

A spectator never sends ROLL, DECISION or ACK, so any number of them can watch without slowing the game down.

## 4. What you see in the game window

One window, like a real Ludo app. It fits the screen (Windows display scaling included), and the board grows and shrinks with the window.

- **The board** is Figure 1 of the brief:
  - Green, Yellow, Red and Blue bases in the four corners, each with a white diamond, a dashed inner diamond and a cross. A waiting token sits on its own arm of the cross (piece 1 top, 2 right, 3 bottom, 4 left).
  - White path cells, each colour's **X** start cell and approach circle, the coloured home straights, and Home in the centre (four triangles labelled "Home").
  - The LUDO-T extras are kept small: **α β γ** in the corner of the Alpha, Beta and Gamma cells, and the **mystery cell** glowing purple with a "?" and the rounds it has left.
- **Tokens** are glossy pieces numbered 1-4. Small badges show LUDO-T state on the piece itself:

  | Badge | Meaning |
  |---|---|
  | ↻ / ↺ (top-right) | travels clockwise / counterclockwise (coin toss, T-1) |
  | gold dot (top-left) | has captured at least once, so it may enter its home straight (T-7) |
  | ⚡ orange (bottom-right) | energised: double speed |
  | ½ green (bottom-right) | sick: half speed |
  | ❚❚ grey (bottom-right) | in a briefing: cannot move |
  | stack with "×N" | a block of N tokens |

  **Hover** over a token for its name, location, direction, captures and effect with rounds left. Hover over a cell for its number and meaning (X, approach, Alpha/Beta/Gamma, mystery).
- **Player boxes** sit outside each corner, next to that player's base:
  - "Red · Aggressive", a dice and the player's Home progress.
  - The player whose turn it is **glows** and its dice shows the roll; the others are dimmed.
  - Tags: **YOU** (your colour, in a player window), **computer** (the server took over that colour), **1st place** and so on.
- **Animations** (game window as spectator only):
  - the dice tumbles before showing the roll
  - tokens walk cell by cell
  - mystery jumps flash, and captured tokens fade back to their base
  - a new state arriving mid-move snaps to it at once
- **Banners** pop up over the board for about 1.5 s on a capture, a mystery teleport, an Alpha/Beta/Gamma effect, a block, a token reaching Home, and a player finishing.
- **Toasts** appear only for problems: "Reconnecting...", "Waiting for Blue..." (the server paused for that player), "Blue is now played by the computer".
- **Winner screen:** at the end of the game, a podium shows places 1-3, with 4th beneath, and how the game ended.

### Developer view: F2

Press **F2** in the game window to show or hide a panel on the right. It is off by default. It shows:
- the connection state
- whether this client's SHA-256 of the state equals the server's (✓ synced / ✖ out of sync, with the hash)
- the Version, Turn and Round
- the game log, exactly as the server sent it

This client's own warnings, e.g. a refused request, are listed there marked "!". The F2 view of every client shows the same version, hash and log at the same moment.

## 5. Troubleshooting

| Problem | Cause and fix |
|---|---|
| "Cannot reach http://..." in the connect window | Wrong address, server not running, or the firewall blocks port 8080. Test from the client PC with `curl.exe http://<LAN-IP>:8080/health`, which should answer `{"status":"UP",...}`. |
| Works on the server PC but not from others | Firewall rule missing (section 3), or the network is set to *Public*. Use a private network or add the rule for all profiles. |
| `start-demo.bat` says a server is already running | Close the old server window (or end its `java.exe`) first. |
| `Address already in use` when starting the server | Another program uses 8080. Start with `--port=8081` and use that port in the clients. |
| "Red has already joined this game; continuing as a reconnecting client" (F2 view of a Red window, or the Red console) | That colour joined before, usually because the client was restarted. It carries on watching and playing that colour. |
| Toast "Waiting for Blue..." | Blue's client did not roll or decide within `--move-timeout` (default 10 s). It resumes when Blue answers or reconnects. After 30 s more, the server plays Blue for the rest of the game: toast "Blue is now played by the computer" and a **computer** tag on Blue's box. |
| Toast "Reconnecting..." | The event stream dropped. The client retries by itself (0.5 s, 1 s, 2 s, 4 s, then every 5 s) and gets the current state on reconnect ("Reconnected"). |
| F2 view says "✖ OUT OF SYNC" | The client's hash differed from the server's. The client ACKs with its own hash, the server answers by sending the state again, and it should say "✓ synced" on the next state. |
| Small screen | The window always fits the usable screen area, and the board and player boxes scale with it. Resize or maximise freely. |
