package io.github.liquidcatmofu.abs.server.web;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RequestBodyReaderTest {
    @Test
    void acceptsABodyAtTheLimit() throws Exception {
        byte[] body = "12345".getBytes(StandardCharsets.UTF_8);

        assertArrayEquals(body, RequestBodyReader.readBytes(
                new ByteArrayInputStream(body), "5", 5));
    }

    @Test
    void rejectsAnOversizedDeclaredLengthBeforeReading() {
        assertThrows(RequestBodyReader.PayloadTooLargeException.class,
                () -> RequestBodyReader.readBytes(
                        new ByteArrayInputStream(new byte[0]), "6", 5));
    }

    @Test
    void rejectsAnOversizedChunkedBodyWhileReading() {
        assertThrows(RequestBodyReader.PayloadTooLargeException.class,
                () -> RequestBodyReader.readBytes(
                        new ByteArrayInputStream(new byte[6]), null, 5));
    }
}
