package ludo.client.net;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SseFrameParserTest {

    private final SseFrameParser parser = new SseFrameParser();

    private List<SseFrame> feed(String... lines) {
        List<SseFrame> frames = new ArrayList<>();
        for (String line : lines) {
            SseFrame frame = parser.accept(line);
            if (frame != null)
                frames.add(frame);
        }
        return frames;
    }

    @Test
    void oneFrameAsTheServerWritesIt() {
        List<SseFrame> frames = feed("id: 7", "event: STATE", "data: {\"version\":3}", "");
        assertEquals(List.of(new SseFrame("7", "STATE", "{\"version\":3}")), frames);
    }

    @Test
    void severalDataLinesAreJoinedWithNewlines() {
        List<SseFrame> frames = feed("event: STATE", "data: {", "data: \"a\": 1", "data: }", "");
        assertEquals("{\n\"a\": 1\n}", frames.get(0).data());
    }

    @Test
    void commentsAndKeepAlivesAreIgnored() {
        List<SseFrame> frames = feed(": keep-alive", "", "id: 1", ": in the middle", "event: PAUSED", "data: x", "");
        assertEquals(1, frames.size());
        assertEquals(new SseFrame("1", "PAUSED", "x"), frames.get(0));
    }

    @Test
    void theIdIsKeptForLaterFramesAndForReconnecting() {
        List<SseFrame> frames = feed("id: 41", "event: STATE", "data: a", "", "event: RESUMED", "data: b", "");
        assertEquals("41", frames.get(1).id(), "a frame without id: keeps the last id");
        assertEquals("41", parser.lastEventId());
        feed("id: 42", "data: c", "");
        assertEquals("42", parser.lastEventId());
    }

    @Test
    void theSpaceAfterTheColonIsOptional() {
        List<SseFrame> frames = feed("id:3", "event:GAME_OVER", "data:{}", "");
        assertEquals(new SseFrame("3", "GAME_OVER", "{}"), frames.get(0));
    }

    @Test
    void onlyOneLeadingSpaceIsRemoved() {
        assertEquals("  two", feed("data:   two", "").get(0).data());
    }

    @Test
    void aBlankLineWithoutDataGivesNoFrame() {
        assertTrue(feed("", "", "retry: 2000", "").isEmpty());
        assertTrue(feed("event: STATE", "").isEmpty(), "an event name without data is dropped");
        assertEquals("message", feed("data: x", "").get(0).event(), "the event name does not leak into the next frame");
    }

    @Test
    void retryAndUnknownFieldsAreIgnored() {
        List<SseFrame> frames = feed("retry: 2000", "colour: RED", "event: STATE", "data: d", "");
        assertEquals(new SseFrame(null, "STATE", "d"), frames.get(0));
    }
}
