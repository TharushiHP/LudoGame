package ludo.shared.json;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Round trips, escaping and error handling of the hand-written JSON writer and parser. */
class JsonTest {

    @Test
    void writesEveryValueTypeCompactly() {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("s", "text");
        json.put("i", 7);
        json.put("l", 12345678901L);
        json.put("d", 1.5);
        json.put("t", true);
        json.put("f", false);
        json.put("n", null);
        json.put("a", List.of(1, "two"));
        json.put("o", Map.of("k", 1));
        assertEquals("{\"s\":\"text\",\"i\":7,\"l\":12345678901,\"d\":1.5,\"t\":true,\"f\":false,"
                + "\"n\":null,\"a\":[1,\"two\"],\"o\":{\"k\":1}}", JsonWriter.write(json));
    }

    @Test
    void parsedValuesWriteBackToTheSameText() {
        String text = "{\"s\":\"text\",\"l\":-42,\"d\":0.25,\"t\":true,\"n\":null,"
                + "\"a\":[1,[2,{}],[]],\"o\":{\"x\":{\"y\":\"z\"}}}";
        assertEquals(text, JsonWriter.write(JsonParser.parse(text)));
    }

    @Test
    void parserReturnsLongForWholeNumbersAndDoubleOtherwise() {
        Map<String, Object> json = JsonParser.parseObject("{\"a\":3,\"b\":3.0,\"c\":-1e2}");
        assertEquals(3L, json.get("a"));
        assertEquals(3.0, json.get("b"));
        assertEquals(-100.0, json.get("c"));
    }

    @Test
    void parserKeepsFieldOrderAndAllowsWhitespace() {
        Map<String, Object> json = JsonParser.parseObject(" {\n \"z\" : 1 ,\t\"a\" : [ 1 , 2 ] }\r\n");
        assertEquals(List.of("z", "a"), new ArrayList<>(json.keySet()));
        assertEquals(List.of(1L, 2L), json.get("a"));
    }

    @Test
    void escapesSpecialCharactersAndReadsThemBack() {
        String tricky = "quote \" backslash \\ slash / newline \n return \r tab \t bell \u0007 end";
        String written = JsonWriter.write(tricky);
        assertEquals("\"quote \\\" backslash \\\\ slash / newline \\n return \\r tab \\t bell \\u0007 end\"", written);
        assertEquals(tricky, JsonParser.parse(written));
    }

    @Test
    void readsUnicodeEscapesIncludingSurrogatePairs() {
        assertEquals("é😀", JsonParser.parse("\"\\u00e9\\ud83d\\ude00\""));
        assertEquals("é😀", JsonParser.parse(JsonWriter.write("é😀")));
    }

    @Test
    void writesNullListElementsAndNestedEmptyValues() {
        assertEquals("[null,[],{}]", JsonWriter.write(Arrays.asList(null, List.of(), Map.of())));
    }

    @Test
    void rejectsMalformedInput() {
        for (String bad : List.of("", "{", "{\"a\"}", "{\"a\":1,}", "[1 2]", "\"open", "tru", "{\"a\":1} x",
                "{a:1}", "\"bad \\q escape\"", "\"raw \n newline\"", "{\"a\":1,\"a\":2}", "01x", "-")) {
            assertThrows(JsonException.class, () -> JsonParser.parse(bad), bad);
        }
    }

    @Test
    void parseObjectRejectsNonObjects() {
        assertThrows(JsonException.class, () -> JsonParser.parseObject("[1]"));
        assertThrows(JsonException.class, () -> JsonParser.parseObject(null));
    }

    @Test
    void writerRejectsValuesJsonCannotHold() {
        assertThrows(JsonException.class, () -> JsonWriter.write(Double.NaN));
        assertThrows(JsonException.class, () -> JsonWriter.write(new Object()));
    }

    @Test
    void typedGettersNameTheBadField() {
        Map<String, Object> json = JsonParser.parseObject("{\"n\":\"seven\",\"b\":1}");
        JsonException missing = assertThrows(JsonException.class, () -> JsonObjects.getString(json, "x"));
        assertTrue(missing.getMessage().contains("\"x\""));
        JsonException wrong = assertThrows(JsonException.class, () -> JsonObjects.getLong(json, "n"));
        assertTrue(wrong.getMessage().contains("\"n\""));
        assertThrows(JsonException.class, () -> JsonObjects.getBoolean(json, "b"));
        assertTrue(JsonObjects.getOptionalInt(json, "absent").isEmpty());
    }
}
