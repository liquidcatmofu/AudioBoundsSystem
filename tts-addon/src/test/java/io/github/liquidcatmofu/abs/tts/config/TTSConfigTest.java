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

        Files.writeString(path, """
                [ffmpeg]
                path = "ffmpeg-custom"
                [cache]
                maxSizeMiB = 32
                """);
        TTSConfig configured = TTSConfig.load(path);

        assertEquals("ffmpeg-custom", configured.ffmpegPath());
        assertEquals(32L * 1024L * 1024L, configured.cacheMaxBytes());
    }

    @Test
    void replacesOutOfRangeCapacityWithTheDefault() throws Exception {
        Path path = tempDir.resolve("abs-tts.toml");
        Files.writeString(path, "[cache]\nmaxSizeMiB = 0\n");

        assertEquals(TTSConfig.DEFAULT.cacheMaxBytes(), TTSConfig.load(path).cacheMaxBytes());
        assertTrue(Files.readString(path).contains("maxSizeMiB = 128"));
    }
}
