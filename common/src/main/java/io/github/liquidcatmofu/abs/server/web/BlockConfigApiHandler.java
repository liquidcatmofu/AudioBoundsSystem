package io.github.liquidcatmofu.abs.server.web;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import io.github.liquidcatmofu.abs.AudioBoundsSystem;
import io.github.liquidcatmofu.abs.blockentity.AudioControllerBlockEntity;
import io.github.liquidcatmofu.abs.blockentity.SpeakerBlockEntity;
import io.github.liquidcatmofu.abs.config.SpeakerTomlConfig;
import io.github.liquidcatmofu.abs.library.LibraryAudio;
import io.github.liquidcatmofu.abs.library.LibraryRef;
import io.github.liquidcatmofu.abs.library.LibraryTts;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * /api/blocks/* — スピーカー・コントローラーの設定を一覧・更新する（OP専用）。
 *
 * GET  /api/blocks/speakers              → TOMLスキャンによるスピーカー一覧（displayName・audioDisplayName 付き）
 * POST /api/blocks/speakers/assign       → スピーカーへ音声を割り当て
 * POST /api/blocks/speakers/rename       → スピーカーの表示名を変更
 * GET  /api/blocks/controllers           → TOMLスキャンによるコントローラー一覧
 * POST /api/blocks/controllers/assign    → コントローラーのキューを更新
 */
public class BlockConfigApiHandler implements HttpHandler {
    private static final Gson GSON = new GsonBuilder().create();

    private final MinecraftServer server;

    public BlockConfigApiHandler(MinecraftServer server) {
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
        if (!isOp(playerUuid)) {
            WebAuthHelper.sendError(exchange, 403, "OP 権限が必要です");
            return;
        }
        if (!WebAuthHelper.validateMutationHeader(exchange)) {
            return;
        }

        String method = exchange.getRequestMethod();
        String path   = exchange.getRequestURI().getPath();
        String sub    = path.substring("/api/blocks".length());

        try {
            switch (sub) {
                case "/speakers", "/speakers/" -> {
                    if ("GET".equals(method)) handleListSpeakers(exchange);
                    else WebAuthHelper.sendError(exchange, 405, "Method Not Allowed");
                }
                case "/speakers/assign" -> {
                    if ("POST".equals(method)) handleAssignSpeaker(exchange);
                    else WebAuthHelper.sendError(exchange, 405, "Method Not Allowed");
                }
                case "/speakers/rename" -> {
                    if ("POST".equals(method)) handleRenameSpeaker(exchange);
                    else WebAuthHelper.sendError(exchange, 405, "Method Not Allowed");
                }
                case "/controllers", "/controllers/" -> {
                    if ("GET".equals(method)) handleListControllers(exchange);
                    else WebAuthHelper.sendError(exchange, 405, "Method Not Allowed");
                }
                case "/controllers/assign" -> {
                    if ("POST".equals(method)) handleAssignController(exchange);
                    else WebAuthHelper.sendError(exchange, 405, "Method Not Allowed");
                }
                default -> WebAuthHelper.sendError(exchange, 404, "Not Found");
            }
        } catch (RequestBodyReader.PayloadTooLargeException e) {
            WebAuthHelper.sendError(exchange, 413, "Request body too large");
        } catch (Exception e) {
            AudioBoundsSystem.LOGGER.error("ABS: BlockConfigApiHandler error", e);
            WebAuthHelper.sendError(exchange, 500, "Internal Server Error");
        }
    }

    // ── Speakers ──────────────────────────────────────────────────────────────

    private void handleListSpeakers(HttpExchange exchange) throws IOException {
        Path speakersRoot = speakersRoot();
        List<JsonObject> result = new ArrayList<>();

        if (Files.isDirectory(speakersRoot)) {
            try (Stream<Path> stream = Files.walk(speakersRoot)) {
                stream.filter(p -> p.toString().endsWith(".toml"))
                      .sorted()
                      .forEach(toml -> {
                          JsonObject entry = parseSpeakerToml(speakersRoot, toml);
                          if (entry != null) result.add(entry);
                      });
            }
        }

        WebAuthHelper.sendJson(exchange, 200, GSON.toJson(result));
    }

    private JsonObject parseSpeakerToml(Path speakersRoot, Path toml) {
        try {
            // 相対パス構造: {namespace}/{dimPath}/{x}_{y}_{z}.toml
            Path rel = speakersRoot.relativize(toml);
            if (rel.getNameCount() < 3) return null;
            String namespace = rel.getName(0).toString();
            String dimPath   = rel.getName(1).toString();
            String fileName  = rel.getName(rel.getNameCount() - 1).toString();
            if (!fileName.endsWith(".toml")) return null;
            String posStr = fileName.substring(0, fileName.length() - 5);
            String[] coords = posStr.split("_");
            if (coords.length != 3) return null;

            String dim = namespace + ":" + dimPath;
            int x = Integer.parseInt(coords[0]);
            int y = Integer.parseInt(coords[1]);
            int z = Integer.parseInt(coords[2]);

            String displayName   = "";
            String audioRef      = "";
            String trackTitle    = "";
            String subtitle      = "";
            try (CommentedFileConfig cfg = CommentedFileConfig.builder(toml).sync().preserveInsertionOrder().build()) {
                cfg.load();
                displayName = SpeakerTomlConfig.readDisplayName(cfg);
                audioRef    = SpeakerTomlConfig.readAudioFile(cfg);
                trackTitle  = SpeakerTomlConfig.readTrackTitle(cfg);
                subtitle    = SpeakerTomlConfig.readSubtitle(cfg);
            }

            // audioRef から表示名を解決
            String audioDisplayName = resolveAudioDisplayName(audioRef);

            JsonObject obj = new JsonObject();
            obj.addProperty("dim", dim);
            obj.addProperty("x", x);
            obj.addProperty("y", y);
            obj.addProperty("z", z);
            obj.addProperty("displayName", displayName);
            obj.addProperty("audioRef", audioRef);
            obj.addProperty("audioDisplayName", audioDisplayName);
            obj.addProperty("trackTitle", trackTitle);
            obj.addProperty("subtitle", subtitle);
            return obj;
        } catch (Exception e) {
            AudioBoundsSystem.LOGGER.warn("ABS: failed to parse speaker TOML {}", toml, e);
            return null;
        }
    }

    /** lib: 参照から音声エントリの displayName を取得する。解決できなければ空文字を返す。 */
    private static String resolveAudioDisplayName(String ref) {
        if (ref == null || ref.isBlank() || !LibraryRef.isLibRef(ref)) return "";
        String body  = ref.substring(LibraryRef.PREFIX.length());
        String[] parts = body.split("/", 3);
        if (parts.length != 3) return "";
        String folderId = parts[0], type = parts[1], entryId = parts[2];
        return switch (type) {
            case "audio" -> LibraryAudio.load(folderId, entryId).map(e -> e.displayName).orElse("");
            case "tts"   -> LibraryTts.load(folderId, entryId).map(e -> e.displayName).orElse("");
            default -> "";
        };
    }

    private void handleAssignSpeaker(HttpExchange exchange) throws IOException {
        JsonObject body = readJson(exchange);
        if (body == null || !body.has("dim") || !body.has("x") || !body.has("y") || !body.has("z") || !body.has("audioRef")) {
            WebAuthHelper.sendError(exchange, 400, "Missing dim/x/y/z/audioRef");
            return;
        }

        String dim      = body.get("dim").getAsString().trim();
        int    x        = body.get("x").getAsInt();
        int    y        = body.get("y").getAsInt();
        int    z        = body.get("z").getAsInt();
        String audioRef = body.get("audioRef").getAsString().trim();
        String trackTitle = body.has("trackTitle") ? body.get("trackTitle").getAsString() : "";
        String subtitle   = body.has("subtitle")   ? body.get("subtitle").getAsString()   : "";

        if (LibraryRef.isLibRef(audioRef) && LibraryRef.resolve(audioRef).isEmpty()) {
            WebAuthHelper.sendError(exchange, 404, "指定されたライブラリエントリが見つかりません");
            return;
        }

        Path toml = speakerTomlPath(dim, x, y, z);
        if (!Files.isRegularFile(toml)) {
            WebAuthHelper.sendError(exchange, 404, "スピーカーのTOML設定が見つかりません。インゲームGUIで一度保存してください。");
            return;
        }
        try (CommentedFileConfig cfg = CommentedFileConfig.builder(toml).sync().preserveInsertionOrder().build()) {
            cfg.load();
            cfg.set("audio.file", audioRef);
            cfg.set("display.track_title", trackTitle);
            cfg.set("display.subtitle", subtitle);
            cfg.save();
        }

        final String finalRef       = audioRef;
        final String finalTitle     = trackTitle;
        final String finalSubtitle  = subtitle;
        server.execute(() -> {
            ServerLevel level = server.getLevel(dimensionKey(dim));
            if (level == null) return;
            BlockEntity be = level.getBlockEntity(new BlockPos(x, y, z));
            if (be instanceof SpeakerBlockEntity speaker) {
                speaker.setAudioFile(finalRef);
                speaker.setAudioDisplayName(LibraryRef.resolveDisplayName(finalRef));
                speaker.setTrackTitle(finalTitle);
                speaker.setSubtitle(finalSubtitle);
                speaker.setChanged();
                speaker.syncConfigToClients();
            }
        });

        // 解決した audioDisplayName を含めてレスポンスを返す
        JsonObject resp = new JsonObject();
        resp.addProperty("ok", true);
        resp.addProperty("audioDisplayName", resolveAudioDisplayName(audioRef));
        WebAuthHelper.sendJson(exchange, 200, GSON.toJson(resp));
    }

    private void handleRenameSpeaker(HttpExchange exchange) throws IOException {
        JsonObject body = readJson(exchange);
        if (body == null || !body.has("dim") || !body.has("x") || !body.has("y") || !body.has("z") || !body.has("name")) {
            WebAuthHelper.sendError(exchange, 400, "Missing dim/x/y/z/name");
            return;
        }

        String dim  = body.get("dim").getAsString().trim();
        int    x    = body.get("x").getAsInt();
        int    y    = body.get("y").getAsInt();
        int    z    = body.get("z").getAsInt();
        String name = body.get("name").getAsString().trim();

        Path toml = speakerTomlPath(dim, x, y, z);
        if (!Files.isRegularFile(toml)) {
            WebAuthHelper.sendError(exchange, 404, "スピーカーのTOML設定が見つかりません。インゲームGUIで一度保存してください。");
            return;
        }
        try (CommentedFileConfig cfg = CommentedFileConfig.builder(toml).sync().preserveInsertionOrder().build()) {
            cfg.load();
            cfg.set("display.name", name);
            cfg.save();
        }

        WebAuthHelper.sendJson(exchange, 200, "{\"ok\":true}");
    }

    // ── Controllers ──────────────────────────────────────────────────────────

    private void handleListControllers(HttpExchange exchange) throws IOException {
        Path controllersRoot = controllersRoot();
        List<JsonObject> result = new ArrayList<>();

        if (Files.isDirectory(controllersRoot)) {
            try (Stream<Path> stream = Files.walk(controllersRoot)) {
                stream.filter(p -> p.toString().endsWith(".toml"))
                      .sorted()
                      .forEach(toml -> {
                          JsonObject entry = parseControllerToml(controllersRoot, toml);
                          if (entry != null) result.add(entry);
                      });
            }
        }

        WebAuthHelper.sendJson(exchange, 200, GSON.toJson(result));
    }

    private JsonObject parseControllerToml(Path controllersRoot, Path toml) {
        try {
            Path rel = controllersRoot.relativize(toml);
            String fileName = rel.getName(rel.getNameCount() - 1).toString();
            if (!fileName.endsWith(".toml")) return null;

            String baseName = fileName.substring(0, fileName.length() - 5);
            if (!baseName.startsWith("controller_")) return null;
            String posStr = baseName.substring("controller_".length());
            String[] coords = posStr.split("_");
            if (coords.length != 3) return null;
            int x = Integer.parseInt(coords[0]);
            int y = Integer.parseInt(coords[1]);
            int z = Integer.parseInt(coords[2]);

            String dim;
            if (rel.getNameCount() == 2) {
                dim = "minecraft:" + rel.getName(0).toString();
            } else if (rel.getNameCount() == 3) {
                dim = rel.getName(0).toString() + ":" + rel.getName(1).toString();
            } else {
                return null;
            }

            Map<Integer, List<String>> queues = new HashMap<>();
            String controllerId = "";
            try (CommentedFileConfig cfg = CommentedFileConfig.builder(toml).sync().preserveInsertionOrder().build()) {
                cfg.load();
                Object idObj = cfg.get("controller_id");
                if (idObj instanceof String s) controllerId = s;
                for (int sig = 1; sig <= 15; sig++) {
                    Object rawQ = cfg.get("rs_triggers." + sig + ".queue");
                    if (rawQ instanceof List<?> list) {
                        List<String> q = new ArrayList<>();
                        for (Object item : list) {
                            if (item instanceof String s && !s.isBlank()) q.add(s.trim());
                        }
                        if (!q.isEmpty()) queues.put(sig, q);
                    }
                }
            }

            JsonObject obj = new JsonObject();
            obj.addProperty("controllerId", controllerId);
            obj.addProperty("dim", dim);
            obj.addProperty("x", x);
            obj.addProperty("y", y);
            obj.addProperty("z", z);
            obj.add("queues", GSON.toJsonTree(queues));
            return obj;
        } catch (Exception e) {
            AudioBoundsSystem.LOGGER.warn("ABS: failed to parse controller TOML {}", toml, e);
            return null;
        }
    }

    private void handleAssignController(HttpExchange exchange) throws IOException {
        JsonObject body = readJson(exchange);
        if (body == null || !body.has("dim") || !body.has("x") || !body.has("y") || !body.has("z") || !body.has("queues")) {
            WebAuthHelper.sendError(exchange, 400, "Missing dim/x/y/z/queues");
            return;
        }

        String dim = body.get("dim").getAsString().trim();
        int    x   = body.get("x").getAsInt();
        int    y   = body.get("y").getAsInt();
        int    z   = body.get("z").getAsInt();

        Map<Integer, List<String>> queues = new HashMap<>();
        body.getAsJsonObject("queues").entrySet().forEach(e -> {
            try {
                int sig = Integer.parseInt(e.getKey());
                List<String> queue = new ArrayList<>();
                e.getValue().getAsJsonArray().forEach(el -> {
                    String ref = el.getAsString().trim();
                    if (!ref.isEmpty()) queue.add(ref);
                });
                if (!queue.isEmpty()) queues.put(sig, queue);
            } catch (Exception ignored) {}
        });

        Path toml = controllerTomlPath(dim, x, y, z);
        if (!Files.isRegularFile(toml)) {
            WebAuthHelper.sendError(exchange, 404, "コントローラーのTOML設定が見つかりません。インゲームGUIで一度保存してください。");
            return;
        }
        try (CommentedFileConfig cfg = CommentedFileConfig.builder(toml).sync().preserveInsertionOrder().build()) {
            cfg.load();
            for (int sig = 1; sig <= 15; sig++) {
                List<String> queue = queues.getOrDefault(sig, List.of());
                cfg.set("rs_triggers." + sig + ".queue", queue);
            }
            cfg.save();
        }

        server.execute(() -> {
            ServerLevel level = server.getLevel(dimensionKey(dim));
            if (level == null) return;
            BlockEntity be = level.getBlockEntity(new BlockPos(x, y, z));
            if (be instanceof AudioControllerBlockEntity controller) {
                controller.applyLoadedConfig(
                        controller.getControllerId(),
                        controller.getTargetSpeakerOffsets(),
                        queues,
                        controller.getRedstoneMode(),
                        controller.getRetriggerMode()
                );
                controller.setChanged();
                controller.syncConfigToClients();
            }
        });

        WebAuthHelper.sendJson(exchange, 200, "{\"ok\":true}");
    }

    // ── ヘルパ ────────────────────────────────────────────────────────────────

    private Path worldRoot() {
        return server.getWorldPath(LevelResource.ROOT);
    }

    private Path speakersRoot() {
        return worldRoot().resolve(AudioBoundsSystem.MOD_ID).resolve("speakers");
    }

    private Path controllersRoot() {
        return worldRoot().resolve(AudioBoundsSystem.MOD_ID).resolve("controllers");
    }

    private Path speakerTomlPath(String dim, int x, int y, int z) {
        String[] parts    = dim.split(":", 2);
        String namespace  = parts.length == 2 ? parts[0] : "minecraft";
        String path       = parts.length == 2 ? parts[1] : parts[0];
        String dimPath    = namespace + "/" + path.replace('/', '_');
        return speakersRoot().resolve(dimPath).resolve(x + "_" + y + "_" + z + ".toml");
    }

    private Path controllerTomlPath(String dim, int x, int y, int z) {
        String[] parts   = dim.split(":", 2);
        String namespace = parts.length == 2 ? parts[0] : "minecraft";
        String path      = parts.length == 2 ? parts[1] : parts[0];
        Path dimPath     = "minecraft".equals(namespace) ? Path.of(path) : Path.of(namespace, path);
        return controllersRoot().resolve(dimPath).resolve("controller_" + x + "_" + y + "_" + z + ".toml");
    }

    private static ResourceKey<Level> dimensionKey(String dim) {
        return ResourceKey.create(Registries.DIMENSION, new ResourceLocation(dim));
    }

    private boolean isOp(UUID playerUuid) {
        var player = server.getPlayerList().getPlayer(playerUuid);
        return player != null && server.getPlayerList().isOp(player.getGameProfile());
    }

    private static JsonObject readJson(HttpExchange exchange) throws IOException {
        try {
            return RequestBodyReader.readJson(exchange);
        } catch (RequestBodyReader.PayloadTooLargeException e) {
            throw e;
        } catch (RuntimeException e) {
            return null;
        }
    }
}
