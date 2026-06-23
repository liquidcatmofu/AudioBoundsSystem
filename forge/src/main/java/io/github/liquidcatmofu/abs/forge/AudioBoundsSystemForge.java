package io.github.liquidcatmofu.abs.forge;

import io.github.liquidcatmofu.abs.AudioBoundsSystem;
import dev.architectury.platform.forge.EventBuses;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(AudioBoundsSystem.MOD_ID)
public final class AudioBoundsSystemForge {
    public AudioBoundsSystemForge() {
        // Submit our event bus to let Architectury API register our content on the right time.
        EventBuses.registerModEventBus(AudioBoundsSystem.MOD_ID, FMLJavaModLoadingContext.get().getModEventBus());

        // Run our common setup.
        AudioBoundsSystem.init();
    }
}
