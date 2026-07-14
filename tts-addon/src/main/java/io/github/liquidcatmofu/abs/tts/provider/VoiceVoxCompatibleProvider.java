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
import io.github.liquidcatmofu.abs.ttsbridge.TTSSynthesisException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
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
    static final int MAX_JSON_RESPONSE_BYTES = 4 * 1024 * 1024;
    static final int MAX_WAV_RESPONSE_BYTES = 64 * 1024 * 1024;
    private static final int MAX_ERROR_RESPONSE_BYTES = 16 * 1024;

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
        HttpURLConnection con = null;
        try {
            con = open(baseUrl() + "/version", "GET", 500, 500);
            return con.getResponseCode() == 200;
        } catch (Exception e) {
            TTSAddon.LOGGER.debug("ABS TTS: {} not available at {}: {}", id, baseUrl(), e.toString());
            return false;
        } finally {
            if (con != null) con.disconnect();
        }
    }

    @Override
    public List<TTSSpeaker> listSpeakers() {
        List<TTSSpeaker> speakers = new ArrayList<>();
        HttpURLConnection con = null;
        try {
            con = open(baseUrl() + "/speakers", "GET", 5000, 10000);
            if (con.getResponseCode() != 200) return speakers;
            String body = readString(con.getInputStream(), con.getContentLengthLong(),
                    MAX_JSON_RESPONSE_BYTES, "speaker response");

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
        } finally {
            if (con != null) con.disconnect();
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
        try {
            return synthesize(text, speakerId, params);
        } catch (TTSSynthesisException e) {
            throw e;
        } catch (SocketTimeoutException e) {
            throw new TTSSynthesisException(TTSSynthesisException.Kind.TIMEOUT,
                    id + " timed out", e);
        } catch (ConnectException e) {
            throw new TTSSynthesisException(TTSSynthesisException.Kind.UNAVAILABLE,
                    id + " is unavailable", e);
        }
    }

    private byte[] synthesize(String text, String speakerId, Map<String, Double> params) throws Exception {
        String base = baseUrl();
        String encodedText = URLEncoder.encode(text, StandardCharsets.UTF_8);

        // 1. audio_query
        String audioQueryJson;
        HttpURLConnection queryCon = open(
                base + "/audio_query?text=" + encodedText + "&speaker=" + speakerId, "POST", 5000, 30000);
        try {
            queryCon.setDoOutput(true);
            queryCon.getOutputStream().close();
            int responseCode = queryCon.getResponseCode();
            if (responseCode != 200) {
                throw providerHttpError("/audio_query", responseCode, queryCon);
            }
            audioQueryJson = readString(queryCon.getInputStream(), queryCon.getContentLengthLong(),
                    MAX_JSON_RESPONSE_BYTES, "audio_query response");
        } finally {
            queryCon.disconnect();
        }

        // 2. パラメータを適用（pauseLength は -1 = null/自動）
        JsonObject query;
        try {
            query = JsonParser.parseString(audioQueryJson).getAsJsonObject();
        } catch (RuntimeException e) {
            throw new TTSSynthesisException(TTSSynthesisException.Kind.INVALID_RESPONSE,
                    id + " returned an invalid audio_query response", e);
        }
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
        byte[] wav;
        HttpURLConnection synthCon = open(base + "/synthesis?speaker=" + speakerId, "POST", 5000, 60000);
        try {
            synthCon.setDoOutput(true);
            synthCon.setRequestProperty("Content-Type", "application/json");
            synthCon.setRequestProperty("Accept", "audio/wav");
            try (OutputStream os = synthCon.getOutputStream()) { os.write(queryBytes); }
            int responseCode = synthCon.getResponseCode();
            if (responseCode != 200) {
                throw providerHttpError("/synthesis", responseCode, synthCon);
            }
            wav = readBytes(synthCon.getInputStream(), synthCon.getContentLengthLong(),
                    MAX_WAV_RESPONSE_BYTES, "synthesis WAV response");
        } finally {
            synthCon.disconnect();
        }

        // 4. WAV → Ogg Vorbis
        return FfmpegTranscoder.toOgg(wav, TTSConfig.get().ffmpegPath());
    }

    private TTSSynthesisException providerHttpError(String endpoint, int status, HttpURLConnection connection) {
        return new TTSSynthesisException(TTSSynthesisException.Kind.HTTP_ERROR, status,
                id + " " + endpoint + " failed: HTTP " + status + " " + readError(connection));
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private static HttpURLConnection open(String url, String method, int connectMs, int readMs) throws IOException {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setRequestMethod(method);
        con.setConnectTimeout(connectMs);
        con.setReadTimeout(readMs);
        return con;
    }

    static String readString(InputStream input, long declaredLength, int maxBytes, String description)
            throws IOException {
        return new String(readBytes(input, declaredLength, maxBytes, description), StandardCharsets.UTF_8);
    }

    static byte[] readBytes(InputStream input, long declaredLength, int maxBytes, String description)
            throws IOException {
        if (maxBytes < 1) throw new IllegalArgumentException("maxBytes must be positive");
        if (declaredLength > maxBytes) {
            try (input) {
                throw new IOException(description + " exceeds " + maxBytes + " bytes");
            }
        }
        try (input) {
            byte[] bytes = input.readNBytes(maxBytes + 1);
            if (bytes.length > maxBytes) {
                throw new IOException(description + " exceeds " + maxBytes + " bytes");
            }
            return bytes;
        }
    }

    private static String readError(HttpURLConnection con) {
        try (InputStream es = con.getErrorStream()) {
            if (es == null) return "";
            byte[] bytes = es.readNBytes(MAX_ERROR_RESPONSE_BYTES + 1);
            boolean truncated = bytes.length > MAX_ERROR_RESPONSE_BYTES;
            int length = Math.min(bytes.length, MAX_ERROR_RESPONSE_BYTES);
            return new String(bytes, 0, length, StandardCharsets.UTF_8) + (truncated ? "…" : "");
        } catch (Exception e) { return ""; }
    }
}
