package io.github.liquidcatmofu.abs.tts;

import dev.architectury.event.events.common.LifecycleEvent;
import io.github.liquidcatmofu.abs.tts.cache.TTSAudioCache;
import io.github.liquidcatmofu.abs.tts.command.TTSAddonCommand;
import io.github.liquidcatmofu.abs.tts.provider.VoiceVoxTTSProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TTSAddon {
    public static final String MOD_ID = "abs_tts";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private TTSAddon() {}

    public static void init() {
        TTSProviderRegistry.register(new VoiceVoxTTSProvider());

        LifecycleEvent.SERVER_STARTING.register(server ->
            TTSAudioCache.init(server.getServerDirectory().toPath())
        );
        TTSAddonCommand.register();
        LOGGER.info("ABS TTS Addon initialized");
    }
}
