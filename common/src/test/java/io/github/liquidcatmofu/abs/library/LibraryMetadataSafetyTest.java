package io.github.liquidcatmofu.abs.library;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LibraryMetadataSafetyTest {
    @TempDir
    Path tempDir;

    @BeforeEach
    void initializeLibrary() {
        ABSLibrary.init(tempDir);
    }

    @Test
    void malformedEntryJsonIsTreatedAsMissing() throws IOException {
        Path folder = ABSLibrary.getRoot().resolve("music");
        writeMalformed(folder.resolve("audio/broken.json"));
        writeMalformed(folder.resolve("tts/broken.json"));
        writeMalformed(folder.resolve("sequences/broken.json"));
        writeMalformed(ABSLibrary.getRoot().resolve("broken/meta.json"));

        assertTrue(LibraryAudio.load("music", "broken").isEmpty());
        assertTrue(LibraryTts.load("music", "broken").isEmpty());
        assertTrue(LibrarySequence.load("music", "broken").isEmpty());
        assertTrue(ABSLibrary.loadFolder("broken").isEmpty());
    }

    @Test
    void folderSaveRejectsUnsafeIds() {
        LibraryFolder unsafe = new LibraryFolder();
        unsafe.id = "../../outside";

        assertThrows(IOException.class, () -> ABSLibrary.saveFolder(unsafe));
        assertThrows(IOException.class, () -> ABSLibrary.saveFolder(null));
    }

    private static void writeMalformed(Path path) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, "{not-json");
    }
}
