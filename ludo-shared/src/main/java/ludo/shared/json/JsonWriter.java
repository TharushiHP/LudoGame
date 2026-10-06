package ludo.shared.json;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Writes Java values as compact JSON text: Map (written in its own iteration order, so callers use
 * LinkedHashMap or EnumMap), List, String, Integer, Long, Double, Boolean and null.
 * There is no optional whitespace, so the same value always gives the same text (canonical form):
 * that is what makes state hashes identical on every machine. Hand-written because the project
 * allows no JSON library.
 */
public final class JsonWriter {

    private JsonWriter() {}

    public static String write(Object value) {
        StringBuilder out = new StringBuilder();
        writeValue(out, value);
        return out.toString();
    }

    private static void writeValue(StringBuilder out, Object value) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof String s) {
            writeString(out, s);
        } else if (value instanceof Integer || value instanceof Long) {
            out.append(value);
        } else if (value instanceof Double d) {
            if (d.isNaN() || d.isInfinite())
                throw new JsonException("JSON has no representation for " + d);
            out.append(d);
        } else if (value instanceof Boolean b) {
            out.append(b);
        } else if (value instanceof Map<?, ?> map) {
            writeObject(out, map);
        } else if (value instanceof List<?> list) {
            writeArray(out, list);
        } else {
            throw new JsonException("cannot write a " + value.getClass().getSimpleName() + " as JSON");
        }
    }

    private static void writeObject(StringBuilder out, Map<?, ?> map) {
        out.append('{');
        Iterator<? extends Map.Entry<?, ?>> entries = map.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<?, ?> entry = entries.next();
            writeString(out, String.valueOf(entry.getKey()));
            out.append(':');
            writeValue(out, entry.getValue());
            if (entries.hasNext())
                out.append(',');
        }
        out.append('}');
    }

    private static void writeArray(StringBuilder out, List<?> list) {
        out.append('[');
        for (int i = 0; i < list.size(); i++) {
            if (i > 0)
                out.append(',');
            writeValue(out, list.get(i));
        }
        out.append(']');
    }

    private static void writeString(StringBuilder out, String s) {
        out.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20)
                        out.append(String.format("\\u%04x", (int) c));
                    else
                        out.append(c);
                }
            }
        }
        out.append('"');
    }
}
