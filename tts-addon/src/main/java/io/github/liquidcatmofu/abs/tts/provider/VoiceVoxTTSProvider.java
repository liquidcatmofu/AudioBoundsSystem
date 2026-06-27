package io.github.liquidcatmofu.abs.tts.provider;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.liquidcatmofu.abs.tts.TTSAddon;
import io.github.liquidcatmofu.abs.tts.api.TTSProvider;
import io.github.liquidcatmofu.abs.tts.config.TTSConfig;
import io.github.liquidcatmofu.abs.tts.transcode.FfmpegTranscoder;
import io.github.liquidcatmofu.abs.ttsbridge.TTSParam;
import io.github.liquidcatmofu.abs.ttsbridge.TTSSpeaker;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class VoiceVoxTTSProvider implements TTSProvider {
    private static final String ID = "voicevox";
    /** VOICEVOX audio_query の調整可能フィールド */
    private static final String[] PARAM_KEYS = { "speedScale", "pitchScale", "intonationScale", "volumeScale" };

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getDisplayName() {
        return "VOICEVOX";
    }

    @Override
    public boolean isAvailable() {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(TTSConfig.get().voicevoxUrl() + "/version"))
                    .timeout(Duration.ofSeconds(3))
                    .GET().build();
            return http.send(req, HttpResponse.BodyHandlers.discarding()).statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public List<TTSSpeaker> listSpeakers() {
        List<TTSSpeaker> speakers = new ArrayList<>();
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(TTSConfig.get().voicevoxUrl() + "/speakers"))
                    .timeout(Duration.ofSeconds(10))
                    .GET().build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (res.statusCode() != 200) return speakers;

            JsonArray arr = JsonParser.parseString(res.body()).getAsJsonArray();
            for (var el : arr) {
                JsonObject sp = el.getAsJsonObject();
                String charName = sp.get("name").getAsString();
                JsonArray styles = sp.getAsJsonArray("styles");
                for (var st : styles) {
                    JsonObject style = st.getAsJsonObject();
                    String id = style.get("id").getAsString();
                    String styleName = style.has("name") ? style.get("name").getAsString() : "";
                    speakers.add(new TTSSpeaker(id, charName + " / " + styleName));
                }
            }
        } catch (Exception e) {
            TTSAddon.LOGGER.warn("ABS TTS: failed to fetch VOICEVOX speakers: {}", e.getMessage());
        }
        return speakers;
    }

    @Override
    public List<TTSParam> paramSchema() {
        return List.of(
            new TTSParam("speedScale",      "速度", 0.5,   2.0, 0.05, 1.0),
            new TTSParam("pitchScale",      "ピッチ", -0.15, 0.15, 0.01, 0.0),
            new TTSParam("intonationScale", "抑揚", 0.0,   2.0, 0.05, 1.0),
            new TTSParam("volumeScale",     "音量", 0.0,   2.0, 0.05, 1.0)
        );
    }

    @Override
    public byte[] synthesizeToOgg(String text, String speakerId, Map<String, Double> params) throws Exception {
        String base = TTSConfig.get().voicevoxUrl();
        String encodedText = URLEncoder.encode(text, StandardCharsets.UTF_8);

        // 1. audio_query
        HttpRequest queryReq = HttpRequest.newBuilder()
                .uri(URI.create(base + "/audio_query?text=" + encodedText + "&speaker=" + speakerId))
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<String> queryRes = http.send(queryReq, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (queryRes.statusCode() != 200) {
            throw new RuntimeException("VOICEVOX /audio_query failed: HTTP " + queryRes.statusCode());
        }

        // 2. パラメータを適用
        JsonObject query = JsonParser.parseString(queryRes.body()).getAsJsonObject();
        if (params != null) {
            for (String key : PARAM_KEYS) {
                Double v = params.get(key);
                if (v != null) query.addProperty(key, v);
            }
        }

        // 3. synthesis → WAV
        HttpRequest synthReq = HttpRequest.newBuilder()
                .uri(URI.create(base + "/synthesis?speaker=" + speakerId))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json")
                .header("Accept", "audio/wav")
                .POST(HttpRequest.BodyPublishers.ofString(query.toString(), StandardCharsets.UTF_8))
                .build();
        HttpResponse<byte[]> synthRes = http.send(synthReq, HttpResponse.BodyHandlers.ofByteArray());
        if (synthRes.statusCode() != 200) {
            throw new RuntimeException("VOICEVOX /synthesis failed: HTTP " + synthRes.statusCode());
        }

        // 4. WAV → Ogg
        return FfmpegTranscoder.toOgg(synthRes.body(), TTSConfig.get().ffmpegPath());
    }
}
