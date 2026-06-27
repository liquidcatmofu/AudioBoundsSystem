package io.github.liquidcatmofu.abs.server.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import io.github.liquidcatmofu.abs.library.ABSLibrary;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

/** GET /api/me → ログイン中プレイヤーの UUID・名前・OP 判定。ルートフォルダを自動生成。 */
public class MeApiHandler implements HttpHandler {
    private final MinecraftServer server;

    public MeApiHandler(MinecraftServer server) {
        this.server = server;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        Optional<UUID> session = WebAuthHelper.extractSessionToken(exchange);
        UUID playerUuid = session.flatMap(WebSessionStore::getPlayerUuid).orElse(null);
        if (playerUuid == null) {
            WebAuthHelper.sendError(exchange, 401, "Unauthorized");
            return;
        }

        ServerPlayer player = server.getPlayerList().getPlayer(playerUuid);
        String name = player != null ? player.getGameProfile().getName() : playerUuid.toString();
        boolean isOp = player != null && server.getPlayerList().isOp(player.getGameProfile());

        // ルートフォルダを保証
        ABSLibrary.ensureRoot(playerUuid, name);

        String json = "{\"uuid\":\"" + playerUuid + "\",\"name\":\"" + escape(name) + "\",\"isOp\":" + isOp + "}";
        WebAuthHelper.sendJson(exchange, 200, json);
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
