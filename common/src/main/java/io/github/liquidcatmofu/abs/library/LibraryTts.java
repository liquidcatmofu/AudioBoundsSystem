package io.github.liquidcatmofu.abs.library;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.liquidcatmofu.abs.AudioBoundsSystem;
import io.github.liquidcatmofu.abs.audio.OggAudioDuration;
import io.github.liquidcatmofu.abs.server.ABSHttpServer;
import io.github.liquidcatmofu.abs.ttsbridge.TTSSynthesisRequest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

public final class LibraryTts {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private LibraryTts() {}

    private static Path ttsDir(String folderId) {
        return ABSLibrary.getRoot().resolve(folderId).resolve("tts");
    }

    public static List<TtsEntry> list(String folderId) {
        List<TtsEntry> result = new ArrayList<>();
        Path dir = ttsDir(folderId);
        if (!Files.isDirectory(dir)) return result;
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(p -> p.getFileName().toString().endsWith(".json")).forEach(p -> {
                try {
                    result.add(GSON.fromJson(Files.readString(p, StandardCharsets.UTF_8), TtsEntry.class));
                } catch (IOException e) {
                    AudioBoundsSystem.LOGGER.error("ABS: failed to read tts metadata {}", p, e);
                }
            });
        } catch (IOException e) {
            AudioBoundsSystem.LOGGER.error("ABS: failed to list tts in {}", folderId, e);
        }
        result.sort((a, b) -> Long.compare(b.createdAt, a.createdAt));
        return result;
    }

    public static Optional<TtsEntry> load(String folderId, String id) {
        Path meta = ttsDir(folderId).resolve(id + ".json");
        if (!Files.exists(meta)) return Optional.empty();
        try {
            return Optional.ofNullable(GSON.fromJson(Files.readString(meta, StandardCharsets.UTF_8), TtsEntry.class));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    /** 合成済みの ogg を保存し、メタデータ（スクリプト込み）を書き出す。 */
    public static TtsEntry create(String folderId, String displayName, TTSSynthesisRequest req,
                                  String speakerName, byte[] ogg, UUID creator) throws IOException {
        Path dir = ttsDir(folderId);
        Files.createDirectories(dir);

        String id = UUID.randomUUID().toString();
        String cacheFile = id + ".ogg";
        Path cachePath = ABSHttpServer.getCacheDir().resolve(cacheFile);
        Files.write(cachePath, ogg);

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
        entry.durationTicks = durationTicks;
        entry.createdBy = creator.toString();
        entry.createdAt = System.currentTimeMillis();

        Files.writeString(dir.resolve(id + ".json"), GSON.toJson(entry), StandardCharsets.UTF_8);
        return entry;
    }

    public static boolean delete(String folderId, String id) throws IOException {
        TtsEntry entry = load(folderId, id).orElse(null);
        if (entry == null) return false;
        if (entry.cacheFile != null) {
            Files.deleteIfExists(ABSHttpServer.getCacheDir().resolve(entry.cacheFile));
        }
        Files.deleteIfExists(ttsDir(folderId).resolve(id + ".json"));
        return true;
    }

    public static void purgeCacheForFolder(String folderId) {
        for (TtsEntry entry : list(folderId)) {
            if (entry.cacheFile != null) {
                try {
                    Files.deleteIfExists(ABSHttpServer.getCacheDir().resolve(entry.cacheFile));
                } catch (IOException ignored) {}
            }
        }
    }

    public static Path cacheFilePath(TtsEntry entry) {
        return ABSHttpServer.getCacheDir().resolve(entry.cacheFile);
    }

    private static String trimForName(String text) {
        if (text == null) return "TTS";
        String t = text.strip();
        return t.length() > 20 ? t.substring(0, 20) + "…" : t;
    }
}
