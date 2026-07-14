package io.github.liquidcatmofu.abs.io;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

/** 同一ディレクトリの一時ファイルを書き切ってから対象パスへ確定する。 */
public final class AtomicFiles {
    private AtomicFiles() {}

    public static void write(Path target, byte[] content) throws IOException {
        write(target, new java.io.ByteArrayInputStream(content), content.length);
    }

    public static long write(Path target, InputStream content, long maxBytes) throws IOException {
        if (maxBytes < 0) throw new IllegalArgumentException("maxBytes must not be negative");
        Path parent = target.toAbsolutePath().getParent();
        if (parent == null) {
            throw new IOException("Target has no parent directory: " + target);
        }
        Files.createDirectories(parent);
        Path temporary = parent.resolve("." + target.getFileName() + "." + UUID.randomUUID() + ".tmp");
        long written = 0;
        try {
            try (FileChannel channel = FileChannel.open(temporary,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                byte[] chunk = new byte[32 * 1024];
                int read;
                while ((read = content.read(chunk)) != -1) {
                    if (written + read > maxBytes) throw new SizeLimitExceededException(maxBytes);
                    ByteBuffer buffer = ByteBuffer.wrap(chunk, 0, read);
                    while (buffer.hasRemaining()) channel.write(buffer);
                    written += read;
                }
                channel.force(true);
            }
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
        return written;
    }

    public static void writeString(Path target, String content, Charset charset) throws IOException {
        write(target, content.getBytes(charset));
    }

    public static final class SizeLimitExceededException extends IOException {
        public SizeLimitExceededException(long maxBytes) {
            super("Content exceeds " + maxBytes + " bytes");
        }
    }
}
