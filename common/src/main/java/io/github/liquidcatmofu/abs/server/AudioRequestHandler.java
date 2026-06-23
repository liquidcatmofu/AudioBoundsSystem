package io.github.liquidcatmofu.abs.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import io.github.liquidcatmofu.abs.AudioBoundsSystem;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

/** GET /audio/{uuid} → audio/ogg bytes */
public class AudioRequestHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendStatus(exchange, 405);
                return;
            }

            String rawPath = exchange.getRequestURI().getPath(); // "/audio/{uuid}"
            String tokenStr = rawPath.replaceFirst("^/audio/", "");

            UUID token;
            try {
                token = UUID.fromString(tokenStr);
            } catch (IllegalArgumentException e) {
                sendStatus(exchange, 400);
                return;
            }

            Optional<Path> file = TokenStore.consume(token);
            if (file.isEmpty()) {
                sendStatus(exchange, 403);
                return;
            }

            byte[] bytes;
            try {
                bytes = Files.readAllBytes(file.get());
            } catch (IOException e) {
                AudioBoundsSystem.LOGGER.error("Failed to read audio file: {}", file.get(), e);
                sendStatus(exchange, 500);
                return;
            }

            exchange.getResponseHeaders().set("Content-Type", "audio/ogg");
            exchange.getResponseHeaders().set("Cache-Control", "no-store");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        } finally {
            exchange.close();
        }
    }

    private static void sendStatus(HttpExchange exchange, int code) throws IOException {
        exchange.sendResponseHeaders(code, -1);
        exchange.close();
    }
}
