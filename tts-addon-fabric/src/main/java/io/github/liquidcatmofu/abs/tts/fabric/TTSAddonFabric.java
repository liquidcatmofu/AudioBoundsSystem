package io.github.liquidcatmofu.abs.tts.fabric;

import io.github.liquidcatmofu.abs.tts.TTSAddon;
import net.fabricmc.api.ModInitializer;

public final class TTSAddonFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        TTSAddon.init();
    }
}
