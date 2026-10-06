package ludo.shared.json;

/**
 * Thrown when JSON text cannot be parsed, or a parsed object lacks a field or has the wrong type.
 * Unchecked, so codecs stay readable; the server turns it into HTTP 400.
 */
public class JsonException extends RuntimeException {

    public JsonException(String message) {
        super(message);
    }
}
