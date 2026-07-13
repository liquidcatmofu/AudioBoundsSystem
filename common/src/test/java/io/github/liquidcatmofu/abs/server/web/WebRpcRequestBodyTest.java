package io.github.liquidcatmofu.abs.server.web;

import io.github.liquidcatmofu.abs.network.WebRpcProtocol;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebRpcRequestBodyTest {
    @Test
    void reassemblesAndVerifiesAFileBackedBody() throws Exception {
        byte[] body = "file-backed request".getBytes(StandardCharsets.UTF_8);
        WebRpcRequestBody assembly = new WebRpcRequestBody(body.length, 4, 1024);
        try {
            assertFalse(assembly.accept(body.length, 0, slice(body, 0, 5)));
            assertTrue(assembly.accept(body.length, 5, slice(body, 5, body.length)));
            assertTrue(assembly.digestMatches(WebRpcProtocol.sha256(body)));
            try (InputStream input = assembly.openStream()) {
                assertArrayEquals(body, input.readAllBytes());
            }
        } finally {
            assembly.close();
        }
        assertThrows(IOException.class, assembly::openStream);
    }

    @Test
    void rejectsOutOfOrderChunksAndWrongDigests() throws Exception {
        byte[] body = "request".getBytes(StandardCharsets.UTF_8);
        try (WebRpcRequestBody assembly = new WebRpcRequestBody(body.length, 1024, 1024)) {
            assertThrows(IOException.class,
                    () -> assembly.accept(body.length, 1, slice(body, 1, body.length)));
            assertTrue(assembly.accept(body.length, 0, body));
            assertFalse(assembly.digestMatches(WebRpcProtocol.sha256(new byte[0])));
        }
    }

    private static byte[] slice(byte[] bytes, int from, int to) {
        byte[] result = new byte[to - from];
        System.arraycopy(bytes, from, result, 0, result.length);
        return result;
    }
}
