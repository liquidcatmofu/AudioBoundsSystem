package io.github.liquidcatmofu.abs.tts.provider;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VoiceVoxCompatibleProviderTest {
    @Test
    void acceptsAResponseAtTheLimit() throws Exception {
        byte[] response = "12345".getBytes(StandardCharsets.UTF_8);

        assertArrayEquals(response, VoiceVoxCompatibleProvider.readBytes(
                new ByteArrayInputStream(response), response.length, response.length, "test response"));
        assertEquals("12345", VoiceVoxCompatibleProvider.readString(
                new ByteArrayInputStream(response), -1, response.length, "test response"));
    }

    @Test
    void rejectsOversizedDeclaredAndStreamingResponses() {
        assertThrows(IOException.class, () -> VoiceVoxCompatibleProvider.readBytes(
                new ByteArrayInputStream(new byte[0]), 6, 5, "declared response"));
        assertThrows(IOException.class, () -> VoiceVoxCompatibleProvider.readBytes(
                new ByteArrayInputStream(new byte[6]), -1, 5, "streaming response"));
    }
}
