package io.github.liquidcatmofu.abs.tts;

import dev.architectury.event.events.common.LifecycleEvent;
import io.github.liquidcatmofu.abs.tts.cache.TTSAudioCache;
import io.github.liquidcatmofu.abs.tts.command.TTSAddonCommand;
import io.github.liquidcatmofu.abs.tts.config.TTSConfig;
import io.github.liquidcatmofu.abs.tts.provider.VoiceVoxCompatibleProvider;
import io.github.liquidcatmofu.abs.ttsbridge.TTSBridgeRegistry;
import net.minecraft.world.level.storage.LevelResource;
import dev.architectury.platform.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TTSAddon {
    public static final String MOD_ID = "abs_tts";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private TTSAddon() {}

    public static void init() {
        TTSConfig.load(Platform.getConfigFolder().resolve("abs-tts.toml"));
        // VOICEVOX 互換 API を持つエンジンを一括登録（起動中のもののみ UI に表示される）
        TTSProviderRegistry.register(new VoiceVoxCompatibleProvider("voicevox",    "VOICEVOX",        defaultUrl("voicevox")));
        TTSProviderRegistry.register(new VoiceVoxCompatibleProvider("coeiroink",   "COEIROINK",       defaultUrl("coeiroink")));
        TTSProviderRegistry.register(new VoiceVoxCompatibleProvider("aivisspeech", "AivisSpeech",     defaultUrl("aivisspeech")));
        TTSProviderRegistry.register(new VoiceVoxCompatibleProvider("sharevox",    "Sharevox",        defaultUrl("sharevox")));
        TTSProviderRegistry.register(new VoiceVoxCompatibleProvider("lmroid",      "LMROID",          defaultUrl("lmroid")));
        TTSBridgeRegistry.set(new AddonTTSBridge());

        LifecycleEvent.SERVER_STARTING.register(server -> TTSAudioCache.init(
                server.getWorldPath(LevelResource.ROOT), TTSConfig.get().cacheMaxBytes()));
        TTSAddonCommand.register();
        LOGGER.info("ABS TTS Addon initialized");
    }

    private static String defaultUrl(String engineId) {
        return TTSConfig.DEFAULT_ENGINE_URLS.get(engineId);
    }
}
