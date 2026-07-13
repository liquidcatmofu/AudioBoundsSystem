package io.github.liquidcatmofu.abs.server.web;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** HTTPリクエスト本文を、宣言値と実読込の両方で上限検証して読み込む。 */
public final class RequestBodyReader {
    public static final int MAX_JSON_BYTES = 64 * 1024;

    private RequestBodyReader() {}

    public static byte[] readBytes(HttpExchange exchange, int maxBytes) throws IOException {
        String contentLength = exchange.getRequestHeaders().getFirst("Content-Length");
        try (InputStream body = exchange.getRequestBody()) {
            return readBytes(body, contentLength, maxBytes);
        }
    }

    public static JsonObject readJson(HttpExchange exchange) throws IOException {
        byte[] bytes = readBytes(exchange, MAX_JSON_BYTES);
        return JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    static byte[] readBytes(InputStream body, String contentLength, int maxBytes) throws IOException {
        if (maxBytes < 0) {
            throw new IllegalArgumentException("maxBytes must not be negative");
        }
        if (contentLength != null) {
            try {
                long declared = Long.parseLong(contentLength);
                if (declared > maxBytes) {
                    throw new PayloadTooLargeException(maxBytes);
                }
            } catch (NumberFormatException ignored) {
                // 不正なContent-LengthはHttpServerまたは実読込上限に任せる。
            }
        }

        byte[] bytes = body.readNBytes(maxBytes + 1);
        if (bytes.length > maxBytes) {
            throw new PayloadTooLargeException(maxBytes);
        }
        return bytes;
    }

    public static final class PayloadTooLargeException extends IOException {
        private final int maxBytes;

        private PayloadTooLargeException(int maxBytes) {
            super("Request body exceeds " + maxBytes + " bytes");
            this.maxBytes = maxBytes;
        }

        public int getMaxBytes() {
            return maxBytes;
        }
    }
}
