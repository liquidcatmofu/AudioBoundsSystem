package io.github.liquidcatmofu.abs.server.web;

import io.github.liquidcatmofu.abs.network.ChunkedTransferAssembler;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Reassembles a request in memory or in a temporary file, depending on its declared size. */
final class WebRpcRequestBody implements AutoCloseable {
    private final int totalLength;
    private final ChunkedTransferAssembler memoryAssembler;
    private final Path temporaryFile;
    private final OutputStream fileOutput;
    private final MessageDigest digest;
    private byte[] memoryBody;
    private int nextOffset;
    private boolean complete;

    WebRpcRequestBody(int totalLength, int memoryThreshold, int maxBytes) throws IOException {
        if (totalLength < 1 || totalLength > maxBytes) {
            throw new IOException("Transfer length is out of bounds: " + totalLength);
        }
        this.totalLength = totalLength;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
        if (totalLength <= memoryThreshold) {
            memoryAssembler = new ChunkedTransferAssembler(maxBytes);
            temporaryFile = null;
            fileOutput = null;
        } else {
            memoryAssembler = null;
            temporaryFile = Files.createTempFile("abs-web-rpc-", ".upload");
            fileOutput = Files.newOutputStream(temporaryFile);
        }
    }

    synchronized boolean accept(int declaredLength, int offset, byte[] chunk) throws IOException {
        if (complete) throw new IOException("Transfer is already complete");
        if (declaredLength != totalLength) throw new IOException("Transfer length changed between chunks");
        if (chunk == null || chunk.length == 0) throw new IOException("Transfer chunk must not be empty");
        if (offset != nextOffset || chunk.length > totalLength - offset) {
            throw new IOException("Transfer chunk is out of order or out of bounds");
        }

        if (memoryAssembler != null) {
            memoryBody = memoryAssembler.accept(declaredLength, offset, chunk);
        } else {
            fileOutput.write(chunk);
        }
        digest.update(chunk);
        nextOffset += chunk.length;
        complete = nextOffset == totalLength;
        if (complete && fileOutput != null) fileOutput.close();
        return complete;
    }

    synchronized boolean digestMatches(byte[] expected) {
        if (!complete) return false;
        return MessageDigest.isEqual(digest.digest(), expected);
    }

    synchronized InputStream openStream() throws IOException {
        if (!complete) throw new IOException("Transfer is incomplete");
        return memoryBody != null ? new ByteArrayInputStream(memoryBody) : Files.newInputStream(temporaryFile);
    }

    @Override
    public synchronized void close() throws IOException {
        IOException failure = null;
        if (fileOutput != null) {
            try {
                fileOutput.close();
            } catch (IOException e) {
                failure = e;
            }
        }
        if (temporaryFile != null) {
            try {
                Files.deleteIfExists(temporaryFile);
            } catch (IOException e) {
                if (failure == null) failure = e;
                else failure.addSuppressed(e);
            }
        }
        memoryBody = null;
        if (failure != null) throw failure;
    }
}
