package ludo.server.coordinator;

import ludo.shared.snapshot.GameSnapshot;

/**
 * The last STATE a game broadcast: version, snapshot and hash (Value Object). It is immutable,
 * so the game thread can publish it through a volatile field and any HTTP thread can read it
 * for GET /state without a lock and without going through the command queue.
 */
public record StateView(long version, GameSnapshot snapshot, String hash) {
}
