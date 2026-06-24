package io.github.liquidcatmofu.abs.client;

import io.github.liquidcatmofu.abs.client.audio.SpeakerAudioManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.BlockPos;

import java.util.UUID;

@Environment(EnvType.CLIENT)
public final class ClientPacketHandler {
    private ClientPacketHandler() {}

    public static void onPlayAudio(BlockPos pos, UUID token) {
        SpeakerAudioManager.INSTANCE.play(pos, token);
    }

    public static void onStopAudio(BlockPos pos) {
        SpeakerAudioManager.INSTANCE.stop(pos);
    }
}
