package io.github.liquidcatmofu.abs.tts;

import io.github.liquidcatmofu.abs.tts.api.TTSProvider;
import io.github.liquidcatmofu.abs.ttsbridge.TTSParam;
import io.github.liquidcatmofu.abs.ttsbridge.TTSSpeaker;
import io.github.liquidcatmofu.abs.ttsbridge.TTSSynthesisRequest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AddonTTSBridgeTest {
    private static final CapturingProvider PROVIDER = new CapturingProvider();

    @BeforeAll
    static void registerFakeProvider() {
        TTSProviderRegistry.register(PROVIDER);
    }

    @Test
    void forwardsACompleteSynthesisRequestToTheSelectedProvider() throws Exception {
        TTSSynthesisRequest request = new TTSSynthesisRequest();
        request.engineId = PROVIDER.getId();
        request.speakerId = "speaker-7";
        request.text = "次は東京です";
        request.params.put("speedScale", 1.15);

        byte[] result = new AddonTTSBridge().synthesize(request);

        assertArrayEquals("fake-ogg".getBytes(StandardCharsets.UTF_8), result);
        assertEquals(request.text, PROVIDER.text);
        assertEquals(request.speakerId, PROVIDER.speakerId);
        assertEquals(request.params, PROVIDER.params);
    }

    @Test
    void exposesAvailableProviderMetadata() {
        AddonTTSBridge bridge = new AddonTTSBridge();

        assertTrue(bridge.isAvailable());
        var engine = bridge.listEngines().stream()
                .filter(candidate -> PROVIDER.getId().equals(candidate.id))
                .findFirst().orElseThrow();
        assertEquals(PROVIDER.getDisplayName(), engine.name);
        assertEquals(1, engine.speakers.size());
        assertEquals(1, engine.params.size());
    }

    @Test
    void rejectsAnUnknownEngine() {
        TTSSynthesisRequest request = new TTSSynthesisRequest();
        request.engineId = "missing-engine";

        assertThrows(IllegalArgumentException.class,
                () -> new AddonTTSBridge().synthesize(request));
    }

    @Test
    void rejectsUnsupportedAndOutOfRangeParameters() {
        TTSSynthesisRequest unsupported = validRequest();
        unsupported.params.put("unknown", 1.0);
        assertThrows(IllegalArgumentException.class,
                () -> new AddonTTSBridge().synthesize(unsupported));

        TTSSynthesisRequest outOfRange = validRequest();
        outOfRange.params.put("speedScale", 3.0);
        assertThrows(IllegalArgumentException.class,
                () -> new AddonTTSBridge().synthesize(outOfRange));

        TTSSynthesisRequest nonFinite = validRequest();
        nonFinite.params.put("speedScale", Double.NaN);
        assertThrows(IllegalArgumentException.class,
                () -> new AddonTTSBridge().synthesize(nonFinite));
    }

    private static TTSSynthesisRequest validRequest() {
        TTSSynthesisRequest request = new TTSSynthesisRequest();
        request.engineId = PROVIDER.getId();
        request.speakerId = "speaker-7";
        request.text = "test";
        return request;
    }

    private static final class CapturingProvider implements TTSProvider {
        private String text;
        private String speakerId;
        private Map<String, Double> params;

        @Override public String getId() { return "fake-regression-provider"; }
        @Override public String getDisplayName() { return "Fake Regression Provider"; }
        @Override public boolean isAvailable() { return true; }
        @Override public List<TTSSpeaker> listSpeakers() {
            return List.of(new TTSSpeaker("speaker-7", "Test Speaker"));
        }
        @Override public List<TTSParam> paramSchema() {
            return List.of(new TTSParam("speedScale", "Speed", 0.5, 2.0, 0.05, 1.0));
        }
        @Override
        public byte[] synthesizeToOgg(String text, String speakerId, Map<String, Double> params) {
            this.text = text;
            this.speakerId = speakerId;
            this.params = Map.copyOf(params);
            return "fake-ogg".getBytes(StandardCharsets.UTF_8);
        }
    }
}
