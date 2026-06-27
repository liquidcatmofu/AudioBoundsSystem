package io.github.liquidcatmofu.abs.tts.provider;

import io.github.liquidcatmofu.abs.tts.TTSAddon;
import io.github.liquidcatmofu.abs.tts.api.SynthesisResult;
import io.github.liquidcatmofu.abs.tts.api.TTSProvider;
import io.github.liquidcatmofu.abs.tts.cache.TTSAudioCache;
import io.github.liquidcatmofu.abs.tts.config.TTSConfig;
import io.github.liquidcatmofu.abs.tts.transcode.FfmpegTranscoder;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public class VoiceVoxTTSProvider implements TTSProvider {
    private static final String ID = "voicevox";
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public boolean isAvailable() {
        try {
            String base = TTSConfig.get().voicevoxUrl();
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(base + "/version"))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();
            HttpResponse<Void> res = http.send(req, HttpResponse.BodyHandlers.discarding());
            return res.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public SynthesisResult synthesize(String text, String speakerId) throws IOException {
        if (TTSAudioCache.exists(speakerId, text)) {
            String cached = "tts/" + TTSAudioCache.resolve(speakerId, text).getFileName().toString();
            TTSAddon.LOGGER.info("ABS TTS: cache hit for speaker={} text={}", speakerId, text);
            return new SynthesisResult(cached);
        }

        String base = TTSConfig.get().voicevoxUrl();
        String encodedText = URLEncoder.encode(text, StandardCharsets.UTF_8);

        try {
            // Step 1: AudioQuery 取得
            String audioQueryJson = fetchAudioQuery(base, encodedText, speakerId);

            // Step 2: WAV 合成
            byte[] wavBytes = fetchSynthesis(base, speakerId, audioQueryJson);
            TTSAddon.LOGGER.info("ABS TTS: synthesized {} bytes WAV", wavBytes.length);

            // Step 3: WAV → Ogg 変換
            byte[] oggBytes = FfmpegTranscoder.toOgg(wavBytes, TTSConfig.get().ffmpegPath());

            // Step 4: キャッシュ保存
            String fileName = TTSAudioCache.save(oggBytes, speakerId, text);
            return new SynthesisResult(fileName);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("TTS synthesis interrupted", e);
        }
    }

    private String fetchAudioQuery(String base, String encodedText, String speakerId)
            throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(base + "/audio_query?text=" + encodedText + "&speaker=" + speakerId))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (res.statusCode() != 200) {
            throw new IOException("VOICEVOX /audio_query failed: HTTP " + res.statusCode() + " " + res.body());
        }
        return res.body();
    }

    private byte[] fetchSynthesis(String base, String speakerId, String audioQueryJson)
            throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(base + "/synthesis?speaker=" + speakerId))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json")
                .header("Accept", "audio/wav")
                .POST(HttpRequest.BodyPublishers.ofString(audioQueryJson, StandardCharsets.UTF_8))
                .build();

        HttpResponse<byte[]> res = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
        if (res.statusCode() != 200) {
            throw new IOException("VOICEVOX /synthesis failed: HTTP " + res.statusCode());
        }
        return res.body();
    }
}
