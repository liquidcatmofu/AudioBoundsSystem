package io.github.liquidcatmofu.abs.network;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayAudioPacketTest {
    @Test
    void roundTripsAllPlaybackMetadata() {
        PlayAudioPacket expected = new PlayAudioPacket(new BlockPos(-12, 64, 30),
                UUID.fromString("12345678-1234-5678-1234-567812345678"),
                "a".repeat(64), "Track title", "Subtitle text", 1_237);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());

        expected.write(buf);

        assertEquals(expected, PlayAudioPacket.read(buf));
        assertEquals(0, buf.readableBytes());
    }
}
