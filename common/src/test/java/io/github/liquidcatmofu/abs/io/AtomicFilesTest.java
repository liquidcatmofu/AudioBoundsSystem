package io.github.liquidcatmofu.abs.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AtomicFilesTest {
    @TempDir Path tempDir;

    @Test
    void replacesAFileAndLeavesNoTemporaryFile() throws Exception {
        Path target = tempDir.resolve("metadata.json");
        AtomicFiles.writeString(target, "old", StandardCharsets.UTF_8);
        AtomicFiles.writeString(target, "new", StandardCharsets.UTF_8);

        assertEquals("new", Files.readString(target));
        try (var files = Files.list(tempDir)) {
            assertTrue(files.noneMatch(path -> path.getFileName().toString().endsWith(".tmp")));
        }
    }
}
