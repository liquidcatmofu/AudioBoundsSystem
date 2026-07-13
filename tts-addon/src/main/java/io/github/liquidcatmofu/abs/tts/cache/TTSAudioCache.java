package io.github.liquidcatmofu.abs.tts.cache;

import io.github.liquidcatmofu.abs.tts.TTSAddon;
import io.github.liquidcatmofu.abs.audio.AudioContent;
import io.github.liquidcatmofu.abs.io.AtomicFiles;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

public final class TTSAudioCache {
    private static final long MAX_CACHE_FILE_BYTES = 64L * 1024L * 1024L;
    private static Path cacheDir;

    private TTSAudioCache() {}

    public static void init(Path serverDir) {
        cacheDir = serverDir.resolve("abs_cache").resolve("tts");
        try {
            Files.createDirectories(cacheDir);
            TTSAddon.LOGGER.info("ABS TTS: cache dir ready at {}", cacheDir);
        } catch (IOException e) {
            TTSAddon.LOGGER.error("ABS TTS: failed to create TTS cache dir", e);
        }
    }

    /** speakerId と text の SHA-256 先頭 16 文字をキャッシュキーとして返す。 */
    public static String computeKey(String speakerId, String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((speakerId + ":" + text).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 合成結果を一意に表す完全なキャッシュキーを返す。
     * パラメータはキー名でソートするため、Map の反復順序には依存しない。
     *
     * <p>{@link #computeKey(String, String)} は簡易コマンド用、こちらはWebUI/API合成用。</p>
     */
    public static String computeKey(String engineId, String speakerId, String text,
                                    Map<String, Double> params) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            updateField(digest, engineId);
            updateField(digest, speakerId);
            updateField(digest, text);
            if (params != null) {
                for (Map.Entry<String, Double> entry : new TreeMap<>(params).entrySet()) {
                    updateField(digest, entry.getKey());
                    updateField(digest, entry.getValue() == null
                            ? "null" : Double.toHexString(entry.getValue()));
                }
            }
            return toHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private static void updateField(MessageDigest digest, String value) {
        byte[] bytes = (value == null ? "" : value).getBytes(StandardCharsets.UTF_8);
        digest.update((byte) (bytes.length >>> 24));
        digest.update((byte) (bytes.length >>> 16));
        digest.update((byte) (bytes.length >>> 8));
        digest.update((byte) bytes.length);
        digest.update(bytes);
    }

    private static String toHex(byte[] hash) {
        StringBuilder hex = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }

    /** キャッシュファイルの絶対パスを返す。 */
    public static Path resolve(String speakerId, String text) {
        return cacheDir.resolve(computeKey(speakerId, text) + ".ogg");
    }

    public static boolean exists(String speakerId, String text) {
        return cacheDir != null && Files.exists(resolve(speakerId, text));
    }

    public static Optional<byte[]> load(String engineId, String speakerId, String text,
                                        Map<String, Double> params) {
        if (cacheDir == null) return Optional.empty();
        Path path = resolve(engineId, speakerId, text, params);
        try {
            if (!AudioContent.hasOggHeader(path)) {
                Files.deleteIfExists(path);
                return Optional.empty();
            }
            long size = Files.size(path);
            if (size <= 0 || size > MAX_CACHE_FILE_BYTES) {
                Files.deleteIfExists(path);
                return Optional.empty();
            }
            return Optional.of(Files.readAllBytes(path));
        } catch (IOException | RuntimeException e) {
            TTSAddon.LOGGER.warn("ABS TTS: failed to read synthesis cache {}", path, e);
            return Optional.empty();
        }
    }

    public static void save(String engineId, String speakerId, String text,
                            Map<String, Double> params, byte[] oggBytes) throws IOException {
        if (cacheDir == null) return;
        AudioContent.requireOgg(oggBytes);
        AtomicFiles.write(resolve(engineId, speakerId, text, params), oggBytes);
    }

    private static Path resolve(String engineId, String speakerId, String text,
                                Map<String, Double> params) {
        return cacheDir.resolve(computeKey(engineId, speakerId, text, params) + ".ogg");
    }

    /**
     * Ogg バイト列をキャッシュに保存し、abs_cache/ からの相対パス（例: "tts/abc123.ogg"）を返す。
     */
    public static String save(byte[] oggBytes, String speakerId, String text) throws IOException {
        if (cacheDir == null) {
            throw new IllegalStateException("TTSAudioCache not initialized");
        }
        Path path = resolve(speakerId, text);
        AudioContent.requireOgg(oggBytes);
        AtomicFiles.write(path, oggBytes);
        return "tts/" + path.getFileName().toString();
    }

    public static Path getCacheDir() {
        return cacheDir;
    }
}
