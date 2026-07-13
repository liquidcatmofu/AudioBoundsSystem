package io.github.liquidcatmofu.abs.audio;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.regex.Pattern;

/** Oggコンテンツの最低限の形式検証とSHA-256計算。 */
public final class AudioContent {
    private static final byte[] OGG_MAGIC = {0x4f, 0x67, 0x67, 0x53};
    private static final Pattern SHA_256 = Pattern.compile("^[0-9a-f]{64}$");

    private AudioContent() {}

    public static void requireOgg(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length < OGG_MAGIC.length) {
            throw new IOException("Ogg output is empty or truncated");
        }
        for (int i = 0; i < OGG_MAGIC.length; i++) {
            if (bytes[i] != OGG_MAGIC[i]) {
                throw new IOException("Audio output does not start with an OggS page");
            }
        }
    }

    public static boolean hasOggHeader(Path path) {
        if (!Files.isRegularFile(path)) return false;
        try (InputStream input = Files.newInputStream(path)) {
            byte[] header = input.readNBytes(OGG_MAGIC.length);
            if (header.length != OGG_MAGIC.length) return false;
            for (int i = 0; i < OGG_MAGIC.length; i++) {
                if (header[i] != OGG_MAGIC[i]) return false;
            }
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public static String sha256(byte[] bytes) {
        return toHex(newDigest().digest(bytes));
    }

    public static boolean isSha256(String value) {
        return value != null && SHA_256.matcher(value).matches();
    }

    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private static String toHex(byte[] hash) {
        StringBuilder result = new StringBuilder(hash.length * 2);
        for (byte value : hash) {
            result.append(String.format("%02x", value));
        }
        return result.toString();
    }
}
