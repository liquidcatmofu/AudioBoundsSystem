package io.github.liquidcatmofu.abs.tts;

import dev.architectury.event.events.common.LifecycleEvent;
import io.github.liquidcatmofu.abs.tts.cache.TTSAudioCache;
import io.github.liquidcatmofu.abs.tts.command.TTSAddonCommand;
import io.github.liquidcatmofu.abs.tts.provider.VoiceVoxCompatibleProvider;
import io.github.liquidcatmofu.abs.ttsbridge.TTSBridgeRegistry;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TTSAddon {
    public static final String MOD_ID = "abs_tts";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private TTSAddon() {}

    public static void init() {
        // VOICEVOX 互換 API を持つエンジンを一括登録（起動中のもののみ UI に表示される）
        TTSProviderRegistry.register(new VoiceVoxCompatibleProvider("voicevox",    "VOICEVOX",        "http://127.0.0.1:50021"));
        TTSProviderRegistry.register(new VoiceVoxCompatibleProvider("coeiroink",   "COEIROINK",       "http://127.0.0.1:50032"));
        TTSProviderRegistry.register(new VoiceVoxCompatibleProvider("aivisspeech", "AivisSpeech",     "http://127.0.0.1:10101"));
        TTSProviderRegistry.register(new VoiceVoxCompatibleProvider("sharevox",    "Sharevox",        "http://127.0.0.1:50025"));
        TTSProviderRegistry.register(new VoiceVoxCompatibleProvider("lmroid",      "LMROID",          "http://127.0.0.1:49513"));
        TTSBridgeRegistry.set(new AddonTTSBridge());

        LifecycleEvent.SERVER_STARTING.register(server ->
            TTSAudioCache.init(server.getWorldPath(LevelResource.ROOT))
        );
        TTSAddonCommand.register();
        LOGGER.info("ABS TTS Addon initialized");
    }
}
