package io.github.liquidcatmofu.abs.tts.provider;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.liquidcatmofu.abs.tts.TTSAddon;
import io.github.liquidcatmofu.abs.tts.api.TTSProvider;
import io.github.liquidcatmofu.abs.tts.config.TTSConfig;
import io.github.liquidcatmofu.abs.tts.transcode.FfmpegTranscoder;
import io.github.liquidcatmofu.abs.ttsbridge.TTSParam;
import io.github.liquidcatmofu.abs.ttsbridge.TTSSpeaker;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * VOICEVOX 互換 REST API を持つ任意のエンジンに対応する汎用プロバイダー。
 * VOICEVOX / COEIROINK / AivisSpeech / Sharevox / LMROID など同じ
 * /audio_query → /synthesis エンドポイント構成を持つエンジンに使用できる。
 *
 * Forge(ModLauncher) 下で java.net.http.HttpClient の非同期セレクタが
 * ClosedChannelException を起こすため、同期的な HttpURLConnection を使用する。
 */
public class VoiceVoxCompatibleProvider implements TTSProvider {

    private static final String[] PARAM_KEYS = {
        "speedScale", "pitchScale", "intonationScale", "volumeScale",
        "prePhonemeLength", "postPhonemeLength", "pauseLengthScale", "pauseLength"
    };

    private final String id;
    private final String displayName;
    private final String defaultUrl;

    public VoiceVoxCompatibleProvider(String id, String displayName, String defaultUrl) {
        this.id = id;
        this.displayName = displayName;
        this.defaultUrl = defaultUrl;
    }

    /** TTSConfig にエンジン固有の URL オーバーライドがあればそちらを優先する。 */
    private String baseUrl() {
        String override = TTSConfig.get().engineUrl(id);
        return override != null ? override : defaultUrl;
    }

    @Override public String getId() { return id; }
    @Override public String getDisplayName() { return displayName; }

    @Override
    public boolean isAvailable() {
        try {
            HttpURLConnection con = open(baseUrl() + "/version", "GET", 500, 500);
            int code = con.getResponseCode();
            con.disconnect();
            return code == 200;
        } catch (Exception e) {
            TTSAddon.LOGGER.debug("ABS TTS: {} not available at {}: {}", id, baseUrl(), e.toString());
            return false;
        }
    }

    @Override
    public List<TTSSpeaker> listSpeakers() {
        List<TTSSpeaker> speakers = new ArrayList<>();
        try {
            HttpURLConnection con = open(baseUrl() + "/speakers", "GET", 5000, 10000);
            if (con.getResponseCode() != 200) { con.disconnect(); return speakers; }
            String body = readString(con.getInputStream());
            con.disconnect();

            JsonArray arr = JsonParser.parseString(body).getAsJsonArray();
            for (var el : arr) {
                JsonObject sp = el.getAsJsonObject();
                String charName = sp.get("name").getAsString();
                for (var st : sp.getAsJsonArray("styles")) {
                    JsonObject style = st.getAsJsonObject();
                    String sid = style.get("id").getAsString();
                    String styleName = style.has("name") ? style.get("name").getAsString() : "";
                    speakers.add(new TTSSpeaker(sid, charName + " / " + styleName));
                }
            }
        } catch (Exception e) {
            TTSAddon.LOGGER.warn("ABS TTS: failed to fetch speakers from {}: {}", id, e.toString());
        }
        return speakers;
    }

    @Override
    public List<TTSParam> paramSchema() {
        return List.of(
            new TTSParam("speedScale",        "速度",           0.5,   2.0,  0.05, 1.0),
            new TTSParam("pitchScale",        "ピッチ",         -0.15, 0.15, 0.01, 0.0),
            new TTSParam("intonationScale",   "抑揚",           0.0,   2.0,  0.05, 1.0),
            new TTSParam("volumeScale",       "音量",           0.0,   2.0,  0.05, 1.0),
            new TTSParam("prePhonemeLength",  "開始無音(秒)",   0.0,   1.5,  0.01, 0.1),
            new TTSParam("postPhonemeLength", "終了無音(秒)",   0.0,   1.5,  0.01, 0.1),
            new TTSParam("pauseLengthScale",  "無音スケール",   0.0,   2.0,  0.05, 1.0),
            new TTSParam("pauseLength",       "句読点無音(秒)", -1.0,  2.0,  0.05, -1.0)
        );
    }

    @Override
    public byte[] synthesizeToOgg(String text, String speakerId, Map<String, Double> params) throws Exception {
        String base = baseUrl();
        String encodedText = URLEncoder.encode(text, StandardCharsets.UTF_8);

        // 1. audio_query
        HttpURLConnection queryCon = open(
                base + "/audio_query?text=" + encodedText + "&speaker=" + speakerId, "POST", 5000, 30000);
        queryCon.setDoOutput(true);
        queryCon.getOutputStream().close();
        if (queryCon.getResponseCode() != 200) {
            String err = readError(queryCon);
            queryCon.disconnect();
            throw new IOException(id + " /audio_query failed: HTTP " + queryCon.getResponseCode() + " " + err);
        }
        String audioQueryJson = readString(queryCon.getInputStream());
        queryCon.disconnect();

        // 2. パラメータを適用（pauseLength は -1 = null/自動）
        JsonObject query = JsonParser.parseString(audioQueryJson).getAsJsonObject();
        if (params != null) {
            for (String key : PARAM_KEYS) {
                Double v = params.get(key);
                if (v == null) continue;
                if ("pauseLength".equals(key) && v < 0) {
                    query.add(key, JsonNull.INSTANCE);
                } else {
                    query.addProperty(key, v);
                }
            }
        }
        byte[] queryBytes = query.toString().getBytes(StandardCharsets.UTF_8);

        // 3. synthesis → WAV
        HttpURLConnection synthCon = open(base + "/synthesis?speaker=" + speakerId, "POST", 5000, 60000);
        synthCon.setDoOutput(true);
        synthCon.setRequestProperty("Content-Type", "application/json");
        synthCon.setRequestProperty("Accept", "audio/wav");
        try (OutputStream os = synthCon.getOutputStream()) { os.write(queryBytes); }
        if (synthCon.getResponseCode() != 200) {
            synthCon.disconnect();
            throw new IOException(id + " /synthesis failed: HTTP " + synthCon.getResponseCode());
        }
        byte[] wav = readBytes(synthCon.getInputStream());
        synthCon.disconnect();

        // 4. WAV → Ogg Vorbis
        return FfmpegTranscoder.toOgg(wav, TTSConfig.get().ffmpegPath());
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private static HttpURLConnection open(String url, String method, int connectMs, int readMs) throws IOException {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setRequestMethod(method);
        con.setConnectTimeout(connectMs);
        con.setReadTimeout(readMs);
        return con;
    }

    private static String readString(InputStream is) throws IOException {
        try (is) { return new String(is.readAllBytes(), StandardCharsets.UTF_8); }
    }

    private static byte[] readBytes(InputStream is) throws IOException {
        try (is) { return is.readAllBytes(); }
    }

    private static String readError(HttpURLConnection con) {
        try (InputStream es = con.getErrorStream()) {
            return es == null ? "" : new String(es.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) { return ""; }
    }
}
