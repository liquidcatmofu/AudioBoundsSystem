package io.github.liquidcatmofu.abs.tts.provider;

import com.sun.net.httpserver.HttpServer;
import io.github.liquidcatmofu.abs.ttsbridge.TTSSynthesisException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.net.InetSocketAddress;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VoiceVoxCompatibleProviderTest {
    @Test
    void acceptsAResponseAtTheLimit() throws Exception {
        byte[] response = "12345".getBytes(StandardCharsets.UTF_8);

        assertArrayEquals(response, VoiceVoxCompatibleProvider.readBytes(
                new ByteArrayInputStream(response), response.length, response.length, "test response"));
        assertEquals("12345", VoiceVoxCompatibleProvider.readString(
                new ByteArrayInputStream(response), -1, response.length, "test response"));
    }

    @Test
    void rejectsOversizedDeclaredAndStreamingResponses() {
        assertThrows(IOException.class, () -> VoiceVoxCompatibleProvider.readBytes(
                new ByteArrayInputStream(new byte[0]), 6, 5, "declared response"));
        assertThrows(IOException.class, () -> VoiceVoxCompatibleProvider.readBytes(
                new ByteArrayInputStream(new byte[6]), -1, 5, "streaming response"));
    }

    @Test
    void classifiesProviderHttpErrors() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/audio_query", exchange -> {
            byte[] body = "busy".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(429, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            var provider = provider(server);
            TTSSynthesisException error = assertThrows(TTSSynthesisException.class,
                    () -> provider.synthesizeToOgg("test", "1", Map.of()));
            assertEquals(TTSSynthesisException.Kind.HTTP_ERROR, error.kind());
            assertEquals(429, error.providerStatus());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void classifiesInvalidAudioQueryJson() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/audio_query", exchange -> {
            byte[] body = "not-json".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            TTSSynthesisException error = assertThrows(TTSSynthesisException.class,
                    () -> provider(server).synthesizeToOgg("test", "1", Map.of()));
            assertEquals(TTSSynthesisException.Kind.INVALID_RESPONSE, error.kind());
        } finally {
            server.stop(0);
        }
    }

    private static VoiceVoxCompatibleProvider provider(HttpServer server) {
        String url = "http://127.0.0.1:" + server.getAddress().getPort();
        return new VoiceVoxCompatibleProvider("test-provider", "Test", url);
    }
}
