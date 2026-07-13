package io.github.liquidcatmofu.abs.io;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/** 最終アクセス時刻をmtimeとして扱う、ディスクキャッシュ共通の容量整理。 */
public final class DiskCachePruner {
    private DiskCachePruner() {}

    public static void evictOldest(Path directory, String suffix, long maxBytes) throws IOException {
        if (maxBytes < 1) throw new IllegalArgumentException("maxBytes must be positive");
        if (!Files.isDirectory(directory)) return;

        List<CacheFile> files = new ArrayList<>();
        try (Stream<Path> stream = Files.list(directory)) {
            for (Path path : stream.filter(Files::isRegularFile)
                    .filter(candidate -> candidate.getFileName().toString().endsWith(suffix)).toList()) {
                files.add(new CacheFile(path, Files.size(path), Files.getLastModifiedTime(path).toMillis()));
            }
        }
        long total = files.stream().mapToLong(CacheFile::size).sum();
        files.sort(Comparator.comparingLong(CacheFile::lastAccess));
        for (CacheFile file : files) {
            if (total <= maxBytes) break;
            if (Files.deleteIfExists(file.path())) total -= file.size();
        }
    }

    private record CacheFile(Path path, long size, long lastAccess) {}
}
