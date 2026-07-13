package io.github.liquidcatmofu.abs.server.web;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class MemoryHttpExchangeTest {
    @Test
    void carriesRequestMetadataAndCapturesAJsonResponse() throws Exception {
        byte[] request = "{\"name\":\"test\"}".getBytes(StandardCharsets.UTF_8);
        MemoryHttpExchange exchange = new MemoryHttpExchange(
                "POST", URI.create("/api/library/example?detail=1"), request);
        exchange.getRequestHeaders().set(WebAuthHelper.CSRF_HEADER, "1");

        assertEquals("POST", exchange.getRequestMethod());
        assertEquals("/api/library/example", exchange.getRequestURI().getPath());
        assertArrayEquals(request, exchange.getRequestBody().readAllBytes());
        assertEquals("1", exchange.getRequestHeaders().getFirst(WebAuthHelper.CSRF_HEADER));

        WebAuthHelper.sendJson(exchange, 201, "{\"ok\":true}");

        assertEquals(201, exchange.getResponseCode());
        assertEquals("application/json; charset=utf-8",
                exchange.getResponseHeaders().getFirst("Content-Type"));
        assertEquals("{\"ok\":true}", new String(exchange.responseBytes(), StandardCharsets.UTF_8));
    }
}
