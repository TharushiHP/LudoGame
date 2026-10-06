package ludo.shared.json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses JSON text into plain Java values (recursive descent): objects become LinkedHashMap
 * (field order kept), arrays ArrayList, strings String, whole numbers Long, other numbers Double,
 * plus Boolean and null. Anything malformed, including text after the value, throws
 * {@link JsonException} with the position. Hand-written because the project allows no JSON library.
 */
public final class JsonParser {

    private final String text;
    private int pos;

    private JsonParser(String text) {
        this.text = text;
    }

    public static Object parse(String text) {
        if (text == null)
            throw new JsonException("no JSON text");
        JsonParser parser = new JsonParser(text);
        parser.skipWhitespace();
        Object value = parser.readValue();
        parser.skipWhitespace();
        if (parser.pos != text.length())
            throw parser.error("unexpected text after the JSON value");
        return value;
    }

    /** Parses text that must be a JSON object. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String text) {
        Object value = parse(text);
        if (!(value instanceof Map))
            throw new JsonException("expected a JSON object");
        return (Map<String, Object>) value;
    }

    private Object readValue() {
        if (pos >= text.length())
            throw error("unexpected end of input");
        char c = text.charAt(pos);
        return switch (c) {
            case '{' -> readObject();
            case '[' -> readArray();
            case '"' -> readString();
            case 't' -> readLiteral("true", Boolean.TRUE);
            case 'f' -> readLiteral("false", Boolean.FALSE);
            case 'n' -> readLiteral("null", null);
            default -> {
                if (c == '-' || (c >= '0' && c <= '9'))
                    yield readNumber();
                throw error("unexpected character '" + c + "'");
            }
        };
    }

    private Map<String, Object> readObject() {
        Map<String, Object> map = new LinkedHashMap<>();
        expect('{');
        skipWhitespace();
        if (peek() == '}') {
            pos++;
            return map;
        }
        while (true) {
            skipWhitespace();
            if (peek() != '"')
                throw error("expected a field name");
            String key = readString();
            skipWhitespace();
            expect(':');
            skipWhitespace();
            if (map.containsKey(key))
                throw error("duplicate field \"" + key + "\"");
            map.put(key, readValue());
            skipWhitespace();
            char c = next();
            if (c == '}')
                return map;
            if (c != ',')
                throw error("expected ',' or '}'");
        }
    }

    private List<Object> readArray() {
        List<Object> list = new ArrayList<>();
        expect('[');
        skipWhitespace();
        if (peek() == ']') {
            pos++;
            return list;
        }
        while (true) {
            skipWhitespace();
            list.add(readValue());
            skipWhitespace();
            char c = next();
            if (c == ']')
                return list;
            if (c != ',')
                throw error("expected ',' or ']'");
        }
    }

    private String readString() {
        expect('"');
        StringBuilder out = new StringBuilder();
        while (true) {
            char c = next();
            if (c == '"')
                return out.toString();
            if (c < 0x20)
                throw error("control character in string");
            if (c != '\\') {
                out.append(c);
                continue;
            }
            char escape = next();
            switch (escape) {
                case '"' -> out.append('"');
                case '\\' -> out.append('\\');
                case '/' -> out.append('/');
                case 'b' -> out.append('\b');
                case 'f' -> out.append('\f');
                case 'n' -> out.append('\n');
                case 'r' -> out.append('\r');
                case 't' -> out.append('\t');
                // A surrogate pair arrives as two \\u escapes; appending both chars rebuilds it.
                case 'u' -> out.append(readHex4());
                default -> throw error("invalid escape \\" + escape);
            }
        }
    }

    private char readHex4() {
        if (pos + 4 > text.length())
            throw error("incomplete \\u escape");
        String hex = text.substring(pos, pos + 4);
        try {
            pos += 4;
            return (char) Integer.parseInt(hex, 16);
        } catch (NumberFormatException e) {
            throw error("invalid \\u escape " + hex);
        }
    }

    private Object readNumber() {
        int start = pos;
        if (peek() == '-')
            pos++;
        boolean decimal = false;
        while (pos < text.length()) {
            char c = text.charAt(pos);
            if (c >= '0' && c <= '9') {
                pos++;
            } else if (c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') {
                decimal = true;
                pos++;
            } else {
                break;
            }
        }
        String number = text.substring(start, pos);
        try {
            return decimal ? (Object) Double.parseDouble(number) : (Object) Long.parseLong(number);
        } catch (NumberFormatException e) {
            throw new JsonException("invalid number '" + number + "' at position " + start);
        }
    }

    private Object readLiteral(String word, Object value) {
        if (!text.startsWith(word, pos))
            throw error("unexpected text");
        pos += word.length();
        return value;
    }

    private void skipWhitespace() {
        while (pos < text.length() && " \t\r\n".indexOf(text.charAt(pos)) >= 0)
            pos++;
    }

    private char peek() {
        if (pos >= text.length())
            throw error("unexpected end of input");
        return text.charAt(pos);
    }

    private char next() {
        char c = peek();
        pos++;
        return c;
    }

    private void expect(char c) {
        if (next() != c)
            throw new JsonException("expected '" + c + "' at position " + (pos - 1));
    }

    private JsonException error(String message) {
        return new JsonException(message + " at position " + pos);
    }
}
