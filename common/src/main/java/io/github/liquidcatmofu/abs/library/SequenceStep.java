package io.github.liquidcatmofu.abs.library;

/** 再生シーケンスの1ステップ。audioRef は abs_cache/ 内のファイル名。 */
public class SequenceStep {
    public String audioRef;    // AudioEntry.cacheFile または TtsEntry.cacheFile
    public int    delayTicks;  // 前ステップ終了後の待機（0 = 即時）
    public String label;
}
