package io.github.liquidcatmofu.abs.tts.config;

/** TTS Addon 設定（Phase 4 で NightConfig 統合予定。現在はインメモリデフォルト）。 */
public record TTSConfig(String voicevoxUrl, String ffmpegPath) {
    public static final TTSConfig DEFAULT = new TTSConfig("http://localhost:50021", "ffmpeg");

    private static TTSConfig instance = DEFAULT;

    public static TTSConfig get() {
        return instance;
    }

    public static void set(TTSConfig config) {
        instance = config;
    }
}
