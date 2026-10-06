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
5. starts the Red, Green, Yellow and Blue clients for game 1, one second apart.

It refuses to start if a server is already running on port 8080, because the new game would then not be game 1. To stop the demo, close the four client windows and press Ctrl+C in the server window.

The four client windows open on top of each other. Drag them apart to compare them side by side.

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
3. Pick the game, then a colour under **Play as:**. Colours already taken are greyed out. Once a game has started, you can only **Spectate**.
4. Optionally enter a **Name** (default "Player Red" and so on), then press **Connect**.

The game starts as soon as all four colours have joined.

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
| `--name=TEXT` | name shown in the header and the server log |
| `--headless` | no window: prints the game log and status to the console. Needs `--game` and `--colour`. |

Examples:

```
java -jar ludo-client.jar --game=1 --colour=SPECTATOR
java -jar ludo-client.jar --game=1 --colour=BLUE --headless
```

A spectator never sends ROLL, DECISION or ACK, so any number of them can watch without slowing the game down.

## 4. What you see in a client window

- **Header:** "You are Player Red (Red)" (or "Spectator"); "Turn N · Round R · Version V"; whose turn it is and the last roll; a badge that is green **✔ Synced** when this client's SHA-256 of the state equals the server's, or red **✖ Out of sync** when it does not. Hover over the badge to see the hashes.
- **Board:** the 52-cell path, X (start) cells, approach cells (coloured circles), home straights, bases and the centre home. α/β/γ mark the special cells, and the purple **?** is the mystery cell. Pieces are circles labelled R1, G2 and so on. Two or more pieces on one cell are drawn as a block with a "×N" badge. Hover over a cell for its number and name.
- **Right column:** one panel per player: base/board/home counts, where each piece is, captures, active effects and rounds left, finishing place, and "played by server" if the server has taken over that colour.
- **Bottom:** the game log, exactly the lines the server sent (the same as the console game). Below it, the status bar: Connected / Reconnecting..., a yellow "Paused: waiting for Blue" banner, and the final places when the game is over.

All four windows show the same version number and the same log at the same time. The server waits for every client's ACK before the next roll.

## 5. Troubleshooting

| Problem | Cause and fix |
|---|---|
| "Cannot reach http://..." in the connect window | Wrong address, server not running, or the firewall blocks port 8080. Test from the client PC with `curl.exe http://<LAN-IP>:8080/health`, which should answer `{"status":"UP",...}`. |
| Works on the server PC but not from others | Firewall rule missing (section 3), or the network is set to *Public*. Use a private network or add the rule for all profiles. |
| `start-demo.bat` says a server is already running | Close the old server window (or end its `java.exe`) first. |
| `Address already in use` when starting the server | Another program uses 8080. Start with `--port=8081` and use that port in the clients. |
| "Red has already joined this game; continuing as a reconnecting client" | That colour joined before, usually because the client was restarted. It carries on watching and playing that colour. |
| Yellow banner "Paused: waiting for Blue" | Blue's client did not roll or decide within `--move-timeout` (default 10 s). It resumes when Blue answers or reconnects. After 30 s more, the server plays Blue for the rest of the game ("played by server"). |
| Status bar says "Reconnecting..." | The event stream dropped. The client retries by itself (0.5 s, 1 s, 2 s, 4 s, then every 5 s) and gets the current state on reconnect. |
| Red "Out of sync" badge | The client's hash differed from the server's. The client ACKs with its own hash, the server answers by sending the state again, and the badge should turn green on the next state. |
| Window too big | On small or scaled screens the window shrinks to fit, and the board scales with it. Drag the divider above the log to give the board more room. |
