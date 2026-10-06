package ludo.client.net;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;

/** A tiny scripted HTTP server for the network tests (JDK only), on a free port. */
final class StubServer implements AutoCloseable {

    private final HttpServer server;

    StubServer(String path, HttpHandler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext(path, handler);
        server.start();
    }

    String url() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
