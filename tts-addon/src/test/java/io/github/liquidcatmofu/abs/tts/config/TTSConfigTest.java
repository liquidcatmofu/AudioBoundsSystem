package io.github.liquidcatmofu.abs.tts.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TTSConfigTest {
    @TempDir
    Path tempDir;

    @Test
    void createsDefaultsAndLoadsConfiguredCacheCapacity() throws Exception {
        Path path = tempDir.resolve("config/abs-tts.toml");
        TTSConfig defaults = TTSConfig.load(path);

        assertTrue(Files.isRegularFile(path));
        assertEquals(128L * 1024L * 1024L, defaults.cacheMaxBytes());
        assertEquals("http://127.0.0.1:50021", defaults.engineUrl("voicevox"));

        Files.writeString(path, """
                [ffmpeg]
                path = "ffmpeg-custom"
                [cache]
                maxSizeMiB = 32
                [engines]
                voicevox = "http://localhost:51021/api/"
                """);
        TTSConfig configured = TTSConfig.load(path);

        assertEquals("ffmpeg-custom", configured.ffmpegPath());
        assertEquals(32L * 1024L * 1024L, configured.cacheMaxBytes());
        assertEquals("http://localhost:51021/api", configured.engineUrl("voicevox"));
        assertEquals("http://127.0.0.1:50032", configured.engineUrl("coeiroink"));
    }

    @Test
    void replacesOutOfRangeCapacityWithTheDefault() throws Exception {
        Path path = tempDir.resolve("abs-tts.toml");
        Files.writeString(path, "[cache]\nmaxSizeMiB = 0\n");

        assertEquals(TTSConfig.DEFAULT.cacheMaxBytes(), TTSConfig.load(path).cacheMaxBytes());
        assertTrue(Files.readString(path).contains("maxSizeMiB = 128"));
    }

    @Test
    void replacesUnsafeOrMalformedEngineUrlsWithDefaults() throws Exception {
        Path path = tempDir.resolve("abs-tts.toml");
        Files.writeString(path, """
                [engines]
                voicevox = "file:///tmp/not-http"
                coeiroink = "http://user:secret@localhost:50032"
                aivisspeech = "http://localhost:99999"
                sharevox = "not a url"
                lmroid = "https://example.test:443/base?query=unsafe"
                """);

        TTSConfig config = TTSConfig.load(path);

        assertEquals(TTSConfig.DEFAULT_ENGINE_URLS, config.engineUrls());
        String persisted = Files.readString(path);
        assertTrue(persisted.contains("voicevox = \"http://127.0.0.1:50021\""));
        assertTrue(persisted.contains("lmroid = \"http://127.0.0.1:49513\""));
    }
}
