package io.github.liquidcatmofu.abs.server.web;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import io.github.liquidcatmofu.abs.AudioBoundsSystem;
import io.github.liquidcatmofu.abs.library.ABSLibrary;
import io.github.liquidcatmofu.abs.library.FolderAccess;
import io.github.liquidcatmofu.abs.library.LibraryFolder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * /api/library/* — ネスト対応フォルダ CRUD（フラットストア + parentId）
 *
 * GET    /api/library            → 閲覧可能な全フォルダ（フラット、クライアントがツリー構成）
 * POST   /api/library            → サブフォルダ作成 {parentId, displayName}
 * GET    /api/library/{id}       → フォルダ詳細
 * PATCH  /api/library/{id}       → displayName 更新（OWNER）
 * DELETE /api/library/{id}       → フォルダ削除（OWNER・ルート不可）
 * PATCH  /api/library/{id}/players → allowedPlayers 更新（OWNER）
 */
public class LibraryApiHandler implements HttpHandler {
    private static final Gson GSON = new GsonBuilder().create();
    private final MinecraftServer server;

    public LibraryApiHandler(MinecraftServer server) {
        this.server = server;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        UUID playerUuid = WebAuthHelper.extractSessionToken(exchange)
                .flatMap(WebSessionStore::getPlayerUuid).orElse(null);
        if (playerUuid == null) {
            WebAuthHelper.sendError(exchange, 401, "Unauthorized");
            return;
        }
        boolean isOp = isOp(playerUuid);
        String method = exchange.getRequestMethod();
        String sub = exchange.getRequestURI().getPath().substring("/api/library".length());

        try {
            if (sub.isEmpty() || sub.equals("/")) {
                handleRoot(exchange, method, playerUuid, isOp);
                return;
            }
            String[] parts = sub.substring(1).split("/", 2);
            String id = parts[0];
            String extra = parts.length > 1 ? parts[1] : "";
            if (!ABSLibrary.isSafeId(id)) {
                WebAuthHelper.sendError(exchange, 400, "Invalid folder id");
                return;
            }
            if (extra.equals("players")) {
                handlePlayers(exchange, method, id, playerUuid, isOp);
            } else if (extra.isEmpty()) {
                handleFolder(exchange, method, id, playerUuid, isOp);
            } else {
                WebAuthHelper.sendError(exchange, 404, "Not Found");
            }
        } catch (Exception e) {
            AudioBoundsSystem.LOGGER.error("ABS: LibraryApiHandler error", e);
            WebAuthHelper.sendError(exchange, 500, "Internal Server Error");
        }
    }

    // GET (list) / POST (create sub-folder)
    private void handleRoot(HttpExchange exchange, String method, UUID playerUuid, boolean isOp) throws IOException {
        if ("GET".equals(method)) {
            // ルート保証（初回アクセスでも自分のルートが見える）
            ABSLibrary.ensureRoot(playerUuid, playerName(playerUuid));
            List<LibraryFolder> folders = ABSLibrary.listAccessible(playerUuid, isOp);
            WebAuthHelper.sendJson(exchange, 200, GSON.toJson(folders));

        } else if ("POST".equals(method)) {
            JsonObject body = readJson(exchange);
            if (body == null || !body.has("parentId") || !body.has("displayName")) {
                WebAuthHelper.sendError(exchange, 400, "Missing parentId or displayName");
                return;
            }
            String parentId = body.get("parentId").getAsString();
            String displayName = body.get("displayName").getAsString().trim();
            if (displayName.isEmpty()) {
                WebAuthHelper.sendError(exchange, 400, "displayName must not be empty");
                return;
            }
            LibraryFolder parent = ABSLibrary.loadFolder(parentId).orElse(null);
            if (parent == null) {
                WebAuthHelper.sendError(exchange, 404, "Parent folder not found");
                return;
            }
            // 構造変更は OWNER のみ
            if (!ABSLibrary.access(parent, playerUuid, isOp).canManage()) {
                WebAuthHelper.sendError(exchange, 403, "Only the owner can create folders here");
                return;
            }
            LibraryFolder created = ABSLibrary.createSubFolder(parent, displayName);
            WebAuthHelper.sendJson(exchange, 201, GSON.toJson(created));

        } else {
            WebAuthHelper.sendError(exchange, 405, "Method Not Allowed");
        }
    }

    // GET / PATCH / DELETE /api/library/{id}
    private void handleFolder(HttpExchange exchange, String method, String id, UUID playerUuid, boolean isOp) throws IOException {
        LibraryFolder folder = ABSLibrary.loadFolder(id).orElse(null);
        if (folder == null) {
            WebAuthHelper.sendError(exchange, 404, "Folder not found");
            return;
        }
        FolderAccess access = ABSLibrary.access(folder, playerUuid, isOp);
        if (!access.canView()) {
            WebAuthHelper.sendError(exchange, 403, "Forbidden");
            return;
        }

        if ("GET".equals(method)) {
            WebAuthHelper.sendJson(exchange, 200, GSON.toJson(folder));

        } else if ("PATCH".equals(method)) {
            if (!access.canManage()) {
                WebAuthHelper.sendError(exchange, 403, "Only the owner can rename folders");
                return;
            }
            JsonObject body = readJson(exchange);
            if (body != null && body.has("displayName")) {
                String name = body.get("displayName").getAsString().trim();
                if (!name.isEmpty()) {
                    folder.displayName = name;
                    ABSLibrary.saveFolder(folder);
                }
            }
            WebAuthHelper.sendJson(exchange, 200, GSON.toJson(folder));

        } else if ("DELETE".equals(method)) {
            if (!access.canManage()) {
                WebAuthHelper.sendError(exchange, 403, "Only the owner can delete folders");
                return;
            }
            if (folder.isRoot()) {
                WebAuthHelper.sendError(exchange, 400, "Cannot delete the root folder");
                return;
            }
            ABSLibrary.deleteFolderRecursive(id);
            WebAuthHelper.sendJson(exchange, 200, "{\"deleted\":true}");

        } else {
            WebAuthHelper.sendError(exchange, 405, "Method Not Allowed");
        }
    }

    // PATCH /api/library/{id}/players
    private void handlePlayers(HttpExchange exchange, String method, String id, UUID playerUuid, boolean isOp) throws IOException {
        if (!"PATCH".equals(method)) {
            WebAuthHelper.sendError(exchange, 405, "Method Not Allowed");
            return;
        }
        LibraryFolder folder = ABSLibrary.loadFolder(id).orElse(null);
        if (folder == null) {
            WebAuthHelper.sendError(exchange, 404, "Folder not found");
            return;
        }
        if (!ABSLibrary.access(folder, playerUuid, isOp).canManage()) {
            WebAuthHelper.sendError(exchange, 403, "Only the owner can manage allowed players");
            return;
        }
        JsonObject body = readJson(exchange);
        if (body == null || !body.has("allowedPlayers")) {
            WebAuthHelper.sendError(exchange, 400, "Missing allowedPlayers");
            return;
        }
        folder.allowedPlayers.clear();
        body.getAsJsonArray("allowedPlayers").forEach(el -> folder.allowedPlayers.add(el.getAsString()));
        ABSLibrary.saveFolder(folder);
        WebAuthHelper.sendJson(exchange, 200, GSON.toJson(folder));
    }

    private JsonObject readJson(HttpExchange exchange) {
        try (InputStream is = exchange.getRequestBody()) {
            return JsonParser.parseString(new String(is.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isOp(UUID playerUuid) {
        ServerPlayer player = server.getPlayerList().getPlayer(playerUuid);
        return player != null && server.getPlayerList().isOp(player.getGameProfile());
    }

    private String playerName(UUID playerUuid) {
        ServerPlayer player = server.getPlayerList().getPlayer(playerUuid);
        return player != null ? player.getGameProfile().getName() : playerUuid.toString();
    }
}
