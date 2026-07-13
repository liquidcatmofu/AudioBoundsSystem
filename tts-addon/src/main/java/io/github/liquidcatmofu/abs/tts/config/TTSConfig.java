package io.github.liquidcatmofu.abs.tts.config;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import io.github.liquidcatmofu.abs.tts.TTSAddon;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** TTS Addonのグローバル設定。変更は次回ゲーム起動時に反映される。 */
public record TTSConfig(String ffmpegPath, Map<String, String> engineUrls, long cacheMaxBytes) {
    public static final long DEFAULT_CACHE_MAX_MIB = 128L;
    public static final long MAX_CACHE_MAX_MIB = 1_048_576L; // 1 TiB
    public static final Map<String, String> DEFAULT_ENGINE_URLS = defaultEngineUrls();
    public static final TTSConfig DEFAULT = new TTSConfig(
            "ffmpeg", DEFAULT_ENGINE_URLS, mebibytesToBytes(DEFAULT_CACHE_MAX_MIB));

    private static TTSConfig instance = DEFAULT;

    public TTSConfig {
        engineUrls = engineUrls == null ? Map.of() : Map.copyOf(engineUrls);
    }

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
            Map<String, String> engineUrls = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : DEFAULT_ENGINE_URLS.entrySet()) {
                String pathKey = "engines." + entry.getKey();
                String url = readEngineUrl(config.get(pathKey), entry.getValue());
                engineUrls.put(entry.getKey(), url);
                config.set(pathKey, url);
                config.setComment(pathKey, "Base URL for the " + entry.getKey() + " compatible API");
            }
            instance = new TTSConfig(ffmpegPath, Collections.unmodifiableMap(engineUrls),
                    mebibytesToBytes(cacheMaxMiB));

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

    private static String readEngineUrl(Object value, String fallback) {
        if (!(value instanceof String string) || string.isBlank()) return fallback;
        String normalized = stripTrailingSlashes(string.trim());
        try {
            URI uri = new URI(normalized);
            String scheme = uri.getScheme();
            int port = uri.getPort();
            if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    || uri.getHost() == null || uri.getUserInfo() != null
                    || uri.getQuery() != null || uri.getFragment() != null
                    || port == 0 || port > 65_535) {
                return fallback;
            }
            return normalized;
        } catch (URISyntaxException e) {
            return fallback;
        }
    }

    private static String stripTrailingSlashes(String value) {
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == '/') end--;
        return value.substring(0, end);
    }

    private static Map<String, String> defaultEngineUrls() {
        Map<String, String> urls = new LinkedHashMap<>();
        urls.put("voicevox", "http://127.0.0.1:50021");
        urls.put("coeiroink", "http://127.0.0.1:50032");
        urls.put("aivisspeech", "http://127.0.0.1:10101");
        urls.put("sharevox", "http://127.0.0.1:50025");
        urls.put("lmroid", "http://127.0.0.1:49513");
        return Collections.unmodifiableMap(urls);
    }

    private static long mebibytesToBytes(long value) {
        return Math.multiplyExact(value, 1024L * 1024L);
    }

    /**
     * エンジン固有の URL オーバーライドを返す。
     * 未設定の場合は null（プロバイダーが持つデフォルト URL を使用）。
     */
    public String engineUrl(String engineId) {
        return engineUrls.get(engineId);
    }
}
