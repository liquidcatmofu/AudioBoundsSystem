package io.github.liquidcatmofu.abs.tts.forge;

import dev.architectury.platform.forge.EventBuses;
import io.github.liquidcatmofu.abs.tts.TTSAddon;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(TTSAddon.MOD_ID)
public final class TTSAddonForge {
    public TTSAddonForge() {
        EventBuses.registerModEventBus(TTSAddon.MOD_ID, FMLJavaModLoadingContext.get().getModEventBus());
        TTSAddon.init();
    }
}
