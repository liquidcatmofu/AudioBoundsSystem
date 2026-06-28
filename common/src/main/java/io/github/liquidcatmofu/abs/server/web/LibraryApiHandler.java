package io.github.liquidcatmofu.abs.server.web;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import io.github.liquidcatmofu.abs.AudioBoundsSystem;
import io.github.liquidcatmofu.abs.library.ABSLibrary;
import io.github.liquidcatmofu.abs.library.AudioEntry;
import io.github.liquidcatmofu.abs.library.FolderAccess;
import io.github.liquidcatmofu.abs.library.LibraryAudio;
import io.github.liquidcatmofu.abs.library.LibraryFolder;
import io.github.liquidcatmofu.abs.library.LibraryTts;
import io.github.liquidcatmofu.abs.library.TtsEntry;
import io.github.liquidcatmofu.abs.ttsbridge.TTSBridge;
import io.github.liquidcatmofu.abs.ttsbridge.TTSBridgeRegistry;
import io.github.liquidcatmofu.abs.ttsbridge.TTSSynthesisRequest;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
            } else if (extra.equals("audio") || extra.startsWith("audio/")) {
                handleAudio(exchange, method, id, extra, playerUuid, isOp);
            } else if (extra.equals("tts") || extra.startsWith("tts/")) {
                handleTts(exchange, method, id, extra, playerUuid, isOp);
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

    private static final int MAX_UPLOAD_BYTES = 64 * 1024 * 1024;

    // /api/library/{folderId}/audio[/{audioId}[/preview]]
    private void handleAudio(HttpExchange exchange, String method, String folderId, String extra,
                             UUID playerUuid, boolean isOp) throws IOException, InterruptedException {
        LibraryFolder folder = ABSLibrary.loadFolder(folderId).orElse(null);
        if (folder == null) {
            WebAuthHelper.sendError(exchange, 404, "Folder not found");
            return;
        }
        if (!ABSLibrary.access(folder, playerUuid, isOp).canView()) {
            WebAuthHelper.sendError(exchange, 403, "Forbidden");
            return;
        }

        String[] seg = extra.split("/");
        if (seg.length == 1) {
            // /audio — list / upload
            if ("GET".equals(method)) {
                WebAuthHelper.sendJson(exchange, 200, GSON.toJson(LibraryAudio.list(folderId)));
            } else if ("POST".equals(method)) {
                String rawName = exchange.getRequestHeaders().getFirst("X-Filename");
                if (rawName == null || rawName.isBlank()) {
                    WebAuthHelper.sendError(exchange, 400, "Missing X-Filename header");
                    return;
                }
                String filename = URLDecoder.decode(rawName, StandardCharsets.UTF_8);
                byte[] data = exchange.getRequestBody().readAllBytes();
                if (data.length == 0) {
                    WebAuthHelper.sendError(exchange, 400, "Empty upload");
                    return;
                }
                if (data.length > MAX_UPLOAD_BYTES) {
                    WebAuthHelper.sendError(exchange, 413, "File too large (max 64MB)");
                    return;
                }
                try {
                    AudioEntry entry = LibraryAudio.importAudio(folderId, data, filename, playerUuid);
                    WebAuthHelper.sendJson(exchange, 201, GSON.toJson(entry));
                } catch (IOException e) {
                    AudioBoundsSystem.LOGGER.error("ABS: audio import failed", e);
                    WebAuthHelper.sendError(exchange, 500, "変換に失敗しました（ffmpeg を確認してください）");
                }
            } else {
                WebAuthHelper.sendError(exchange, 405, "Method Not Allowed");
            }
            return;
        }

        String audioId = seg[1];
        if (!ABSLibrary.isSafeId(audioId)) {
            WebAuthHelper.sendError(exchange, 400, "Invalid audio id");
            return;
        }

        if (seg.length == 2) {
            // /audio/{audioId} — get / delete
            if ("GET".equals(method)) {
                AudioEntry entry = LibraryAudio.load(folderId, audioId).orElse(null);
                if (entry == null) WebAuthHelper.sendError(exchange, 404, "Audio not found");
                else WebAuthHelper.sendJson(exchange, 200, GSON.toJson(entry));
            } else if ("DELETE".equals(method)) {
                boolean ok = LibraryAudio.delete(folderId, audioId);
                if (ok) WebAuthHelper.sendJson(exchange, 200, "{\"deleted\":true}");
                else WebAuthHelper.sendError(exchange, 404, "Audio not found");
            } else {
                WebAuthHelper.sendError(exchange, 405, "Method Not Allowed");
            }
        } else if (seg.length == 3 && "preview".equals(seg[2]) && "GET".equals(method)) {
            servePreview(exchange, folderId, audioId);
        } else {
            WebAuthHelper.sendError(exchange, 404, "Not Found");
        }
    }

    private void servePreview(HttpExchange exchange, String folderId, String audioId) throws IOException {
        AudioEntry entry = LibraryAudio.load(folderId, audioId).orElse(null);
        if (entry == null) {
            WebAuthHelper.sendError(exchange, 404, "Audio not found");
            return;
        }
        serveOgg(exchange, LibraryAudio.cacheFilePath(entry));
    }

    // /api/library/{folderId}/tts[/{id}[/preview]]
    private void handleTts(HttpExchange exchange, String method, String folderId, String extra,
                           UUID playerUuid, boolean isOp) throws IOException {
        LibraryFolder folder = ABSLibrary.loadFolder(folderId).orElse(null);
        if (folder == null) {
            WebAuthHelper.sendError(exchange, 404, "Folder not found");
            return;
        }
        if (!ABSLibrary.access(folder, playerUuid, isOp).canView()) {
            WebAuthHelper.sendError(exchange, 403, "Forbidden");
            return;
        }

        String[] seg = extra.split("/");
        if (seg.length == 1) {
            if ("GET".equals(method)) {
                WebAuthHelper.sendJson(exchange, 200, GSON.toJson(LibraryTts.list(folderId)));
            } else if ("POST".equals(method)) {
                synthesizeTts(exchange, folderId, playerUuid);
            } else {
                WebAuthHelper.sendError(exchange, 405, "Method Not Allowed");
            }
            return;
        }

        String ttsId = seg[1];
        if (!ABSLibrary.isSafeId(ttsId)) {
            WebAuthHelper.sendError(exchange, 400, "Invalid tts id");
            return;
        }
        if (seg.length == 2) {
            if ("GET".equals(method)) {
                TtsEntry entry = LibraryTts.load(folderId, ttsId).orElse(null);
                if (entry == null) WebAuthHelper.sendError(exchange, 404, "Not found");
                else WebAuthHelper.sendJson(exchange, 200, GSON.toJson(entry));
            } else if ("PATCH".equals(method)) {
                if (!ABSLibrary.access(folder, playerUuid, isOp).canManage()) {
                    WebAuthHelper.sendError(exchange, 403, "Only the owner can edit TTS entries");
                    return;
                }
                resynthesizeTts(exchange, folderId, ttsId, playerUuid);
            } else if ("DELETE".equals(method)) {
                if (!ABSLibrary.access(folder, playerUuid, isOp).canManage()) {
                    WebAuthHelper.sendError(exchange, 403, "Only the owner can delete TTS entries");
                    return;
                }
                if (LibraryTts.delete(folderId, ttsId)) WebAuthHelper.sendJson(exchange, 200, "{\"deleted\":true}");
                else WebAuthHelper.sendError(exchange, 404, "Not found");
            } else {
                WebAuthHelper.sendError(exchange, 405, "Method Not Allowed");
            }
        } else if (seg.length == 3 && "preview".equals(seg[2]) && "GET".equals(method)) {
            TtsEntry entry = LibraryTts.load(folderId, ttsId).orElse(null);
            if (entry == null) { WebAuthHelper.sendError(exchange, 404, "Not found"); return; }
            serveOgg(exchange, LibraryTts.cacheFilePath(entry));
        } else {
            WebAuthHelper.sendError(exchange, 404, "Not Found");
        }
    }

    private void synthesizeTts(HttpExchange exchange, String folderId, UUID playerUuid) throws IOException {
        TTSBridge bridge = TTSBridgeRegistry.get();
        if (bridge == null) {
            WebAuthHelper.sendError(exchange, 503, "TTS アドオンが導入されていません");
            return;
        }
        JsonObject body = readJson(exchange);
        if (body == null || !body.has("engineId") || !body.has("speakerId") || !body.has("text")) {
            WebAuthHelper.sendError(exchange, 400, "Missing engineId/speakerId/text");
            return;
        }
        String text = body.get("text").getAsString().trim();
        if (text.isEmpty()) {
            WebAuthHelper.sendError(exchange, 400, "text must not be empty");
            return;
        }

        TTSSynthesisRequest req = new TTSSynthesisRequest();
        req.engineId = body.get("engineId").getAsString();
        req.speakerId = body.get("speakerId").getAsString();
        req.text = text;
        if (body.has("params") && body.get("params").isJsonObject()) {
            for (var e : body.getAsJsonObject("params").entrySet()) {
                try {
                    req.params.put(e.getKey(), e.getValue().getAsDouble());
                } catch (Exception ignored) {}
            }
        }
        String displayName = body.has("displayName") ? body.get("displayName").getAsString() : null;
        String speakerName = body.has("speakerName") ? body.get("speakerName").getAsString() : null;

        try {
            byte[] ogg = bridge.synthesize(req);
            TtsEntry entry = LibraryTts.create(folderId, displayName, req, speakerName, ogg, playerUuid);
            WebAuthHelper.sendJson(exchange, 201, GSON.toJson(entry));
        } catch (Exception e) {
            AudioBoundsSystem.LOGGER.error("ABS: TTS synthesis failed", e);
            WebAuthHelper.sendError(exchange, 502, "TTS 合成に失敗しました（VOICEVOX の起動状況を確認してください）");
        }
    }

    private void resynthesizeTts(HttpExchange exchange, String folderId, String ttsId, UUID playerUuid) throws IOException {
        TTSBridge bridge = TTSBridgeRegistry.get();
        if (bridge == null) {
            WebAuthHelper.sendError(exchange, 503, "TTS アドオンが導入されていません");
            return;
        }
        JsonObject body = readJson(exchange);
        if (body == null || !body.has("engineId") || !body.has("speakerId") || !body.has("text")) {
            WebAuthHelper.sendError(exchange, 400, "Missing engineId/speakerId/text");
            return;
        }
        String text = body.get("text").getAsString().trim();
        if (text.isEmpty()) {
            WebAuthHelper.sendError(exchange, 400, "text must not be empty");
            return;
        }

        TTSSynthesisRequest req = new TTSSynthesisRequest();
        req.engineId = body.get("engineId").getAsString();
        req.speakerId = body.get("speakerId").getAsString();
        req.text = text;
        if (body.has("params") && body.get("params").isJsonObject()) {
            for (var e : body.getAsJsonObject("params").entrySet()) {
                try { req.params.put(e.getKey(), e.getValue().getAsDouble()); } catch (Exception ignored) {}
            }
        }
        String displayName = body.has("displayName") ? body.get("displayName").getAsString() : null;
        String speakerName = body.has("speakerName") ? body.get("speakerName").getAsString() : null;

        try {
            byte[] ogg = bridge.synthesize(req);
            TtsEntry entry = LibraryTts.update(folderId, ttsId, displayName, req, speakerName, ogg);
            WebAuthHelper.sendJson(exchange, 200, GSON.toJson(entry));
        } catch (Exception e) {
            AudioBoundsSystem.LOGGER.error("ABS: TTS re-synthesis failed", e);
            WebAuthHelper.sendError(exchange, 502, "TTS 合成に失敗しました（VOICEVOX の起動状況を確認してください）");
        }
    }

    private void serveOgg(HttpExchange exchange, Path ogg) throws IOException {
        if (!Files.isRegularFile(ogg)) {
            WebAuthHelper.sendError(exchange, 404, "Audio file missing");
            return;
        }
        byte[] bytes = Files.readAllBytes(ogg);
        exchange.getResponseHeaders().set("Content-Type", "audio/ogg");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
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
