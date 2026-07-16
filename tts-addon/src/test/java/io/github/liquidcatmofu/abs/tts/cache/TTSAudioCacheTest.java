package io.github.liquidcatmofu.abs.tts.cache;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.nio.file.attribute.FileTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class TTSAudioCacheTest {
    @TempDir
    Path tempDir;

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
    void fullKeyChangesWithSynthesisFormatVersion() {
        String current = TTSAudioCache.computeKey(
                "ogg-vorbis-v1", "voicevox", "3", "hello", Map.of());
        String future = TTSAudioCache.computeKey(
                "ogg-vorbis-v2", "voicevox", "3", "hello", Map.of());

        assertNotEquals(current, future);
        assertEquals(current, TTSAudioCache.computeKey("voicevox", "3", "hello", Map.of()));
    }

    @Test
    void simpleCommandKeyRemainsStable() {
        assertEquals("9341952d71496c1b", TTSAudioCache.computeKey("3", "hello"));
    }

    @Test
    void storesValidatedFullRequestResultsAndRejectsCorruption() throws Exception {
        TTSAudioCache.init(tempDir);
        Map<String, Double> params = Map.of("speedScale", 1.0);
        byte[] ogg = "OggS-cache".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        TTSAudioCache.save("voicevox", "3", "hello", params, ogg);
        org.junit.jupiter.api.Assertions.assertArrayEquals(ogg,
                TTSAudioCache.load("voicevox", "3", "hello", params).orElseThrow());

        Path cached = tempDir.resolve("abs_cache/tts")
                .resolve(TTSAudioCache.computeKey("voicevox", "3", "hello", params) + ".ogg");
        Files.writeString(cached, "broken");
        assertEquals(java.util.Optional.empty(),
                TTSAudioCache.load("voicevox", "3", "hello", params));
        org.junit.jupiter.api.Assertions.assertFalse(Files.exists(cached));
    }

    @Test
    void evictsLeastRecentlyUsedFilesBeyondTheCapacity() throws Exception {
        byte[] first = "OggS-first".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] second = "OggS-second".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        TTSAudioCache.init(tempDir, second.length);

        TTSAudioCache.save("voicevox", "3", "first", Map.of(), first);
        Path firstPath = tempDir.resolve("abs_cache/tts")
                .resolve(TTSAudioCache.computeKey("voicevox", "3", "first", Map.of()) + ".ogg");
        Files.setLastModifiedTime(firstPath, FileTime.fromMillis(1));
        TTSAudioCache.save("voicevox", "3", "second", Map.of(), second);

        Path secondPath = tempDir.resolve("abs_cache/tts")
                .resolve(TTSAudioCache.computeKey("voicevox", "3", "second", Map.of()) + ".ogg");
        org.junit.jupiter.api.Assertions.assertFalse(Files.exists(firstPath));
        org.junit.jupiter.api.Assertions.assertTrue(Files.exists(secondPath));
    }
}
