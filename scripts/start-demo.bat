@echo off
rem LUDO-T demo on one PC: the coordinator server plus the four thick clients.
rem   1. builds the jars if they are missing
rem   2. starts the server in its own window (output also saved to logs\server.log); 10 s after
rem      a game ends it starts the next one by itself (same four players, new seed)
rem   3. waits until GET /health answers
rem   4. creates game 1 with seed 7
rem   5. opens the game window (spectator), then starts the four players headless
rem For several PCs see docs\RUNNING.md.
setlocal
cd /d "%~dp0.."

set PORT=8080
set SERVER=http://localhost:%PORT%
set SERVER_JAR=ludo-server\target\ludo-server.jar
set CLIENT_JAR=ludo-client\target\ludo-client.jar

rem A running server would already have games, so the new game would not get id 1.
curl.exe -s -o nul %SERVER%/health
if not errorlevel 1 (
    echo A server is already running on port %PORT%. Close it first, then run this script again.
    exit /b 1
)

if not exist "%SERVER_JAR%" goto build
if not exist "%CLIENT_JAR%" goto build
goto start_server

:build
echo Building the jars (tests skipped)...
call mvnw.cmd -q -DskipTests package
if errorlevel 1 (
    echo Build failed.
    exit /b 1
)

:start_server
if not exist logs mkdir logs
echo Starting the coordinator server on port %PORT%...
start "LUDO-T server" powershell -NoProfile -NoExit -Command "java -jar %SERVER_JAR% --port=%PORT% --turn-delay=500 --rematch-delay=10000 | Tee-Object -FilePath logs\server.log"

rem Poll /health once a second, for at most 30 s (ping is used as the delay: it also works without a console).
set TRIES=0
:wait_for_server
curl.exe -s -o nul %SERVER%/health
if not errorlevel 1 goto server_up
set /a TRIES+=1
if %TRIES% geq 30 (
    echo The server did not start within 30 s. Check the "LUDO-T server" window.
    exit /b 1
)
ping -n 2 127.0.0.1 >nul
goto wait_for_server

:server_up
echo Server is up. Creating game 1 (seed 7)...
curl.exe -s -X POST -H "Content-Type: application/json" -d "{\"seed\":7}" %SERVER%/games
echo.

rem The game window is a spectator (it never ACKs, so its animations never slow the game).
rem It opens first so it shows the game from the very first move.
echo Opening the game window (spectator)...
start "" javaw -jar "%CLIENT_JAR%" --server=%SERVER% --game=1 --colour=SPECTATOR --name=Viewer
ping -n 3 127.0.0.1 >nul

rem The four players are separate client applications without a window (--headless), each in its
rem own minimised console that prints its log. --colour is not case-sensitive.
for %%C in (Red Green Yellow Blue) do (
    echo Starting the %%C player...
    start "LUDO-T %%C player" /min java -jar "%CLIENT_JAR%" --server=%SERVER% --game=1 --colour=%%C --name="Player %%C" --headless
    ping -n 2 127.0.0.1 >nul
)

echo.
echo Server, game window and four players started. The game begins when all four have joined.
echo Server log: logs\server.log
endlocal
