package io.github.liquidcatmofu.abs.tts;

import io.github.liquidcatmofu.abs.tts.api.TTSProvider;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public final class TTSProviderRegistry {
    private static final Map<String, TTSProvider> providers = new LinkedHashMap<>();
    private static String activeId = null;

    private TTSProviderRegistry() {}

    public static void register(TTSProvider provider) {
        providers.put(provider.getId(), provider);
        if (activeId == null) {
            activeId = provider.getId();
        }
        TTSAddon.LOGGER.info("ABS TTS: registered provider '{}'", provider.getId());
    }

    public static TTSProvider getActive() {
        if (activeId == null || !providers.containsKey(activeId)) {
            return null;
        }
        return providers.get(activeId);
    }

    public static boolean setActive(String id) {
        if (!providers.containsKey(id)) {
            return false;
        }
        activeId = id;
        return true;
    }

    public static String getActiveId() {
        return activeId;
    }

    public static Collection<TTSProvider> getAll() {
        return providers.values();
    }

    public static boolean has(String id) {
        return providers.containsKey(id);
    }
}
