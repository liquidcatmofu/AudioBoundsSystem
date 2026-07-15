package io.github.liquidcatmofu.abs.network;

import io.github.liquidcatmofu.abs.server.AudioTransferService;
import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;

public record AudioTransferChunkPacket(UUID token, int totalLength, int offset, byte[] chunk) {
    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(token);
        buf.writeVarInt(totalLength);
        buf.writeVarInt(offset);
        buf.writeByteArray(chunk);
    }

    public static AudioTransferChunkPacket read(FriendlyByteBuf buf) {
        return new AudioTransferChunkPacket(buf.readUUID(), buf.readVarInt(), buf.readVarInt(),
                buf.readByteArray(AudioTransferService.MAX_CHUNK_BYTES));
    }
}
