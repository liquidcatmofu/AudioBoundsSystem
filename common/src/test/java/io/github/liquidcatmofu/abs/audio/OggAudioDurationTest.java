package io.github.liquidcatmofu.abs.audio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OggAudioDurationTest {
    @TempDir
    Path tempDir;

    @Test
    void convertsLastGranulePositionToTicks() throws IOException {
        Path ogg = writePage(48_000, 72_000);

        assertEquals(30, OggAudioDuration.readDurationTicks(ogg));
    }

    @Test
    void roundsShortPositiveDurationsUpToOneTick() throws IOException {
        Path ogg = writePage(48_000, 1);

        assertEquals(1, OggAudioDuration.readDurationTicks(ogg));
    }

    @Test
    void rejectsIdentificationPacketWithInvalidSampleRate() throws IOException {
        Path ogg = writePage(0, 48_000);

        assertThrows(IOException.class, () -> OggAudioDuration.readDurationTicks(ogg));
    }

    private Path writePage(int sampleRate, long granulePosition) throws IOException {
        byte[] packet = new byte[16];
        packet[0] = 1;
        byte[] signature = "vorbis".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        System.arraycopy(signature, 0, packet, 1, signature.length);
        writeLittleEndianInt(packet, 12, sampleRate);

        byte[] page = new byte[28 + packet.length];
        page[0] = 'O';
        page[1] = 'g';
        page[2] = 'g';
        page[3] = 'S';
        writeLittleEndianLong(page, 6, granulePosition);
        page[26] = 1;
        page[27] = (byte) packet.length;
        System.arraycopy(packet, 0, page, 28, packet.length);

        Path path = tempDir.resolve("fixture.ogg");
        Files.write(path, page);
        return path;
    }

    private static void writeLittleEndianInt(byte[] data, int offset, int value) {
        for (int i = 0; i < Integer.BYTES; i++) data[offset + i] = (byte) (value >>> (i * 8));
    }

    private static void writeLittleEndianLong(byte[] data, int offset, long value) {
        for (int i = 0; i < Long.BYTES; i++) data[offset + i] = (byte) (value >>> (i * 8));
    }
}
