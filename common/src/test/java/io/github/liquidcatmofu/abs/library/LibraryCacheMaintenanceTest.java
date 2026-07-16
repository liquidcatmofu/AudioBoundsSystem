package io.github.liquidcatmofu.abs.library;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.io.TempDir;
import io.github.liquidcatmofu.abs.server.ServerAudioCache;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LibraryCacheMaintenanceTest {
    @TempDir
    Path tempDir;

    @AfterEach
    void stopMaintenance() {
        LibraryCacheMaintenance.stop();
    }

    @Test
    void removesOnlyOldUnreferencedRootOggFiles() throws Exception {
        Path referenced = Files.write(tempDir.resolve("referenced.ogg"), new byte[] {1});
        Path orphan = Files.write(tempDir.resolve("orphan.ogg"), new byte[] {2});
        Path recent = Files.write(tempDir.resolve("recent.ogg"), new byte[] {3});
        Path nested = Files.createDirectories(tempDir.resolve("tts-command-cache")).resolve("nested.ogg");
        Files.write(nested, new byte[] {4});

        long cutoff = System.currentTimeMillis();
        FileTime old = FileTime.fromMillis(cutoff - 1_000);
        Files.setLastModifiedTime(referenced, old);
        Files.setLastModifiedTime(orphan, old);
        Files.setLastModifiedTime(recent, FileTime.fromMillis(cutoff));

        int removed = LibraryCacheMaintenance.removeOrphanRootOgg(
                tempDir, Set.of(referenced.toAbsolutePath().normalize()), cutoff);

        assertEquals(1, removed);
        assertTrue(Files.exists(referenced));
        assertFalse(Files.exists(orphan));
        assertTrue(Files.exists(recent));
        assertTrue(Files.exists(nested));
    }

    @Test
    void startAndStopAreIdempotentAndRestartable() throws Exception {
        ABSLibrary.init(tempDir);
        ServerAudioCache.init(tempDir);

        LibraryCacheMaintenance.start();
        LibraryCacheMaintenance.start();
        assertTrue(LibraryCacheMaintenance.isRunning());

        LibraryCacheMaintenance.stop();
        LibraryCacheMaintenance.stop();
        assertFalse(LibraryCacheMaintenance.isRunning());

        LibraryCacheMaintenance.start();
        assertTrue(LibraryCacheMaintenance.isRunning());
    }
}
