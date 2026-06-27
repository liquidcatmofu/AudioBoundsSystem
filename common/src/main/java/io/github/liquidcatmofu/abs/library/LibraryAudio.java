package io.github.liquidcatmofu.abs.library;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.liquidcatmofu.abs.AudioBoundsSystem;
import io.github.liquidcatmofu.abs.audio.FfmpegTranscoder;
import io.github.liquidcatmofu.abs.audio.OggAudioDuration;
import io.github.liquidcatmofu.abs.server.ABSHttpServer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

public final class LibraryAudio {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private LibraryAudio() {}

    private static Path audioDir(String folderId) {
        return ABSLibrary.getRoot().resolve(folderId).resolve("audio");
    }

    public static List<AudioEntry> list(String folderId) {
        List<AudioEntry> result = new ArrayList<>();
        Path dir = audioDir(folderId);
        if (!Files.isDirectory(dir)) return result;
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(p -> p.getFileName().toString().endsWith(".json")).forEach(p -> {
                try {
                    result.add(GSON.fromJson(Files.readString(p, StandardCharsets.UTF_8), AudioEntry.class));
                } catch (IOException e) {
                    AudioBoundsSystem.LOGGER.error("ABS: failed to read audio metadata {}", p, e);
                }
            });
        } catch (IOException e) {
            AudioBoundsSystem.LOGGER.error("ABS: failed to list audio in {}", folderId, e);
        }
        result.sort((a, b) -> Long.compare(b.uploadedAt, a.uploadedAt));
        return result;
    }

    public static Optional<AudioEntry> load(String folderId, String audioId) {
        Path meta = audioDir(folderId).resolve(audioId + ".json");
        if (!Files.exists(meta)) return Optional.empty();
        try {
            return Optional.ofNullable(GSON.fromJson(Files.readString(meta, StandardCharsets.UTF_8), AudioEntry.class));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    /** アップロードされたファイルを Ogg に変換して取り込み、メタデータを保存する。 */
    public static AudioEntry importAudio(String folderId, byte[] data, String originalName, UUID uploader)
            throws IOException, InterruptedException {
        Path dir = audioDir(folderId);
        Files.createDirectories(dir);

        String ext = extensionOf(originalName);
        String id = UUID.randomUUID().toString();

        // 原本を保存（将来の非破壊編集用）
        String srcFile = id + ".src." + ext;
        Files.write(dir.resolve(srcFile), data);

        // Ogg 変換 → abs_cache へ
        byte[] ogg = FfmpegTranscoder.toOgg(data, ext);
        String cacheFile = id + ".ogg";
        Path cachePath = ABSHttpServer.getCacheDir().resolve(cacheFile);
        Files.write(cachePath, ogg);

        long durationTicks;
        try {
            durationTicks = OggAudioDuration.readDurationTicks(cachePath);
        } catch (IOException e) {
            durationTicks = 0;
        }

        AudioEntry entry = new AudioEntry();
        entry.id = id;
        entry.displayName = stripExtension(originalName);
        entry.originalName = originalName;
        entry.srcFile = srcFile;
        entry.cacheFile = cacheFile;
        entry.durationTicks = durationTicks;
        entry.uploadedBy = uploader.toString();
        entry.uploadedAt = System.currentTimeMillis();

        Files.writeString(dir.resolve(id + ".json"), GSON.toJson(entry), StandardCharsets.UTF_8);
        return entry;
    }

    public static boolean delete(String folderId, String audioId) throws IOException {
        AudioEntry entry = load(folderId, audioId).orElse(null);
        if (entry == null) return false;
        Path dir = audioDir(folderId);
        // 配信用 ogg
        if (entry.cacheFile != null) {
            Files.deleteIfExists(ABSHttpServer.getCacheDir().resolve(entry.cacheFile));
        }
        // 原本
        if (entry.srcFile != null) {
            Files.deleteIfExists(dir.resolve(entry.srcFile));
        }
        // メタデータ
        Files.deleteIfExists(dir.resolve(audioId + ".json"));
        return true;
    }

    /** フォルダ削除時に、配下の音声が参照する abs_cache の ogg を消す。 */
    public static void purgeCacheForFolder(String folderId) {
        for (AudioEntry entry : list(folderId)) {
            if (entry.cacheFile != null) {
                try {
                    Files.deleteIfExists(ABSHttpServer.getCacheDir().resolve(entry.cacheFile));
                } catch (IOException ignored) {}
            }
        }
    }

    public static Path cacheFilePath(AudioEntry entry) {
        return ABSHttpServer.getCacheDir().resolve(entry.cacheFile);
    }

    // ── helpers ────────────────────────────────────────

    private static String extensionOf(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) return "bin";
        String ext = name.substring(dot + 1).toLowerCase().replaceAll("[^a-z0-9]", "");
        return ext.isEmpty() ? "bin" : ext;
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}
