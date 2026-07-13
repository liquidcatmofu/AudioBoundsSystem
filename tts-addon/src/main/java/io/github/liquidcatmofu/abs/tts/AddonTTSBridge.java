package io.github.liquidcatmofu.abs.tts;

import io.github.liquidcatmofu.abs.tts.api.TTSProvider;
import io.github.liquidcatmofu.abs.tts.cache.TTSAudioCache;
import io.github.liquidcatmofu.abs.audio.AudioContent;
import io.github.liquidcatmofu.abs.ttsbridge.TTSBridge;
import io.github.liquidcatmofu.abs.ttsbridge.TTSEngine;
import io.github.liquidcatmofu.abs.ttsbridge.TTSParam;
import io.github.liquidcatmofu.abs.ttsbridge.TTSSynthesisRequest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** common の {@link TTSBridge} を tts-addon の {@link TTSProviderRegistry} で実装する。 */
public class AddonTTSBridge implements TTSBridge {
    private static final int CACHE_LOCK_COUNT = 64;
    private static final Object[] CACHE_LOCKS = createCacheLocks();

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
        if (request == null || request.engineId == null) {
            throw new IllegalArgumentException("TTS request and engineId are required");
        }
        TTSProvider provider = findProvider(request.engineId);
        if (provider == null) {
            throw new IllegalArgumentException("Unknown TTS engine: " + request.engineId);
        }
        validateRequest(provider, request);
        String cacheKey = TTSAudioCache.computeKey(
                request.engineId, request.speakerId, request.text, request.params);
        Object lock = CACHE_LOCKS[Math.floorMod(cacheKey.hashCode(), CACHE_LOCKS.length)];
        synchronized (lock) {
            byte[] cached = TTSAudioCache.load(
                    request.engineId, request.speakerId, request.text, request.params).orElse(null);
            if (cached != null) return cached;

            byte[] synthesized = provider.synthesizeToOgg(request.text, request.speakerId, request.params);
            AudioContent.requireOgg(synthesized);
            try {
                TTSAudioCache.save(request.engineId, request.speakerId, request.text,
                        request.params, synthesized);
            } catch (java.io.IOException e) {
                TTSAddon.LOGGER.warn("ABS TTS: failed to cache synthesis result", e);
            }
            return synthesized;
        }
    }

    private void validateRequest(TTSProvider provider, TTSSynthesisRequest request) {
        if (request.speakerId == null || request.speakerId.isBlank()) {
            throw new IllegalArgumentException("speakerId must not be blank");
        }
        if (request.text == null || request.text.isBlank()) {
            throw new IllegalArgumentException("text must not be blank");
        }
        if (request.text.length() > 10_000) {
            throw new IllegalArgumentException("text exceeds 10000 characters");
        }
        if (request.params == null) {
            request.params = new HashMap<>();
            return;
        }
        if (request.params.size() > 32) {
            throw new IllegalArgumentException("too many TTS parameters");
        }

        Map<String, TTSParam> schema = new HashMap<>();
        for (TTSParam param : provider.paramSchema()) {
            schema.put(param.key, param);
        }
        for (Map.Entry<String, Double> entry : request.params.entrySet()) {
            TTSParam param = schema.get(entry.getKey());
            if (param == null) {
                throw new IllegalArgumentException("Unsupported TTS parameter: " + entry.getKey());
            }
            Double value = entry.getValue();
            if (value == null || !Double.isFinite(value) || value < param.min || value > param.max) {
                throw new IllegalArgumentException("TTS parameter out of range: " + entry.getKey());
            }
        }
    }

    private TTSProvider findProvider(String id) {
        for (TTSProvider p : TTSProviderRegistry.getAll()) {
            if (p.getId().equals(id)) return p;
        }
        return null;
    }

    private static Object[] createCacheLocks() {
        Object[] locks = new Object[CACHE_LOCK_COUNT];
        for (int i = 0; i < locks.length; i++) locks[i] = new Object();
        return locks;
    }
}
