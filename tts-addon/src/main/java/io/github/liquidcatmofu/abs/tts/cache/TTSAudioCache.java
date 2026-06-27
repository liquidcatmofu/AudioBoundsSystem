package io.github.liquidcatmofu.abs.tts.cache;

import io.github.liquidcatmofu.abs.tts.TTSAddon;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class TTSAudioCache {
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

    /** キャッシュファイルの絶対パスを返す。 */
    public static Path resolve(String speakerId, String text) {
        return cacheDir.resolve(computeKey(speakerId, text) + ".ogg");
    }

    public static boolean exists(String speakerId, String text) {
        return cacheDir != null && Files.exists(resolve(speakerId, text));
    }

    /**
     * Ogg バイト列をキャッシュに保存し、abs_cache/ からの相対パス（例: "tts/abc123.ogg"）を返す。
     */
    public static String save(byte[] oggBytes, String speakerId, String text) throws IOException {
        if (cacheDir == null) {
            throw new IllegalStateException("TTSAudioCache not initialized");
        }
        Path path = resolve(speakerId, text);
        Files.write(path, oggBytes);
        return "tts/" + path.getFileName().toString();
    }

    public static Path getCacheDir() {
        return cacheDir;
    }
}
