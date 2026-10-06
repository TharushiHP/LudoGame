package ludo.shared.protocol;

import ludo.shared.json.JsonWriter;
import ludo.shared.snapshot.GameSnapshot;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Fingerprint of a game state: SHA-256 of the snapshot's canonical JSON (UTF-8), as lowercase hex.
 * The server sends it with every STATE and each client recomputes it from the snapshot it received;
 * equal hashes prove all four clients hold the same state. Because {@link SnapshotCodec} fixes the
 * field and colour order, identical snapshots give identical hashes on any machine.
 */
public final class StateHasher {

    private StateHasher() {}

    public static String hash(GameSnapshot snapshot) {
        return sha256Hex(JsonWriter.write(SnapshotCodec.toJson(snapshot)));
    }

    static String sha256Hex(String text) {
        try {
            // A new digest per call: MessageDigest is not thread-safe, and this is called from many threads.
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest)
                hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("every Java platform must support SHA-256", e);
        }
    }
}
