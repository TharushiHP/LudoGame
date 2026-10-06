package ludo.shared.json;

import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

/**
 * Typed getters for a parsed JSON object. A missing field or a field of the wrong type throws
 * {@link JsonException} naming the field, so one malformed request gives one clear error message.
 */
public final class JsonObjects {

    private JsonObjects() {}

    public static String getString(Map<String, Object> json, String field) {
        Object value = require(json, field);
        if (!(value instanceof String s))
            throw wrongType(field, "a string");
        return s;
    }

    public static long getLong(Map<String, Object> json, String field) {
        Object value = require(json, field);
        if (!(value instanceof Long l))
            throw wrongType(field, "a whole number");
        return l;
    }

    public static int getInt(Map<String, Object> json, String field) {
        long value = getLong(json, field);
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE)
            throw wrongType(field, "a whole number in int range");
        return (int) value;
    }

    /** Absent or null gives an empty result; any other non-integer value is an error. */
    public static OptionalInt getOptionalInt(Map<String, Object> json, String field) {
        if (json.get(field) == null)
            return OptionalInt.empty();
        return OptionalInt.of(getInt(json, field));
    }

    public static boolean getBoolean(Map<String, Object> json, String field) {
        Object value = require(json, field);
        if (!(value instanceof Boolean b))
            throw wrongType(field, "true or false");
        return b;
    }

    @SuppressWarnings("unchecked")
    public static List<Object> getList(Map<String, Object> json, String field) {
        Object value = require(json, field);
        if (!(value instanceof List))
            throw wrongType(field, "an array");
        return (List<Object>) value;
    }

    public static Map<String, Object> getObject(Map<String, Object> json, String field) {
        return asObject(require(json, field), field);
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> asObject(Object value, String what) {
        if (!(value instanceof Map))
            throw wrongType(what, "an object");
        return (Map<String, Object>) value;
    }

    public static <E extends Enum<E>> E getEnum(Map<String, Object> json, String field, Class<E> type) {
        return toEnum(getString(json, field), field, type);
    }

    /** Converts a string (e.g. an object key) to an enum constant; {@code field} names it in errors. */
    public static <E extends Enum<E>> E toEnum(String name, String field, Class<E> type) {
        try {
            return Enum.valueOf(type, name);
        } catch (IllegalArgumentException e) {
            throw new JsonException("field \"" + field + "\" has unknown value \"" + name + "\"");
        }
    }

    private static Object require(Map<String, Object> json, String field) {
        Object value = json.get(field);
        if (value == null)
            throw new JsonException("missing field \"" + field + "\"");
        return value;
    }

    private static JsonException wrongType(String field, String expected) {
        return new JsonException("field \"" + field + "\" must be " + expected);
    }
}
