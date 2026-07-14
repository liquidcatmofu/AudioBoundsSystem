package io.github.liquidcatmofu.abs.library;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.liquidcatmofu.abs.AudioBoundsSystem;
import io.github.liquidcatmofu.abs.audio.FfmpegTranscoder;
import io.github.liquidcatmofu.abs.audio.AudioContent;
import io.github.liquidcatmofu.abs.audio.OggAudioDuration;
import io.github.liquidcatmofu.abs.io.AtomicFiles;
import io.github.liquidcatmofu.abs.server.ServerAudioCache;

import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
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
        if (!ABSLibrary.isSafeId(folderId)) return result;
        Path dir = audioDir(folderId);
        if (!Files.isDirectory(dir)) return result;
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(p -> p.getFileName().toString().endsWith(".json")).forEach(p -> {
                try {
                    result.add(GSON.fromJson(Files.readString(p, StandardCharsets.UTF_8), AudioEntry.class));
                } catch (IOException | RuntimeException e) {
                    AudioBoundsSystem.LOGGER.error("ABS: failed to read audio metadata {}", p, e);
                }
            });
        } catch (IOException | RuntimeException e) {
            AudioBoundsSystem.LOGGER.error("ABS: failed to list audio in {}", folderId, e);
        }
        result.sort((a, b) -> Long.compare(b.uploadedAt, a.uploadedAt));
        return result;
    }

    public static Optional<AudioEntry> load(String folderId, String audioId) {
        if (!ABSLibrary.isSafeId(folderId) || !ABSLibrary.isSafeId(audioId)) return Optional.empty();
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
        return importAudio(folderId, new ByteArrayInputStream(data), data.length, originalName, uploader);
    }

    public static AudioEntry importAudio(String folderId, InputStream data, long maxBytes,
                                         String originalName, UUID uploader)
            throws IOException, InterruptedException {
        if (!ABSLibrary.isSafeId(folderId)) throw new IOException("Invalid folder id");
        Path dir = audioDir(folderId);
        Files.createDirectories(dir);

        String ext = extensionOf(originalName);
        String id = UUID.randomUUID().toString();

        // 原本を上限付きで保存し、変換と検証後にキャッシュ・メタデータを確定する。
        String srcFile = id + ".src." + ext;
        Path srcPath = dir.resolve(srcFile);
        Path metadataPath = dir.resolve(id + ".json");
        Path cachePath = null;
        boolean committed = false;
        try {
            long inputSize = AtomicFiles.write(srcPath, data, maxBytes);
            if (inputSize == 0) throw new EmptyUploadException();
            byte[] ogg = FfmpegTranscoder.toOgg(srcPath, ext);
            AudioContent.requireOgg(ogg);
            String contentHash = AudioContent.sha256(ogg);
            String cacheFile = id + "-" + contentHash.substring(0, 16) + ".ogg";
            cachePath = ServerAudioCache.getDirectory().resolve(cacheFile);
            AtomicFiles.write(cachePath, ogg);

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
            entry.contentHash = contentHash;
            entry.durationTicks = durationTicks;
            entry.uploadedBy = uploader.toString();
            entry.uploadedAt = System.currentTimeMillis();

            AtomicFiles.writeString(metadataPath, GSON.toJson(entry), StandardCharsets.UTF_8);
            committed = true;
            return entry;
        } finally {
            if (!committed) {
                Files.deleteIfExists(metadataPath);
                if (cachePath != null) Files.deleteIfExists(cachePath);
                Files.deleteIfExists(srcPath);
            }
        }
    }

    public static final class EmptyUploadException extends IOException {
        public EmptyUploadException() {
            super("Empty upload");
        }
    }

    public static boolean delete(String folderId, String audioId) throws IOException {
        AudioEntry entry = load(folderId, audioId).orElse(null);
        if (entry == null) return false;
        Path dir = audioDir(folderId);
        // 配信用 ogg
        Optional<Path> cachePath = cacheFilePath(entry);
        if (cachePath.isPresent()) Files.deleteIfExists(cachePath.get());
        // 原本
        if (entry.srcFile != null) {
            Optional<Path> sourcePath = resolveDirectChild(dir, entry.srcFile);
            if (sourcePath.isPresent()) Files.deleteIfExists(sourcePath.get());
        }
        // メタデータ
        Files.deleteIfExists(dir.resolve(audioId + ".json"));
        return true;
    }

    /** フォルダ削除時に、配下の音声が参照する abs_cache の ogg を消す。 */
    public static void purgeCacheForFolder(String folderId) {
        for (AudioEntry entry : list(folderId)) {
            try {
                Optional<Path> cachePath = cacheFilePath(entry);
                if (cachePath.isPresent()) Files.deleteIfExists(cachePath.get());
            } catch (IOException ignored) {}
        }
    }

    public static Optional<Path> cacheFilePath(AudioEntry entry) {
        return entry == null ? Optional.empty() : ServerAudioCache.resolve(entry.cacheFile);
    }

    private static Optional<Path> resolveDirectChild(Path directory, String fileName) {
        if (directory == null || fileName == null || fileName.isBlank()) return Optional.empty();
        try {
            Path root = directory.toAbsolutePath().normalize();
            Path resolved = root.resolve(fileName).normalize();
            return root.equals(resolved.getParent()) ? Optional.of(resolved) : Optional.empty();
        } catch (RuntimeException e) {
            return Optional.empty();
        }
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
