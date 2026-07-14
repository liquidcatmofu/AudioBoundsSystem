package io.github.liquidcatmofu.abs.library;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.liquidcatmofu.abs.AudioBoundsSystem;
import io.github.liquidcatmofu.abs.io.AtomicFiles;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class ABSLibrary {
    private static final Pattern SAFE_ID = Pattern.compile("^[a-z0-9_-]{1,64}$");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Path libraryRoot;

    private ABSLibrary() {}

    public static void init(Path serverDir) {
        libraryRoot = serverDir.resolve("abs_library");
        try {
            Files.createDirectories(libraryRoot);
        } catch (IOException | RuntimeException e) {
            AudioBoundsSystem.LOGGER.error("ABS: failed to create abs_library dir", e);
        }
    }

    public static Path getRoot() {
        return libraryRoot;
    }

    /** フォルダ ID がパス安全か（UUID 文字列も通る） */
    public static boolean isSafeId(String id) {
        return id != null && SAFE_ID.matcher(id).matches();
    }

    // ── 読み書き ───────────────────────────────────────

    public static Optional<LibraryFolder> loadFolder(String id) {
        if (!isSafeId(id) || libraryRoot == null) return Optional.empty();
        Path meta = libraryRoot.resolve(id).resolve("meta.json");
        if (!Files.exists(meta)) return Optional.empty();
        try {
            return Optional.ofNullable(GSON.fromJson(Files.readString(meta, StandardCharsets.UTF_8), LibraryFolder.class));
        } catch (IOException | RuntimeException e) {
            AudioBoundsSystem.LOGGER.error("ABS: failed to load folder {}", id, e);
            return Optional.empty();
        }
    }

    public static void saveFolder(LibraryFolder folder) throws IOException {
        if (folder == null || !isSafeId(folder.id) || libraryRoot == null) {
            throw new IOException("Invalid folder or uninitialized library");
        }
        Path dir = libraryRoot.resolve(folder.id);
        Files.createDirectories(dir.resolve("audio"));
        Files.createDirectories(dir.resolve("tts"));
        Files.createDirectories(dir.resolve("sequences"));
        AtomicFiles.writeString(dir.resolve("meta.json"), GSON.toJson(folder), StandardCharsets.UTF_8);
    }

    public static List<LibraryFolder> loadAll() {
        List<LibraryFolder> all = new ArrayList<>();
        if (libraryRoot == null) return all;
        try (Stream<Path> dirs = Files.list(libraryRoot)) {
            dirs.filter(Files::isDirectory).forEach(dir ->
                loadFolder(dir.getFileName().toString()).ifPresent(all::add));
        } catch (IOException e) {
            AudioBoundsSystem.LOGGER.error("ABS: failed to list library folders", e);
        }
        return all;
    }

    // ── 作成 ───────────────────────────────────────────

    /** プレイヤーのルートフォルダ（id = UUID）が無ければ作成し、返す。 */
    public static LibraryFolder ensureRoot(UUID ownerUuid, String ownerName) {
        String id = ownerUuid.toString();
        Optional<LibraryFolder> existing = loadFolder(id);
        if (existing.isPresent()) return existing.get();
        LibraryFolder root = new LibraryFolder(id, ownerName, null, ownerUuid, ownerName);
        try {
            saveFolder(root);
        } catch (IOException e) {
            AudioBoundsSystem.LOGGER.error("ABS: failed to create root folder for {}", ownerName, e);
        }
        return root;
    }

    /** 親フォルダの配下にサブフォルダを作成する。owner は親（ルート）から引き継ぐ。 */
    public static LibraryFolder createSubFolder(LibraryFolder parent, String displayName) throws IOException {
        String id = UUID.randomUUID().toString();
        LibraryFolder folder = new LibraryFolder(id, displayName, parent.id,
                UUID.fromString(parent.ownerUuid), parent.ownerName);
        saveFolder(folder);
        return folder;
    }

    /** フォルダとその全子孫を削除する（ルートは不可）。 */
    public static boolean deleteFolderRecursive(String id) throws IOException {
        if (!isSafeId(id) || libraryRoot == null) return false;
        LibraryFolder target = loadFolder(id).orElse(null);
        if (target == null || target.isRoot()) return false;

        Map<String, List<LibraryFolder>> byParent = childrenIndex(loadAll());
        Set<String> toDelete = new LinkedHashSet<>();
        collectSubtree(id, byParent, toDelete);
        for (String fid : toDelete) {
            LibraryAudio.purgeCacheForFolder(fid);   // 配下音声の abs_cache ogg を削除
            LibraryTts.purgeCacheForFolder(fid);     // 配下 TTS の abs_cache ogg を削除
            deleteDir(libraryRoot.resolve(fid));
        }
        return true;
    }

    // ── アクセス制御 ───────────────────────────────────

    /** フォルダに対するプレイヤーの実効アクセス。祖先の allowedPlayers も継承。 */
    public static FolderAccess access(LibraryFolder folder, UUID player, boolean isOp) {
        if (folder == null || player == null) return FolderAccess.NONE;
        if (isOp) return FolderAccess.OWNER;
        if (player.toString().equals(folder.ownerUuid)) return FolderAccess.OWNER;
        LibraryFolder cur = folder;
        Set<String> visited = new LinkedHashSet<>();
        while (cur != null && cur.id != null && visited.add(cur.id)) {
            if (cur.allowedPlayers != null && cur.allowedPlayers.contains(player.toString())) {
                return FolderAccess.ALLOWED;
            }
            cur = cur.parentId == null ? null : loadFolder(cur.parentId).orElse(null);
        }
        return FolderAccess.NONE;
    }

    /**
     * プレイヤーが閲覧できる全フォルダ（自分のルート以下 + 共有された各サブツリー）。
     * クライアントは parentId からツリーを再構成する。
     */
    public static List<LibraryFolder> listAccessible(UUID player, boolean isOp) {
        List<LibraryFolder> all = loadAll();
        if (isOp) return all;

        Map<String, List<LibraryFolder>> byParent = childrenIndex(all);
        Set<String> visible = new LinkedHashSet<>();

        // 自分のルート以下
        String rootId = player.toString();
        if (loadFolder(rootId).isPresent()) {
            collectSubtree(rootId, byParent, visible);
        }
        // 明示的に共有されたフォルダ以下
        for (LibraryFolder f : all) {
            if (!visible.contains(f.id) && f.allowedPlayers != null
                    && f.allowedPlayers.contains(player.toString())) {
                collectSubtree(f.id, byParent, visible);
            }
        }

        List<LibraryFolder> result = new ArrayList<>();
        for (LibraryFolder f : all) {
            if (visible.contains(f.id)) result.add(f);
        }
        return result;
    }

    // ── 内部ヘルパ ─────────────────────────────────────

    private static Map<String, List<LibraryFolder>> childrenIndex(List<LibraryFolder> all) {
        Map<String, List<LibraryFolder>> byParent = new LinkedHashMap<>();
        for (LibraryFolder f : all) {
            byParent.computeIfAbsent(f.parentId, k -> new ArrayList<>()).add(f);
        }
        return byParent;
    }

    private static void collectSubtree(String id, Map<String, List<LibraryFolder>> byParent, Set<String> out) {
        if (!out.add(id)) return;
        List<LibraryFolder> children = byParent.get(id);
        if (children != null) {
            for (LibraryFolder child : children) {
                collectSubtree(child.id, byParent, out);
            }
        }
    }

    private static void deleteDir(Path path) throws IOException {
        if (!Files.exists(path)) return;
        if (Files.isDirectory(path)) {
            try (Stream<Path> children = Files.list(path)) {
                for (Path child : children.toList()) {
                    deleteDir(child);
                }
            }
        }
        Files.delete(path);
    }
}
