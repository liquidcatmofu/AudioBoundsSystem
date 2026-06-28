package io.github.liquidcatmofu.abs.tts.config;

import java.util.Map;

/** TTS Addon 設定（Phase 4 で NightConfig 統合予定。現在はインメモリデフォルト）。 */
public record TTSConfig(String ffmpegPath, Map<String, String> engineUrls) {
    public static final TTSConfig DEFAULT = new TTSConfig("ffmpeg", Map.of());

    private static TTSConfig instance = DEFAULT;

    public static TTSConfig get() { return instance; }
    public static void set(TTSConfig config) { instance = config; }

    /**
     * エンジン固有の URL オーバーライドを返す。
     * 未設定の場合は null（プロバイダーが持つデフォルト URL を使用）。
     */
    public String engineUrl(String engineId) {
        return engineUrls != null ? engineUrls.get(engineId) : null;
    }
}
