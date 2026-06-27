package io.github.liquidcatmofu.abs.server.web;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import io.github.liquidcatmofu.abs.AudioBoundsSystem;
import io.github.liquidcatmofu.abs.ttsbridge.TTSBridge;
import io.github.liquidcatmofu.abs.ttsbridge.TTSBridgeRegistry;
import io.github.liquidcatmofu.abs.ttsbridge.TTSEngine;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * GET /api/tts → TTS の利用可否とエンジン一覧（話者・パラメータスキーマ込み）。
 * tts-addon 未導入なら available=false を返す。
 */
public class TtsApiHandler implements HttpHandler {
    private static final Gson GSON = new GsonBuilder().create();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        UUID playerUuid = WebAuthHelper.extractSessionToken(exchange)
                .flatMap(WebSessionStore::getPlayerUuid).orElse(null);
        if (playerUuid == null) {
            WebAuthHelper.sendError(exchange, 401, "Unauthorized");
            return;
        }
        if (!"GET".equals(exchange.getRequestMethod())) {
            WebAuthHelper.sendError(exchange, 405, "Method Not Allowed");
            return;
        }

        TTSBridge bridge = TTSBridgeRegistry.get();
        if (bridge == null) {
            WebAuthHelper.sendJson(exchange, 200, "{\"installed\":false,\"available\":false,\"engines\":[]}");
            return;
        }

        boolean available;
        List<TTSEngine> engines;
        try {
            available = bridge.isAvailable();
            engines = bridge.listEngines();
        } catch (Exception e) {
            AudioBoundsSystem.LOGGER.warn("ABS: TTS bridge query failed", e);
            WebAuthHelper.sendJson(exchange, 200, "{\"installed\":true,\"available\":false,\"engines\":[]}");
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("{\"installed\":true,\"available\":").append(available)
          .append(",\"engines\":").append(GSON.toJson(engines)).append("}");
        WebAuthHelper.sendJson(exchange, 200, sb.toString());
    }
}
