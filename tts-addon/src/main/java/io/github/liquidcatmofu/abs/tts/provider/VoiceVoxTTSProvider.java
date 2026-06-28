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
 * VOICEVOX HTTP API クライアント。
 * Forge(ModLauncher) 下で java.net.http.HttpClient の非同期セレクタが
 * ClosedChannelException を起こすため、同期的な HttpURLConnection を使用する。
 */
public class VoiceVoxTTSProvider implements TTSProvider {
    private static final String ID = "voicevox";
    /** VOICEVOX audio_query の調整可能フィールド */
    private static final String[] PARAM_KEYS = { "speedScale", "pitchScale", "intonationScale", "volumeScale" };

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
            HttpURLConnection con = open(TTSConfig.get().voicevoxUrl() + "/version", "GET", 3000, 3000);
            int code = con.getResponseCode();
            con.disconnect();
            return code == 200;
        } catch (Exception e) {
            TTSAddon.LOGGER.warn("ABS TTS: VOICEVOX availability check failed ({}): {}",
                    TTSConfig.get().voicevoxUrl(), e.toString());
            return false;
        }
    }

    @Override
    public List<TTSSpeaker> listSpeakers() {
        List<TTSSpeaker> speakers = new ArrayList<>();
        try {
            HttpURLConnection con = open(TTSConfig.get().voicevoxUrl() + "/speakers", "GET", 5000, 10000);
            if (con.getResponseCode() != 200) {
                con.disconnect();
                return speakers;
            }
            String body = readString(con.getInputStream());
            con.disconnect();

            JsonArray arr = JsonParser.parseString(body).getAsJsonArray();
            for (var el : arr) {
                JsonObject sp = el.getAsJsonObject();
                String charName = sp.get("name").getAsString();
                for (var st : sp.getAsJsonArray("styles")) {
                    JsonObject style = st.getAsJsonObject();
                    String id = style.get("id").getAsString();
                    String styleName = style.has("name") ? style.get("name").getAsString() : "";
                    speakers.add(new TTSSpeaker(id, charName + " / " + styleName));
                }
            }
        } catch (Exception e) {
            TTSAddon.LOGGER.warn("ABS TTS: failed to fetch VOICEVOX speakers: {}", e.toString());
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

        // 1. audio_query（クエリ文字列で指定、ボディ無し POST）
        HttpURLConnection queryCon = open(
                base + "/audio_query?text=" + encodedText + "&speaker=" + speakerId, "POST", 5000, 30000);
        queryCon.setDoOutput(true);
        queryCon.getOutputStream().close(); // 空ボディ
        if (queryCon.getResponseCode() != 200) {
            String err = readError(queryCon);
            queryCon.disconnect();
            throw new IOException("VOICEVOX /audio_query failed: HTTP " + queryCon.getResponseCode() + " " + err);
        }
        String audioQueryJson = readString(queryCon.getInputStream());
        queryCon.disconnect();

        // 2. パラメータを適用
        JsonObject query = JsonParser.parseString(audioQueryJson).getAsJsonObject();
        if (params != null) {
            for (String key : PARAM_KEYS) {
                Double v = params.get(key);
                if (v != null) query.addProperty(key, v);
            }
        }
        byte[] queryBytes = query.toString().getBytes(StandardCharsets.UTF_8);

        // 3. synthesis → WAV
        HttpURLConnection synthCon = open(base + "/synthesis?speaker=" + speakerId, "POST", 5000, 60000);
        synthCon.setDoOutput(true);
        synthCon.setRequestProperty("Content-Type", "application/json");
        synthCon.setRequestProperty("Accept", "audio/wav");
        try (OutputStream os = synthCon.getOutputStream()) {
            os.write(queryBytes);
        }
        if (synthCon.getResponseCode() != 200) {
            synthCon.disconnect();
            throw new IOException("VOICEVOX /synthesis failed: HTTP " + synthCon.getResponseCode());
        }
        byte[] wav = readBytes(synthCon.getInputStream());
        synthCon.disconnect();

        // 4. WAV → Ogg
        return FfmpegTranscoder.toOgg(wav, TTSConfig.get().ffmpegPath());
    }

    // ── helpers ────────────────────────────────────────

    private static HttpURLConnection open(String url, String method, int connectTimeout, int readTimeout) throws IOException {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setRequestMethod(method);
        con.setConnectTimeout(connectTimeout);
        con.setReadTimeout(readTimeout);
        return con;
    }

    private static String readString(InputStream is) throws IOException {
        try (is) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static byte[] readBytes(InputStream is) throws IOException {
        try (is) {
            return is.readAllBytes();
        }
    }

    private static String readError(HttpURLConnection con) {
        try (InputStream es = con.getErrorStream()) {
            return es == null ? "" : new String(es.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }
}
