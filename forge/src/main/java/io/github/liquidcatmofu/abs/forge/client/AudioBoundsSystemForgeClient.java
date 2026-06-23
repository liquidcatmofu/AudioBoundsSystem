package io.github.liquidcatmofu.abs.forge.client;

import io.github.liquidcatmofu.abs.AudioBoundsSystem;
import io.github.liquidcatmofu.abs.client.AudioBoundsSystemClient;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(value = Dist.CLIENT, modid = AudioBoundsSystem.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class AudioBoundsSystemForgeClient {
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        AudioBoundsSystemClient.init();
    }
}
