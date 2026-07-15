package io.github.liquidcatmofu.abs.network;

import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;

public record AudioTransferRequestPacket(UUID token) {
    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(token);
    }

    public static AudioTransferRequestPacket read(FriendlyByteBuf buf) {
        return new AudioTransferRequestPacket(buf.readUUID());
    }
}
