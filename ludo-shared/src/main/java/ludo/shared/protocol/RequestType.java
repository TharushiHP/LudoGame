package ludo.shared.protocol;

/** Kinds of request a client sends to the server (each is one POST endpoint). */
public enum RequestType {
    JOIN,
    ROLL,
    DECISION,
    ACK
}
