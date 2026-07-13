package io.github.liquidcatmofu.abs.tts.transcode;

import java.io.IOException;

/**
 * 既存TTS呼び出しとの互換性を維持するアダプター。
 * プロセス管理、一時ファイル、タイムアウトはABS Coreの汎用変換サービスが担当する。
 */
public final class FfmpegTranscoder {
    private FfmpegTranscoder() {}

    /**
     * WAVバイト列をOgg Vorbisへ変換する。
     * ffmpegPathにはPATHで解決できる名前、または絶対パスを指定する。
     */
    public static byte[] toOgg(byte[] wavBytes, String ffmpegPath)
            throws IOException, InterruptedException {
        return io.github.liquidcatmofu.abs.audio.FfmpegTranscoder
                .toOgg(wavBytes, "wav", ffmpegPath);
    }
}
