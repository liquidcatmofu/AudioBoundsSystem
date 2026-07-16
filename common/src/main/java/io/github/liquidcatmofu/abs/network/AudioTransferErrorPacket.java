package io.github.liquidcatmofu.abs.network;

import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;

public record AudioTransferErrorPacket(UUID token, String message, boolean retryable) {
    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(token);
        buf.writeUtf(message, 128);
        buf.writeBoolean(retryable);
    }

    public static AudioTransferErrorPacket read(FriendlyByteBuf buf) {
        return new AudioTransferErrorPacket(buf.readUUID(), buf.readUtf(128), buf.readBoolean());
    }
}
