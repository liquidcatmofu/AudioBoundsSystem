package io.github.liquidcatmofu.abs.server;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ABSHttpServerTest {
    @TempDir
    Path tempDir;

    @Test
    void resolvesOnlyPathsInsideCacheRoot() {
        assertEquals(tempDir.resolve("track.ogg").toAbsolutePath().normalize(),
                ABSHttpServer.resolveCacheFile(tempDir, "track.ogg").orElseThrow());
        assertTrue(ABSHttpServer.resolveCacheFile(tempDir, "../secret.ogg").isEmpty());
        assertTrue(ABSHttpServer.resolveCacheFile(tempDir,
                tempDir.resolveSibling("outside.ogg").toString()).isEmpty());
        assertTrue(ABSHttpServer.resolveCacheFile(tempDir, "internal/tts.ogg").isEmpty());
    }
}
