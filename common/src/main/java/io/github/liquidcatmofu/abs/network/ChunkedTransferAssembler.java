package io.github.liquidcatmofu.abs.network;

import java.io.IOException;
import java.util.Arrays;

/** Reassembles one bounded, ordered series of transfer chunks. */
public final class ChunkedTransferAssembler {
    private final int maxBytes;
    private byte[] bytes;
    private int nextOffset;

    public ChunkedTransferAssembler(int maxBytes) {
        if (maxBytes < 1) throw new IllegalArgumentException("maxBytes must be positive");
        this.maxBytes = maxBytes;
    }

    public synchronized byte[] accept(int totalLength, int offset, byte[] chunk) throws IOException {
        if (totalLength < 1 || totalLength > maxBytes) {
            throw new IOException("Transfer length is out of bounds: " + totalLength);
        }
        if (chunk == null || chunk.length == 0) {
            throw new IOException("Transfer chunk must not be empty");
        }
        if (bytes == null) {
            if (offset != 0) throw new IOException("Transfer must start at offset 0");
            bytes = new byte[totalLength];
        } else if (bytes.length != totalLength) {
            throw new IOException("Transfer length changed between chunks");
        }
        if (offset != nextOffset || chunk.length > bytes.length - offset) {
            throw new IOException("Transfer chunk is out of order or out of bounds");
        }

        System.arraycopy(chunk, 0, bytes, offset, chunk.length);
        nextOffset += chunk.length;
        return nextOffset == bytes.length ? Arrays.copyOf(bytes, bytes.length) : null;
    }
}
