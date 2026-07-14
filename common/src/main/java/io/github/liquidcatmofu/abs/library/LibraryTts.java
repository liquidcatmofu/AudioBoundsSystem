package io.github.liquidcatmofu.abs.library;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.liquidcatmofu.abs.AudioBoundsSystem;
import io.github.liquidcatmofu.abs.audio.AudioContent;
import io.github.liquidcatmofu.abs.audio.OggAudioDuration;
import io.github.liquidcatmofu.abs.io.AtomicFiles;
import io.github.liquidcatmofu.abs.server.ServerAudioCache;
import io.github.liquidcatmofu.abs.ttsbridge.TTSSynthesisRequest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Map;
import java.util.stream.Stream;

public final class LibraryTts {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private LibraryTts() {}

    private static Path ttsDir(String folderId) {
        return ABSLibrary.getRoot().resolve(folderId).resolve("tts");
    }

    public static List<TtsEntry> list(String folderId) {
        List<TtsEntry> result = new ArrayList<>();
        if (!ABSLibrary.isSafeId(folderId) || ABSLibrary.getRoot() == null) return result;
        Path dir = ttsDir(folderId);
        if (!Files.isDirectory(dir)) return result;
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(p -> p.getFileName().toString().endsWith(".json")).forEach(p -> {
                try {
                    result.add(GSON.fromJson(Files.readString(p, StandardCharsets.UTF_8), TtsEntry.class));
                } catch (IOException | RuntimeException e) {
                    AudioBoundsSystem.LOGGER.error("ABS: failed to read tts metadata {}", p, e);
                }
            });
        } catch (IOException | RuntimeException e) {
            AudioBoundsSystem.LOGGER.error("ABS: failed to list tts in {}", folderId, e);
        }
        result.sort((a, b) -> Long.compare(b.createdAt, a.createdAt));
        return result;
    }

    public static Optional<TtsEntry> load(String folderId, String id) {
        if (!ABSLibrary.isSafeId(folderId) || !ABSLibrary.isSafeId(id) || ABSLibrary.getRoot() == null) {
            return Optional.empty();
        }
        Path meta = ttsDir(folderId).resolve(id + ".json");
        if (!Files.exists(meta)) return Optional.empty();
        try {
            return Optional.ofNullable(GSON.fromJson(Files.readString(meta, StandardCharsets.UTF_8), TtsEntry.class));
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }

    /** 合成済みの ogg を保存し、メタデータ（スクリプト込み）を書き出す。 */
    public static synchronized TtsEntry create(String folderId, String displayName, TTSSynthesisRequest req,
                                               String speakerName, byte[] ogg, UUID creator) throws IOException {
        if (!ABSLibrary.isSafeId(folderId) || ABSLibrary.getRoot() == null) {
            throw new IOException("Invalid folder id or uninitialized library");
        }
        Path dir = ttsDir(folderId);
        Files.createDirectories(dir);

        AudioContent.requireOgg(ogg);
        TtsEntry duplicate = findByRequest(folderId, req).orElse(null);
        if (duplicate != null) {
            return duplicate;
        }
        String contentHash = AudioContent.sha256(ogg);

        String id = UUID.randomUUID().toString();
        String cacheFile = id + "-" + contentHash.substring(0, 16) + ".ogg";
        Path cachePath = ServerAudioCache.getDirectory().resolve(cacheFile);
        Path metadataPath = dir.resolve(id + ".json");
        boolean committed = false;
        try {
            AtomicFiles.write(cachePath, ogg);

            long durationTicks;
            try {
                durationTicks = OggAudioDuration.readDurationTicks(cachePath);
            } catch (IOException e) {
                durationTicks = 0;
            }

            TtsEntry entry = new TtsEntry();
            entry.id = id;
            entry.displayName = (displayName == null || displayName.isBlank())
                    ? trimForName(req.text) : displayName;
            entry.engineId = req.engineId;
            entry.speakerId = req.speakerId;
            entry.speakerName = speakerName;
            entry.text = req.text;
            entry.params = req.params;
            entry.cacheFile = cacheFile;
            entry.contentHash = contentHash;
            entry.durationTicks = durationTicks;
            entry.createdBy = creator.toString();
            entry.createdAt = System.currentTimeMillis();

            AtomicFiles.writeString(metadataPath, GSON.toJson(entry), StandardCharsets.UTF_8);
            committed = true;
            return entry;
        } finally {
            if (!committed) {
                Files.deleteIfExists(metadataPath);
                Files.deleteIfExists(cachePath);
            }
        }
    }

    /** 既存エントリを再合成して上書き保存する（ID・cacheFile は維持）。 */
    public static synchronized TtsEntry update(String folderId, String id, String displayName,
                                               TTSSynthesisRequest req, String speakerName, byte[] ogg) throws IOException {
        if (!ABSLibrary.isSafeId(folderId) || !ABSLibrary.isSafeId(id)) {
            throw new IOException("Invalid TTS entry path");
        }
        TtsEntry entry = load(folderId, id)
                .orElseThrow(() -> new IOException("TTS entry not found: " + id));

        AudioContent.requireOgg(ogg);
        String contentHash = AudioContent.sha256(ogg);
        String oldCacheFile = entry.cacheFile;
        String newCacheFile = id + "-" + contentHash.substring(0, 16) + ".ogg";
        Path cachePath = ServerAudioCache.getDirectory().resolve(newCacheFile);
        AtomicFiles.write(cachePath, ogg);

        long durationTicks;
        try {
            durationTicks = OggAudioDuration.readDurationTicks(cachePath);
        } catch (IOException e) {
            durationTicks = 0;
        }

        entry.engineId = req.engineId;
        entry.speakerId = req.speakerId;
        entry.speakerName = speakerName;
        entry.text = req.text;
        entry.params = req.params;
        entry.cacheFile = newCacheFile;
        entry.contentHash = contentHash;
        entry.durationTicks = durationTicks;
        if (displayName != null && !displayName.isBlank()) {
            entry.displayName = displayName;
        } else {
            entry.displayName = trimForName(req.text);
        }

        try {
            AtomicFiles.writeString(ttsDir(folderId).resolve(id + ".json"),
                    GSON.toJson(entry), StandardCharsets.UTF_8);
            if (oldCacheFile != null && !oldCacheFile.equals(newCacheFile)) {
                Optional<Path> oldCachePath = ServerAudioCache.resolve(oldCacheFile);
                if (oldCachePath.isPresent()) Files.deleteIfExists(oldCachePath.get());
            }
            return entry;
        } catch (IOException e) {
            if (!newCacheFile.equals(oldCacheFile)) {
                Files.deleteIfExists(cachePath);
            }
            throw e;
        }
    }

    public static boolean delete(String folderId, String id) throws IOException {
        TtsEntry entry = load(folderId, id).orElse(null);
        if (entry == null) return false;
        Optional<Path> cachePath = cacheFilePath(entry);
        if (cachePath.isPresent()) Files.deleteIfExists(cachePath.get());
        Files.deleteIfExists(ttsDir(folderId).resolve(id + ".json"));
        return true;
    }

    public static void purgeCacheForFolder(String folderId) {
        for (TtsEntry entry : list(folderId)) {
            try {
                Optional<Path> cachePath = cacheFilePath(entry);
                if (cachePath.isPresent()) Files.deleteIfExists(cachePath.get());
            } catch (IOException ignored) {}
        }
    }

    public static Optional<Path> cacheFilePath(TtsEntry entry) {
        return entry == null ? Optional.empty() : ServerAudioCache.resolve(entry.cacheFile);
    }

    /** 同一の合成入力を持ち、再利用可能なOggが存在するエントリを返す。 */
    public static Optional<TtsEntry> findByRequest(String folderId, TTSSynthesisRequest req) {
        if (!ABSLibrary.isSafeId(folderId) || req == null) return Optional.empty();
        for (TtsEntry entry : list(folderId)) {
            if (!sameSynthesisRequest(entry, req)) continue;
            Path cached = cacheFilePath(entry).orElse(null);
            if (cached != null && AudioContent.hasOggHeader(cached)) return Optional.of(entry);
        }
        return Optional.empty();
    }

    private static String trimForName(String text) {
        if (text == null) return "TTS";
        String t = text.strip();
        return t.length() > 20 ? t.substring(0, 20) + "…" : t;
    }

    static boolean sameSynthesisRequest(TtsEntry entry, TTSSynthesisRequest req) {
        return entry != null && req != null
                && java.util.Objects.equals(entry.engineId, req.engineId)
                && java.util.Objects.equals(entry.speakerId, req.speakerId)
                && java.util.Objects.equals(entry.text, req.text)
                && normalizedParams(entry.params).equals(normalizedParams(req.params));
    }

    private static Map<String, Double> normalizedParams(Map<String, Double> params) {
        return params == null ? Map.of() : Map.copyOf(params);
    }
}
