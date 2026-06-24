package io.github.liquidcatmofu.abs.client;

import io.github.liquidcatmofu.abs.client.audio.SpeakerAudioManager;
import io.github.liquidcatmofu.abs.client.subtitle.SubtitleOverlayManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.BlockPos;

import java.util.UUID;

@Environment(EnvType.CLIENT)
public final class ClientPacketHandler {
    private ClientPacketHandler() {}

    public static void onPlayAudio(BlockPos pos, UUID token, String trackTitle, String subtitle, int subtitleDurationTicks) {
        SpeakerAudioManager.INSTANCE.play(pos, token);
        SubtitleOverlayManager.INSTANCE.show(pos, trackTitle, subtitle, subtitleDurationTicks);
    }

    public static void onStopAudio(BlockPos pos) {
        SpeakerAudioManager.INSTANCE.stop(pos);
        SubtitleOverlayManager.INSTANCE.clear(pos);
    }
}
