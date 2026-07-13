package io.github.liquidcatmofu.abs.client.audio;

import io.github.liquidcatmofu.abs.audio.AudioContent;
import io.github.liquidcatmofu.abs.io.AtomicFiles;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/** SHA-256をキーにした、アクセス時刻ベースのクライアントOggディスクキャッシュ。 */
public final class ClientAudioCache {
    public static final long DEFAULT_MAX_BYTES = 128L * 1024L * 1024L;
    private static final int MAX_SINGLE_FILE_BYTES = 64 * 1024 * 1024;

    private final Path directory;
    private final long maxBytes;

    public ClientAudioCache(Path directory) throws IOException {
        this(directory, DEFAULT_MAX_BYTES);
    }

    ClientAudioCache(Path directory, long maxBytes) throws IOException {
        if (maxBytes < 1) throw new IllegalArgumentException("maxBytes must be positive");
        this.directory = directory;
        this.maxBytes = maxBytes;
        Files.createDirectories(directory);
        evictIfNeeded();
    }

    public synchronized Optional<byte[]> get(String contentHash) {
        if (!isValidHash(contentHash)) return Optional.empty();
        Path path = pathFor(contentHash);
        if (!Files.isRegularFile(path)) return Optional.empty();
        try {
            long size = Files.size(path);
            if (size < 4 || size > MAX_SINGLE_FILE_BYTES) {
                Files.deleteIfExists(path);
                return Optional.empty();
            }
            byte[] bytes = Files.readAllBytes(path);
            AudioContent.requireOgg(bytes);
            if (!contentHash.equals(AudioContent.sha256(bytes))) {
                Files.deleteIfExists(path);
                return Optional.empty();
            }
            Files.setLastModifiedTime(path, FileTime.fromMillis(System.currentTimeMillis()));
            return Optional.of(bytes);
        } catch (IOException e) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException ignored) {}
            return Optional.empty();
        }
    }

    public synchronized void put(String contentHash, byte[] bytes) throws IOException {
        if (!isValidHash(contentHash)) {
            throw new IOException("Invalid audio content hash");
        }
        if (bytes.length > MAX_SINGLE_FILE_BYTES) {
            throw new IOException("Audio exceeds client cache file limit");
        }
        AudioContent.requireOgg(bytes);
        if (!contentHash.equals(AudioContent.sha256(bytes))) {
            throw new IOException("Downloaded audio hash does not match the server metadata");
        }
        Path path = pathFor(contentHash);
        AtomicFiles.write(path, bytes);
        Files.setLastModifiedTime(path, FileTime.fromMillis(System.currentTimeMillis()));
        evictIfNeeded();
    }

    synchronized void evictIfNeeded() throws IOException {
        List<CacheFile> files = new ArrayList<>();
        try (Stream<Path> stream = Files.list(directory)) {
            for (Path path : stream.filter(Files::isRegularFile)
                    .filter(candidate -> candidate.getFileName().toString().endsWith(".ogg")).toList()) {
                files.add(new CacheFile(path, Files.size(path), Files.getLastModifiedTime(path).toMillis()));
            }
        }
        long total = files.stream().mapToLong(CacheFile::size).sum();
        files.sort(Comparator.comparingLong(CacheFile::lastAccess));
        for (CacheFile file : files) {
            if (total <= maxBytes) break;
            if (Files.deleteIfExists(file.path())) {
                total -= file.size();
            }
        }
    }

    public static boolean isValidHash(String contentHash) {
        return AudioContent.isSha256(contentHash);
    }

    private Path pathFor(String contentHash) {
        return directory.resolve(contentHash + ".ogg");
    }

    private record CacheFile(Path path, long size, long lastAccess) {}
}
