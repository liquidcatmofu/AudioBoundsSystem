package io.github.liquidcatmofu.abs.audio;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AudioContentTest {
    @Test
    void acceptsOggPageMagicAndComputesStableHash() throws Exception {
        byte[] ogg = "OggS-test".getBytes(StandardCharsets.UTF_8);
        AudioContent.requireOgg(ogg);

        assertEquals("c37a3bc5201f0d5b5573b3dee39808f75cf4eb2a70e455ebe019839ee8ca8d95",
                AudioContent.sha256(ogg));
    }

    @Test
    void rejectsEmptyAndNonOggOutput() {
        assertThrows(IOException.class, () -> AudioContent.requireOgg(new byte[0]));
        assertThrows(IOException.class,
                () -> AudioContent.requireOgg("RIFF-test".getBytes(StandardCharsets.UTF_8)));
    }
}
