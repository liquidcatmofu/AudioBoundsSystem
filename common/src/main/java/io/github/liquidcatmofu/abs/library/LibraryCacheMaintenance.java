package io.github.liquidcatmofu.abs.library;

import io.github.liquidcatmofu.abs.AudioBoundsSystem;
import io.github.liquidcatmofu.abs.server.ABSHttpServer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/** abs_cache の孤立ファイルをバックグラウンドで整理する。 */
public final class LibraryCacheMaintenance {
    private static ExecutorService executor;

    private LibraryCacheMaintenance() {}

    public static synchronized void start() {
        if (executor != null) return;
        Path cacheRoot = ABSHttpServer.getCacheDir();
        if (ABSLibrary.getRoot() == null || cacheRoot == null) return;

        long scanStartedAt = System.currentTimeMillis();
        executor = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "abs-library-maintenance");
            thread.setDaemon(true);
            return thread;
        });
        executor.submit(() -> runOnce(cacheRoot, scanStartedAt));
    }

    public static synchronized void stop() {
        ExecutorService running = executor;
        executor = null;
        if (running == null) return;
        running.shutdown();
        try {
            if (!running.awaitTermination(5, TimeUnit.SECONDS)) running.shutdownNow();
        } catch (InterruptedException e) {
            running.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private static void runOnce(Path cacheRoot, long scanStartedAt) {
        try {
            if (Thread.currentThread().isInterrupted()) return;
            Set<Path> referenced = collectReferencedCacheFiles();
            if (Thread.currentThread().isInterrupted()) return;
            int removed = removeOrphanRootOgg(cacheRoot, referenced, scanStartedAt);
            AudioBoundsSystem.LOGGER.info("ABS: library cache maintenance completed (removed {} orphans)", removed);
        } catch (RuntimeException | IOException e) {
            AudioBoundsSystem.LOGGER.error("ABS: library cache maintenance failed", e);
        }
    }

    private static Set<Path> collectReferencedCacheFiles() {
        Set<Path> referenced = new HashSet<>();
        for (LibraryFolder folder : ABSLibrary.loadAll()) {
            for (AudioEntry entry : LibraryAudio.list(folder.id)) {
                LibraryAudio.cacheFilePath(entry).map(LibraryCacheMaintenance::normalize).ifPresent(referenced::add);
            }
            for (TtsEntry entry : LibraryTts.list(folder.id)) {
                LibraryTts.cacheFilePath(entry).map(LibraryCacheMaintenance::normalize).ifPresent(referenced::add);
            }
        }
        return referenced;
    }

    static int removeOrphanRootOgg(Path cacheRoot, Set<Path> referenced, long scanStartedAt) throws IOException {
        if (!Files.isDirectory(cacheRoot)) return 0;
        int removed = 0;
        try (Stream<Path> files = Files.list(cacheRoot)) {
            for (Path file : files.toList()) {
                if (!Files.isRegularFile(file)
                        || !file.getFileName().toString().endsWith(".ogg")
                        || referenced.contains(normalize(file))) {
                    continue;
                }
                FileTime modified = Files.getLastModifiedTime(file);
                if (modified.toMillis() >= scanStartedAt) continue;
                if (Files.deleteIfExists(file)) removed++;
            }
        }
        return removed;
    }

    private static Path normalize(Path path) {
        return path.toAbsolutePath().normalize();
    }
}
