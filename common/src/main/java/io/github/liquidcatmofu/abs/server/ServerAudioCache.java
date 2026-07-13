package io.github.liquidcatmofu.abs.server;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/** World-scoped playable audio cache, independent of any delivery transport. */
public final class ServerAudioCache {
    private static volatile Path directory;

    private ServerAudioCache() {}

    public static void init(Path worldRoot) throws IOException {
        Path initialized = worldRoot.resolve("abs_cache").toAbsolutePath().normalize();
        Files.createDirectories(initialized);
        directory = initialized;
    }

    public static Path getDirectory() {
        Path current = directory;
        if (current == null) throw new IllegalStateException("Server audio cache is not initialized");
        return current;
    }

    /** Resolves metadata paths to direct children of the playable cache root only. */
    public static Optional<Path> resolve(String cacheFile) {
        return resolve(getDirectory(), cacheFile);
    }

    static Optional<Path> resolve(Path root, String cacheFile) {
        if (root == null || cacheFile == null || cacheFile.isBlank()) return Optional.empty();
        try {
            Path normalizedRoot = root.toAbsolutePath().normalize();
            Path resolved = normalizedRoot.resolve(cacheFile).normalize();
            if (!normalizedRoot.equals(resolved.getParent())) return Optional.empty();
            if (Files.exists(resolved)) {
                Path realRoot = normalizedRoot.toRealPath();
                Path realFile = resolved.toRealPath();
                if (!realRoot.equals(realFile.getParent())) return Optional.empty();
            }
            return Optional.of(resolved);
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }
}
