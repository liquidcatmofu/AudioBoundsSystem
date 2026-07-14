package io.github.liquidcatmofu.abs.library;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LibrarySequenceTest {
    @TempDir
    Path tempDir;

    @BeforeEach
    void initializeLibrary() {
        ABSLibrary.init(tempDir);
    }

    @Test
    void createsLoadsUpdatesListsAndDeletesSequenceMetadata() throws IOException {
        SequenceStep intro = step("lib:music/audio/intro", 5);
        SequenceEntry created = LibrarySequence.create(
                "music", "Show", List.of(intro), UUID.fromString("12345678-1234-5678-1234-567812345678"));

        SequenceEntry loaded = LibrarySequence.load("music", created.id).orElseThrow();
        assertEquals("Show", loaded.displayName);
        assertEquals("lib:music/audio/intro", loaded.steps.get(0).audioRef);
        assertEquals(5, loaded.steps.get(0).delayTicks);
        assertEquals(List.of(created.id), LibrarySequence.list("music").stream().map(entry -> entry.id).toList());

        SequenceStep finale = step("lib:music/audio/finale", 10);
        SequenceEntry updated = LibrarySequence.update("music", created.id, "Updated Show", List.of(finale));
        assertEquals("Updated Show", updated.displayName);
        assertEquals("lib:music/audio/finale",
                LibrarySequence.load("music", created.id).orElseThrow().steps.get(0).audioRef);

        assertTrue(LibrarySequence.delete("music", created.id));
        assertFalse(LibrarySequence.load("music", created.id).isPresent());
        assertFalse(LibrarySequence.delete("music", created.id));
    }

    @Test
    void rejectsUnsafeFolderAndSequenceIds() {
        assertTrue(LibrarySequence.list("..").isEmpty());
        assertTrue(LibrarySequence.load("music", "../../outside").isEmpty());
        assertFalse(assertDoesNotThrowDelete("music", "../../outside"));
        assertThrows(IOException.class,
                () -> LibrarySequence.create("../outside", "Unsafe", List.of(), UUID.randomUUID()));
        assertThrows(IOException.class,
                () -> LibrarySequence.update("music", "../../outside", "Unsafe", List.of()));
    }

    private static boolean assertDoesNotThrowDelete(String folderId, String sequenceId) {
        try {
            return LibrarySequence.delete(folderId, sequenceId);
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    private static SequenceStep step(String audioRef, int delayTicks) {
        SequenceStep step = new SequenceStep();
        step.audioRef = audioRef;
        step.delayTicks = delayTicks;
        return step;
    }
}
