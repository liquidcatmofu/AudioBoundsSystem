package io.github.liquidcatmofu.abs.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AudioTransferControlPacketTest {
    @Test
    void requestRoundTripsToken() {
        AudioTransferRequestPacket expected = new AudioTransferRequestPacket(UUID.randomUUID());
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());

        expected.write(buf);

        assertEquals(expected, AudioTransferRequestPacket.read(buf));
        assertEquals(0, buf.readableBytes());
    }

    @Test
    void errorRoundTripsTokenAndMessage() {
        AudioTransferErrorPacket expected = new AudioTransferErrorPacket(
                UUID.randomUUID(), "Audio transfer service is busy", true);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());

        expected.write(buf);

        assertEquals(expected, AudioTransferErrorPacket.read(buf));
        assertEquals(0, buf.readableBytes());
    }
}
