package io.github.liquidcatmofu.abs.network;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/** Shared bounds and integrity helpers for browser-to-server RPC payloads. */
public final class WebRpcProtocol {
    public static final int MAX_BODY_BYTES = 64 * 1024 * 1024;
    public static final int MAX_CHUNK_BYTES = 30 * 1024;
    public static final int DIGEST_BYTES = 32;

    private WebRpcProtocol() {}

    public static byte[] sha256(byte[] bytes) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    public static boolean digestMatches(byte[] bytes, byte[] expected) {
        return expected != null && expected.length == DIGEST_BYTES
                && MessageDigest.isEqual(sha256(bytes), expected);
    }

    public static byte[] chunk(byte[] bytes, int offset) {
        if (offset < 0 || offset >= bytes.length) throw new IllegalArgumentException("Invalid chunk offset");
        return Arrays.copyOfRange(bytes, offset, Math.min(bytes.length, offset + MAX_CHUNK_BYTES));
    }
}
