package io.github.liquidcatmofu.abs.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    @Test
    void streamsWithinTheLimitWithoutLeavingAHeapSizedCopy() throws Exception {
        Path target = tempDir.resolve("upload.wav");
        byte[] content = "streamed audio".getBytes(StandardCharsets.UTF_8);

        long written = AtomicFiles.write(target, new ByteArrayInputStream(content), content.length);

        assertEquals(content.length, written);
        assertEquals("streamed audio", Files.readString(target));
    }

    @Test
    void rejectsAnOversizedStreamWithoutReplacingTheTarget() throws Exception {
        Path target = tempDir.resolve("upload.wav");
        Files.writeString(target, "existing");

        assertThrows(AtomicFiles.SizeLimitExceededException.class, () -> AtomicFiles.write(
                target, new ByteArrayInputStream(new byte[9]), 8));

        assertEquals("existing", Files.readString(target));
        try (var files = Files.list(tempDir)) {
            assertTrue(files.noneMatch(path -> path.getFileName().toString().endsWith(".tmp")));
        }
    }
}
