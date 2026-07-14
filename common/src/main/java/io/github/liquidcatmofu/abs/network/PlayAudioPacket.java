package io.github.liquidcatmofu.abs.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;

public record PlayAudioPacket(BlockPos pos, UUID token, String contentHash,
                              String trackTitle, String subtitle, int subtitleDurationTicks) {
    public void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeUUID(token);
        buf.writeUtf(contentHash, 64);
        buf.writeUtf(trackTitle, 128);
        buf.writeUtf(subtitle, 512);
        buf.writeVarInt(subtitleDurationTicks);
    }

    public static PlayAudioPacket read(FriendlyByteBuf buf) {
        return new PlayAudioPacket(buf.readBlockPos(), buf.readUUID(), buf.readUtf(64),
                buf.readUtf(128), buf.readUtf(512), buf.readVarInt());
    }
}
