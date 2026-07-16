package io.github.liquidcatmofu.abs.client.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientWebServerLifecycleTest {
    private final ClientWebServer server = ClientWebServer.INSTANCE;

    @AfterEach
    void stopServer() {
        server.stop();
    }

    @Test
    void startAndStopAreIdempotentAndRestartable() throws Exception {
        server.ensureStarted();
        server.ensureStarted();
        assertTrue(server.isRunning());

        server.stop();
        server.stop();
        assertFalse(server.isRunning());

        server.ensureStarted();
        assertTrue(server.isRunning());
    }
}
