package io.github.liquidcatmofu.abs.server;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerAudioCacheTest {
    @TempDir
    Path tempDir;

    @Test
    void resolvesOnlyPathsInsideCacheRoot() {
        assertEquals(tempDir.resolve("track.ogg").toAbsolutePath().normalize(),
                ServerAudioCache.resolve(tempDir, "track.ogg").orElseThrow());
        assertTrue(ServerAudioCache.resolve(tempDir, "../secret.ogg").isEmpty());
        assertTrue(ServerAudioCache.resolve(tempDir,
                tempDir.resolveSibling("outside.ogg").toString()).isEmpty());
        assertTrue(ServerAudioCache.resolve(tempDir, "internal/tts.ogg").isEmpty());
    }
}
