package io.github.liquidcatmofu.abs.audio;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class OggAudioDuration {
    private static final byte[] OGG_CAPTURE_PATTERN = "OggS".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] VORBIS_SIGNATURE = "vorbis".getBytes(StandardCharsets.US_ASCII);

    private OggAudioDuration() {
    }

    public static long readDurationTicks(Path path) throws IOException {
        byte[] data = Files.readAllBytes(path);
        AudioInfo info = readInfo(data);
        if (info.sampleRate <= 0 || info.lastGranulePosition < 0) {
            throw new IOException("Could not determine Ogg Vorbis duration: " + path);
        }

        double seconds = (double) info.lastGranulePosition / (double) info.sampleRate;
        return Math.max(1L, (long) Math.ceil(seconds * 20.0D));
    }

    private static AudioInfo readInfo(byte[] data) throws IOException {
        int offset = 0;
        int sampleRate = -1;
        long lastGranulePosition = -1L;
        ByteArrayOutputStream packet = new ByteArrayOutputStream();

        while (offset + 27 <= data.length) {
            if (!matches(data, offset, OGG_CAPTURE_PATTERN)) {
                offset++;
                continue;
            }

            int pageSegments = unsignedByte(data[offset + 26]);
            int segmentTableOffset = offset + 27;
            int dataOffset = segmentTableOffset + pageSegments;
            if (dataOffset > data.length) {
                throw new IOException("Truncated Ogg page header");
            }

            int pageDataLength = 0;
            for (int i = 0; i < pageSegments; i++) {
                pageDataLength += unsignedByte(data[segmentTableOffset + i]);
            }
            if (dataOffset + pageDataLength > data.length) {
                throw new IOException("Truncated Ogg page data");
            }

            long granulePosition = readLittleEndianLong(data, offset + 6);
            if (granulePosition >= 0) {
                lastGranulePosition = granulePosition;
            }

            int packetOffset = dataOffset;
            for (int i = 0; i < pageSegments; i++) {
                int segmentLength = unsignedByte(data[segmentTableOffset + i]);
                packet.write(data, packetOffset, segmentLength);
                packetOffset += segmentLength;

                if (segmentLength < 255) {
                    if (sampleRate < 0) {
                        sampleRate = readVorbisSampleRate(packet.toByteArray());
                    }
                    packet.reset();
                }
            }

            offset = dataOffset + pageDataLength;
        }

        return new AudioInfo(sampleRate, lastGranulePosition);
    }

    private static int readVorbisSampleRate(byte[] packet) throws IOException {
        if (packet.length < 16 || packet[0] != 1 || !matches(packet, 1, VORBIS_SIGNATURE)) {
            return -1;
        }

        int sampleRate = readLittleEndianInt(packet, 12);
        if (sampleRate <= 0) {
            throw new IOException("Invalid Vorbis sample rate: " + sampleRate);
        }
        return sampleRate;
    }

    private static boolean matches(byte[] data, int offset, byte[] expected) {
        if (offset < 0 || offset + expected.length > data.length) {
            return false;
        }

        for (int i = 0; i < expected.length; i++) {
            if (data[offset + i] != expected[i]) {
                return false;
            }
        }
        return true;
    }

    private static int readLittleEndianInt(byte[] data, int offset) {
        return unsignedByte(data[offset])
                | (unsignedByte(data[offset + 1]) << 8)
                | (unsignedByte(data[offset + 2]) << 16)
                | (unsignedByte(data[offset + 3]) << 24);
    }

    private static long readLittleEndianLong(byte[] data, int offset) {
        return (long) unsignedByte(data[offset])
                | ((long) unsignedByte(data[offset + 1]) << 8)
                | ((long) unsignedByte(data[offset + 2]) << 16)
                | ((long) unsignedByte(data[offset + 3]) << 24)
                | ((long) unsignedByte(data[offset + 4]) << 32)
                | ((long) unsignedByte(data[offset + 5]) << 40)
                | ((long) unsignedByte(data[offset + 6]) << 48)
                | ((long) unsignedByte(data[offset + 7]) << 56);
    }

    private static int unsignedByte(byte value) {
        return value & 0xFF;
    }

    private record AudioInfo(int sampleRate, long lastGranulePosition) {
    }
}
