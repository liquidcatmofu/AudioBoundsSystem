package io.github.liquidcatmofu.abs.tts;

import io.github.liquidcatmofu.abs.tts.api.TTSProvider;
import io.github.liquidcatmofu.abs.ttsbridge.TTSBridge;
import io.github.liquidcatmofu.abs.ttsbridge.TTSEngine;
import io.github.liquidcatmofu.abs.ttsbridge.TTSSynthesisRequest;

import java.util.ArrayList;
import java.util.List;

/** common の {@link TTSBridge} を tts-addon の {@link TTSProviderRegistry} で実装する。 */
public class AddonTTSBridge implements TTSBridge {

    @Override
    public boolean isAvailable() {
        for (TTSProvider p : TTSProviderRegistry.getAll()) {
            if (p.isAvailable()) return true;
        }
        return false;
    }

    @Override
    public List<TTSEngine> listEngines() {
        List<TTSEngine> engines = new ArrayList<>();
        for (TTSProvider p : TTSProviderRegistry.getAll()) {
            if (!p.isAvailable()) continue;   // 起動していないエンジンはスキップ
            TTSEngine engine = new TTSEngine();
            engine.id = p.getId();
            engine.name = p.getDisplayName();
            engine.params = p.paramSchema();
            try {
                engine.speakers = p.listSpeakers();
            } catch (Exception e) {
                TTSAddon.LOGGER.warn("ABS TTS: failed to list speakers for {}", p.getId(), e);
            }
            engines.add(engine);
        }
        return engines;
    }

    @Override
    public byte[] synthesize(TTSSynthesisRequest request) throws Exception {
        TTSProvider provider = findProvider(request.engineId);
        if (provider == null) {
            throw new IllegalArgumentException("Unknown TTS engine: " + request.engineId);
        }
        return provider.synthesizeToOgg(request.text, request.speakerId, request.params);
    }

    private TTSProvider findProvider(String id) {
        for (TTSProvider p : TTSProviderRegistry.getAll()) {
            if (p.getId().equals(id)) return p;
        }
        return null;
    }
}
