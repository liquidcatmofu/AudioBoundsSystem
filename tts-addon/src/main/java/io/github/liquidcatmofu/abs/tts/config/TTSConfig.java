package io.github.liquidcatmofu.abs.tts.config;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import io.github.liquidcatmofu.abs.tts.TTSAddon;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** TTS Addonのグローバル設定。変更は次回ゲーム起動時に反映される。 */
public record TTSConfig(String ffmpegPath, Map<String, String> engineUrls, long cacheMaxBytes) {
    public static final long DEFAULT_CACHE_MAX_MIB = 128L;
    public static final long MAX_CACHE_MAX_MIB = 1_048_576L; // 1 TiB
    public static final TTSConfig DEFAULT = new TTSConfig(
            "ffmpeg", Map.of(), mebibytesToBytes(DEFAULT_CACHE_MAX_MIB));

    private static TTSConfig instance = DEFAULT;

    public static TTSConfig get() { return instance; }
    public static void set(TTSConfig config) { instance = config; }

    public static TTSConfig load(Path path) {
        try {
            Files.createDirectories(path.toAbsolutePath().normalize().getParent());
        } catch (IOException | RuntimeException e) {
            TTSAddon.LOGGER.error("ABS TTS: failed to create config directory for {}", path, e);
            instance = DEFAULT;
            return instance;
        }

        try (CommentedFileConfig config = CommentedFileConfig.builder(path)
                .sync().preserveInsertionOrder().build()) {
            config.load();
            String ffmpegPath = readNonBlankString(config.get("ffmpeg.path"), DEFAULT.ffmpegPath());
            long cacheMaxMiB = readCacheMaxMiB(config.get("cache.maxSizeMiB"));
            instance = new TTSConfig(ffmpegPath, Map.of(), mebibytesToBytes(cacheMaxMiB));

            config.set("ffmpeg.path", ffmpegPath);
            config.setComment("ffmpeg.path", "FFmpeg executable name or absolute path");
            config.set("cache.maxSizeMiB", cacheMaxMiB);
            config.setComment("cache.maxSizeMiB",
                    "Maximum pre-synthesis TTS cache size in MiB (1 to " + MAX_CACHE_MAX_MIB + ")");
            config.save();
            TTSAddon.LOGGER.info("ABS TTS: loaded config {} (cache {} MiB)", path, cacheMaxMiB);
            return instance;
        } catch (RuntimeException e) {
            TTSAddon.LOGGER.error("ABS TTS: failed to load config {}, using defaults", path, e);
            instance = DEFAULT;
            return instance;
        }
    }

    private static long readCacheMaxMiB(Object value) {
        if (!(value instanceof Number number)) return DEFAULT_CACHE_MAX_MIB;
        long result = number.longValue();
        return result >= 1 && result <= MAX_CACHE_MAX_MIB ? result : DEFAULT_CACHE_MAX_MIB;
    }

    private static String readNonBlankString(Object value, String fallback) {
        return value instanceof String string && !string.isBlank() ? string : fallback;
    }

    private static long mebibytesToBytes(long value) {
        return Math.multiplyExact(value, 1024L * 1024L);
    }

    /**
     * エンジン固有の URL オーバーライドを返す。
     * 未設定の場合は null（プロバイダーが持つデフォルト URL を使用）。
     */
    public String engineUrl(String engineId) {
        return engineUrls != null ? engineUrls.get(engineId) : null;
    }
}
