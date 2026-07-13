package io.github.liquidcatmofu.abs.server.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/** Routes API requests identically for the temporary HTTP server and Minecraft RPC transport. */
public final class WebApiRouter implements HttpHandler {
    private final Map<String, HttpHandler> routes = new LinkedHashMap<>();

    public WebApiRouter(MinecraftServer server) {
        routes.put("/api/blocks", new BlockConfigApiHandler(server));
        routes.put("/api/library", new LibraryApiHandler(server));
        routes.put("/api/tts", new TtsApiHandler());
        routes.put("/api/me", new MeApiHandler(server));
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        for (Map.Entry<String, HttpHandler> route : routes.entrySet()) {
            String prefix = route.getKey();
            if (path.equals(prefix) || path.startsWith(prefix + "/")) {
                route.getValue().handle(exchange);
                return;
            }
        }
        WebAuthHelper.sendError(exchange, 404, "Not Found");
    }
}
