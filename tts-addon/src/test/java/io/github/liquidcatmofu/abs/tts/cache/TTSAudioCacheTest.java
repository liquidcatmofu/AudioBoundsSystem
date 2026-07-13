package io.github.liquidcatmofu.abs.tts.cache;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class TTSAudioCacheTest {
    @Test
    void fullKeyIsIndependentOfParameterIterationOrder() {
        Map<String, Double> first = new LinkedHashMap<>();
        first.put("speedScale", 1.1);
        first.put("pitchScale", 0.05);
        Map<String, Double> second = new LinkedHashMap<>();
        second.put("pitchScale", 0.05);
        second.put("speedScale", 1.1);

        assertEquals(
                TTSAudioCache.computeKey("voicevox", "3", "hello", first),
                TTSAudioCache.computeKey("voicevox", "3", "hello", second));
    }

    @Test
    void fullKeyCoversEverySynthesisInput() {
        String baseline = TTSAudioCache.computeKey(
                "voicevox", "3", "hello", Map.of("speedScale", 1.0));

        assertNotEquals(baseline, TTSAudioCache.computeKey(
                "coeiroink", "3", "hello", Map.of("speedScale", 1.0)));
        assertNotEquals(baseline, TTSAudioCache.computeKey(
                "voicevox", "4", "hello", Map.of("speedScale", 1.0)));
        assertNotEquals(baseline, TTSAudioCache.computeKey(
                "voicevox", "3", "goodbye", Map.of("speedScale", 1.0)));
        assertNotEquals(baseline, TTSAudioCache.computeKey(
                "voicevox", "3", "hello", Map.of("speedScale", 1.1)));
    }

    @Test
    void legacyKeyRemainsStableForExistingCommandCaches() {
        assertEquals("9341952d71496c1b", TTSAudioCache.computeKey("3", "hello"));
    }
}
