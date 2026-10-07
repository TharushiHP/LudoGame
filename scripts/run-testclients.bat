@echo off
rem LUDO-T concurrency evidence: fast automatic test clients against a separate server.
rem   1. builds the jars if they are missing
rem   2. starts a load-test server on port 8090 in its own window (turn delay 0, no next game);
rem      its output is also saved to logs\server-load.log
rem   3. waits until GET /health answers
rem   4. burst:  2 test clients fire 200 asynchronous requests each at one game while 4 automatic
rem              players play it; a spectator game window opens on that game so it can be watched
rem   5. play:   4 games at the same time, 4 fast automatic clients each
rem   6. create: 4 clients create 25 games each at the same time
rem Each scenario prints a summary and saves it as logs\testclients-<scenario>-<time>.txt.
rem See docs\TESTING.md for what each check proves.
setlocal
cd /d "%~dp0.."

set PORT=8090
set SERVER=http://localhost:%PORT%
set SERVER_JAR=ludo-server\target\ludo-server.jar
set CLIENT_JAR=ludo-client\target\ludo-client.jar
set TEST_JAR=ludo-testclients\target\ludo-testclients.jar

rem A server that is already running would have other games and other settings.
curl.exe -s -o nul %SERVER%/health
if not errorlevel 1 (
    echo A server is already running on port %PORT%. Close it first, then run this script again.
    exit /b 1
)

if not exist "%SERVER_JAR%" goto build
if not exist "%CLIENT_JAR%" goto build
if not exist "%TEST_JAR%" goto build
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
echo Starting the load-test server on port %PORT%...
start "LUDO-T load server" powershell -NoProfile -NoExit -Command "java -jar %SERVER_JAR% --port=%PORT% --turn-delay=0 --rematch-delay=0 | Tee-Object -FilePath logs\server-load.log"

set TRIES=0
:wait_for_server
curl.exe -s -o nul %SERVER%/health
if not errorlevel 1 goto server_up
set /a TRIES+=1
if %TRIES% geq 30 (
    echo The server did not start within 30 s. Check the "LUDO-T load server" window.
    exit /b 1
)
ping -n 2 127.0.0.1 >nul
goto wait_for_server

:server_up
set FAILED=0

echo.
echo === burst: 2 test clients x 200 requests at one game (spectator window opens) ===
rem A turn delay of 150 ms on this one game and waves of 10 (20 waves per client), so the burst
rem lasts long enough to be watched in the window.
java -jar "%TEST_JAR%" --server=%SERVER% --scenario=burst --clients=2 --requests=200 --wave-size=10 --turn-delay=150 --watch="%CLIENT_JAR%"
if errorlevel 1 set FAILED=1

echo.
echo === play: 4 games at the same time, 4 fast clients each ===
java -jar "%TEST_JAR%" --server=%SERVER% --scenario=play --games=4 --seed=11
if errorlevel 1 set FAILED=1

echo.
echo === create: 4 clients x 25 games at the same time ===
java -jar "%TEST_JAR%" --server=%SERVER% --scenario=create --clients=4 --creates=25
if errorlevel 1 set FAILED=1

echo.
echo Summaries:  logs\testclients-*.txt
dir /b /o-d logs\testclients-*.txt
echo Server log: logs\server-load.log  (lines "queue=N" with N above 0 show requests waiting in a game's queue)
if %FAILED%==1 (
    echo At least one check FAILED: see the summaries above.
) else (
    echo Every check passed.
)
echo The load server keeps running in its own window; close it when you are done.
endlocal
