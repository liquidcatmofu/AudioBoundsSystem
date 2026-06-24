package io.github.liquidcatmofu.abs.client;

import dev.architectury.event.events.client.ClientPlayerEvent;
import dev.architectury.event.events.client.ClientTickEvent;
import io.github.liquidcatmofu.abs.client.audio.SpeakerAudioManager;
import io.github.liquidcatmofu.abs.client.gui.SpeakerConfigScreen;
import io.github.liquidcatmofu.abs.network.ABSNetwork;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

@Environment(EnvType.CLIENT)
public final class AudioBoundsSystemClient {
    public static void init() {
        ABSNetwork.registerClientHandlers();
        ClientTickEvent.CLIENT_POST.register(client -> SpeakerAudioManager.INSTANCE.tick(client));
        ClientPlayerEvent.CLIENT_PLAYER_QUIT.register(player -> SpeakerAudioManager.INSTANCE.stopAll());
    }

    public static void openSpeakerConfigScreen(BlockPos pos) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreen(new SpeakerConfigScreen(pos, minecraft.screen));
    }
}
