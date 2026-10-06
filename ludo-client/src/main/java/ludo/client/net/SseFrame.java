package ludo.client.net;

/**
 * One complete Server-Sent Events message (Value Object).
 *
 * @param id    the last event id seen so far (null if the server never sent one)
 * @param event the "event:" field, or "message" when the frame had none
 * @param data  all "data:" lines of the frame, joined with '\n'
 */
public record SseFrame(String id, String event, String data) {
}
