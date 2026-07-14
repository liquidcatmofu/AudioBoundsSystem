package io.github.liquidcatmofu.abs.server.web;

import io.github.liquidcatmofu.abs.network.WebRpcProtocol;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class WebRpcServiceMetadataTest {
    private static final byte[] VALID_DIGEST = new byte[WebRpcProtocol.DIGEST_BYTES];

    @Test
    void acceptsSupportedMethodsAndApiPaths() {
        for (String method : new String[]{"GET", "POST", "PATCH", "DELETE"}) {
            assertNull(WebRpcService.validateMetadata(method, "/api", 0, VALID_DIGEST), method);
            assertNull(WebRpcService.validateMetadata(
                    method, "/api/library/items?limit=10", WebRpcProtocol.MAX_BODY_BYTES, VALID_DIGEST), method);
        }
    }

    @Test
    void rejectsUnsupportedMethods() {
        assertEquals("Unsupported request method",
                WebRpcService.validateMetadata("PUT", "/api/library", 0, VALID_DIGEST));
        assertEquals("Unsupported request method",
                WebRpcService.validateMetadata(null, "/api/library", 0, VALID_DIGEST));
    }

    @Test
    void rejectsPathsOutsideApiBoundaryAndRelativeSegments() {
        for (String path : new String[]{
                "/", "/apiary", "https://example.com/api/library", "/api/../admin",
                "/api/%2e%2e/admin", "/api/./library", null}) {
            assertEquals("Invalid API path",
                    WebRpcService.validateMetadata("GET", path, 0, VALID_DIGEST), String.valueOf(path));
        }
    }

    @Test
    void rejectsInvalidBodyLengthsAndDigests() {
        assertEquals("Request body is too large",
                WebRpcService.validateMetadata("POST", "/api/library", -1, VALID_DIGEST));
        assertEquals("Request body is too large", WebRpcService.validateMetadata(
                "POST", "/api/library", WebRpcProtocol.MAX_BODY_BYTES + 1, VALID_DIGEST));
        assertEquals("Invalid request checksum",
                WebRpcService.validateMetadata("POST", "/api/library", 0, null));
        assertEquals("Invalid request checksum",
                WebRpcService.validateMetadata("POST", "/api/library", 0, new byte[31]));
    }
}
