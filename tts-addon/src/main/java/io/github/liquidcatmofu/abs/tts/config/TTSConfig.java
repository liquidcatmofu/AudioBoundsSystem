package io.github.liquidcatmofu.abs.tts.config;

/** TTS Addon 設定（Phase 4 で NightConfig 統合予定。現在はインメモリデフォルト）。 */
public record TTSConfig(String voicevoxUrl, String ffmpegPath) {
    // 接続先は 127.0.0.1 を使用（localhost は IPv6 ::1 に解決され、IPv4 のみ待受の VOICEVOX に繋がらないことがある）
    public static final TTSConfig DEFAULT = new TTSConfig("http://127.0.0.1:50021", "ffmpeg");

    private static TTSConfig instance = DEFAULT;

    public static TTSConfig get() {
        return instance;
    }

    public static void set(TTSConfig config) {
        instance = config;
    }
}
