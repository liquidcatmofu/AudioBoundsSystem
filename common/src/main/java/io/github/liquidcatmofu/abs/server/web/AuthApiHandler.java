package io.github.liquidcatmofu.abs.server.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.util.UUID;

/**
 * GET /api/auth?session=<init_token>
 * 初回トークンを検証し、セッション Cookie を発行して /ui にリダイレクト。
 */
public class AuthApiHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            WebAuthHelper.sendError(exchange, 405, "Method Not Allowed");
            return;
        }

        String query = exchange.getRequestURI().getQuery();
        String initTokenStr = parseParam(query, "session");
        if (initTokenStr == null) {
            WebAuthHelper.sendError(exchange, 400, "Missing session parameter");
            return;
        }

        UUID initToken;
        try {
            initToken = UUID.fromString(initTokenStr);
        } catch (IllegalArgumentException e) {
            WebAuthHelper.sendError(exchange, 400, "Invalid session token");
            return;
        }

        UUID sessionToken = WebSessionStore.promoteToSession(initToken);
        if (sessionToken == null) {
            WebAuthHelper.sendError(exchange, 403, "Session token expired or invalid");
            return;
        }

        WebAuthHelper.setSessionCookie(exchange, sessionToken);
        WebAuthHelper.sendRedirect(exchange, "/ui");
    }

    private static String parseParam(String query, String key) {
        if (query == null) return null;
        for (String part : query.split("&")) {
            String[] kv = part.split("=", 2);
            if (kv.length == 2 && key.equals(kv[0])) {
                return kv[1];
            }
        }
        return null;
    }
}
