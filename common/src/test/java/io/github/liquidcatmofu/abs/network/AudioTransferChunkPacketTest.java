package io.github.liquidcatmofu.abs.network;

import io.github.liquidcatmofu.abs.server.AudioTransferService;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AudioTransferChunkPacketTest {
    @Test
    void roundTripsMaximumSizedChunk() {
        byte[] chunk = new byte[AudioTransferService.MAX_CHUNK_BYTES];
        Arrays.fill(chunk, (byte) 0x5a);
        AudioTransferChunkPacket expected = new AudioTransferChunkPacket(UUID.randomUUID(),
                chunk.length + 17, 17, chunk);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());

        expected.write(buf);
        AudioTransferChunkPacket actual = AudioTransferChunkPacket.read(buf);

        assertEquals(expected.token(), actual.token());
        assertEquals(expected.totalLength(), actual.totalLength());
        assertEquals(expected.offset(), actual.offset());
        assertArrayEquals(expected.chunk(), actual.chunk());
        assertEquals(0, buf.readableBytes());
    }

    @Test
    void rejectsChunkAboveProtocolLimit() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        new AudioTransferChunkPacket(UUID.randomUUID(), AudioTransferService.MAX_CHUNK_BYTES + 1, 0,
                new byte[AudioTransferService.MAX_CHUNK_BYTES + 1]).write(buf);

        assertThrows(DecoderException.class, () -> AudioTransferChunkPacket.read(buf));
    }
}
