package io.github.liquidcatmofu.abs.client.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @Test
    void unauthenticatedApiRequestIsRejectedOverLoopback() throws Exception {
        server.ensureStarted();
        HttpRequest request = HttpRequest.newBuilder(
                URI.create("http://127.0.0.1:" + server.port() + "/api/me")).GET().build();

        HttpResponse<String> response = HttpClient.newHttpClient().send(
                request, HttpResponse.BodyHandlers.ofString());

        assertEquals(401, response.statusCode());
        assertEquals("{\"error\":\"Unauthorized\"}", response.body());
        assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
    }
}
