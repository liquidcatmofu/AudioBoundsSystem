package io.github.liquidcatmofu.abs.fabric.client;

import io.github.liquidcatmofu.abs.client.AudioBoundsSystemClient;
import net.fabricmc.api.ClientModInitializer;

public final class AudioBoundsSystemFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        AudioBoundsSystemClient.init();
    }
}
