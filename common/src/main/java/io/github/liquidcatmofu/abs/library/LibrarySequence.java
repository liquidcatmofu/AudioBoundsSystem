package io.github.liquidcatmofu.abs.library;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.liquidcatmofu.abs.AudioBoundsSystem;
import io.github.liquidcatmofu.abs.io.AtomicFiles;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

public final class LibrarySequence {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private LibrarySequence() {}

    private static Path seqDir(String folderId) {
        return ABSLibrary.getRoot().resolve(folderId).resolve("sequences");
    }

    public static List<SequenceEntry> list(String folderId) {
        List<SequenceEntry> result = new ArrayList<>();
        if (!ABSLibrary.isSafeId(folderId) || ABSLibrary.getRoot() == null) return result;
        Path dir = seqDir(folderId);
        if (!Files.isDirectory(dir)) return result;
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(p -> p.getFileName().toString().endsWith(".json")).forEach(p -> {
                try {
                    result.add(GSON.fromJson(Files.readString(p, StandardCharsets.UTF_8), SequenceEntry.class));
                } catch (IOException | RuntimeException e) {
                    AudioBoundsSystem.LOGGER.error("ABS: failed to read sequence metadata {}", p, e);
                }
            });
        } catch (IOException | RuntimeException e) {
            AudioBoundsSystem.LOGGER.error("ABS: failed to list sequences in {}", folderId, e);
        }
        result.sort((a, b) -> Long.compare(b.createdAt, a.createdAt));
        return result;
    }

    public static Optional<SequenceEntry> load(String folderId, String id) {
        if (!ABSLibrary.isSafeId(folderId) || !ABSLibrary.isSafeId(id) || ABSLibrary.getRoot() == null) {
            return Optional.empty();
        }
        Path meta = seqDir(folderId).resolve(id + ".json");
        if (!Files.exists(meta)) return Optional.empty();
        try {
            return Optional.ofNullable(GSON.fromJson(Files.readString(meta, StandardCharsets.UTF_8), SequenceEntry.class));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    public static SequenceEntry create(String folderId, String displayName,
                                       List<SequenceStep> steps, UUID creator) throws IOException {
        requireSafeId(folderId, "folder");
        Path dir = seqDir(folderId);
        Files.createDirectories(dir);

        SequenceEntry entry = new SequenceEntry();
        entry.id          = UUID.randomUUID().toString();
        entry.displayName = (displayName == null || displayName.isBlank()) ? "新規シーケンス" : displayName;
        entry.steps       = steps != null ? steps : new ArrayList<>();
        entry.createdBy   = creator.toString();
        entry.createdAt   = System.currentTimeMillis();
        entry.updatedAt   = entry.createdAt;

        AtomicFiles.writeString(dir.resolve(entry.id + ".json"), GSON.toJson(entry), StandardCharsets.UTF_8);
        return entry;
    }

    public static SequenceEntry update(String folderId, String id,
                                       String displayName, List<SequenceStep> steps) throws IOException {
        requireSafeId(folderId, "folder");
        requireSafeId(id, "sequence");
        SequenceEntry entry = load(folderId, id)
                .orElseThrow(() -> new IOException("Sequence not found: " + id));

        if (displayName != null && !displayName.isBlank()) entry.displayName = displayName;
        if (steps != null) entry.steps = steps;
        entry.updatedAt = System.currentTimeMillis();

        AtomicFiles.writeString(seqDir(folderId).resolve(id + ".json"), GSON.toJson(entry), StandardCharsets.UTF_8);
        return entry;
    }

    public static boolean delete(String folderId, String id) throws IOException {
        if (!ABSLibrary.isSafeId(folderId) || !ABSLibrary.isSafeId(id) || ABSLibrary.getRoot() == null) {
            return false;
        }
        Path meta = seqDir(folderId).resolve(id + ".json");
        if (!Files.exists(meta)) return false;
        Files.delete(meta);
        return true;
    }

    private static void requireSafeId(String id, String kind) throws IOException {
        if (!ABSLibrary.isSafeId(id) || ABSLibrary.getRoot() == null) {
            throw new IOException("Invalid " + kind + " id");
        }
    }
}
