package server;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

import org.junit.jupiter.api.Test;

class NIOServerTest {

    @Test
    void stopServerDoesNotThrowWhenNotStarted() {
        NIOServer server = new NIOServer() {
            @Override
            protected void handleClient(SelectionKey key) throws IOException {
            }
            @Override
            protected void registeredClient(SocketChannel sc) throws IOException {
            }
        };
        assertDoesNotThrow(() -> server.stopServer());
    }
}
