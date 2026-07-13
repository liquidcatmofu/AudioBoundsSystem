package io.github.liquidcatmofu.abs.network;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebRpcProtocolTest {
    @Test
    void chunksPayloadsAndVerifiesTheirDigest() {
        byte[] body = new byte[WebRpcProtocol.MAX_CHUNK_BYTES + 3];
        body[body.length - 1] = 42;

        assertEqualsLength(WebRpcProtocol.MAX_CHUNK_BYTES, WebRpcProtocol.chunk(body, 0));
        assertArrayEquals(new byte[]{0, 0, 42},
                WebRpcProtocol.chunk(body, WebRpcProtocol.MAX_CHUNK_BYTES));

        byte[] digest = WebRpcProtocol.sha256(body);
        assertTrue(WebRpcProtocol.digestMatches(body, digest));
        assertFalse(WebRpcProtocol.digestMatches("other".getBytes(StandardCharsets.UTF_8), digest));
    }

    private static void assertEqualsLength(int expected, byte[] actual) {
        org.junit.jupiter.api.Assertions.assertEquals(expected, actual.length);
    }
}
