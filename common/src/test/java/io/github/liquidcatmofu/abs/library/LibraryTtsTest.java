package io.github.liquidcatmofu.abs.library;

import io.github.liquidcatmofu.abs.ttsbridge.TTSSynthesisRequest;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LibraryTtsTest {
    @Test
    void identifiesDuplicatesBySynthesisInputsInsteadOfGeneratedBytes() {
        TtsEntry entry = new TtsEntry();
        entry.engineId = "voicevox";
        entry.speakerId = "2";
        entry.text = "こんにちは";
        entry.contentHash = "first-generated-hash";
        entry.params = new LinkedHashMap<>();
        entry.params.put("speedScale", 1.0);
        entry.params.put("pitchScale", 0.0);

        TTSSynthesisRequest sameRequest = new TTSSynthesisRequest();
        sameRequest.engineId = "voicevox";
        sameRequest.speakerId = "2";
        sameRequest.text = "こんにちは";
        sameRequest.params = Map.of("pitchScale", 0.0, "speedScale", 1.0);

        assertTrue(LibraryTts.sameSynthesisRequest(entry, sameRequest));

        sameRequest.params = Map.of("pitchScale", 0.1, "speedScale", 1.0);
        assertFalse(LibraryTts.sameSynthesisRequest(entry, sameRequest));
    }
}
