package io.github.liquidcatmofu.abs.server.web;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

public final class WebAuthHelper {
    private static final String SESSION_COOKIE = "abs_session";
    public static final String CSRF_HEADER = "X-ABS-CSRF";

    private WebAuthHelper() {}

    /** Cookie ヘッダーからセッショントークンを取り出す */
    public static Optional<UUID> extractSessionToken(HttpExchange exchange) {
        String cookieHeader = exchange.getRequestHeaders().getFirst("Cookie");
        if (cookieHeader == null) return Optional.empty();
        for (String part : cookieHeader.split(";")) {
            String[] kv = part.strip().split("=", 2);
            if (kv.length == 2 && SESSION_COOKIE.equals(kv[0].strip())) {
                try {
                    return Optional.of(UUID.fromString(kv[1].strip()));
                } catch (IllegalArgumentException ignored) {}
            }
        }
        return Optional.empty();
    }

    /** セッション Cookie を設定する（8h）*/
    public static void setSessionCookie(HttpExchange exchange, UUID sessionToken) {
        String cookie = SESSION_COOKIE + "=" + sessionToken
                + "; HttpOnly; Path=/; Max-Age=28800; SameSite=Strict";
        exchange.getResponseHeaders().set("Set-Cookie", cookie);
    }

    /** 更新系APIでは、ブラウザがクロスサイトフォームから付与できない専用ヘッダーを要求する。 */
    public static boolean validateMutationHeader(HttpExchange exchange) throws IOException {
        if (isMutationHeaderValid(exchange.getRequestMethod(),
                exchange.getRequestHeaders().getFirst(CSRF_HEADER))) {
            return true;
        }
        sendError(exchange, 403, "Missing or invalid CSRF header");
        return false;
    }

    static boolean isMutationHeaderValid(String method, String headerValue) {
        if ("GET".equals(method) || "HEAD".equals(method) || "OPTIONS".equals(method)) {
            return true;
        }
        return "1".equals(headerValue);
    }

    /** JSON レスポンスを送信する */
    public static void sendJson(HttpExchange exchange, int status, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        }
    }

    /** エラー JSON を送信する */
    public static void sendError(HttpExchange exchange, int status, String message) throws IOException {
        sendJson(exchange, status, "{\"error\":\"" + escape(message) + "\"}");
    }

    /** リダイレクト */
    public static void sendRedirect(HttpExchange exchange, String location) throws IOException {
        exchange.getResponseHeaders().set("Location", location);
        exchange.sendResponseHeaders(302, -1);
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
