package io.github.liquidcatmofu.abs.library;

/** フォルダ内の音声ファイルメタデータ。実体 ogg は abs_cache/<cacheFile> に置かれる。 */
public class AudioEntry {
    public String id;
    public String displayName;
    public String originalName;
    public String srcFile;        // abs_library/<folder>/audio/ 内の原本ファイル名
    public String cacheFile;      // abs_cache/ からの相対パス（SpeakerBlock が参照）
    public long   durationTicks;
    public double volumeDb = 0.0; // 将来の音量編集用
    public long   trimStartMs = 0;   // 将来のトリム編集用
    public long   trimEndMs = -1;    // -1 = 末尾まで
    public String uploadedBy;
    public long   uploadedAt;
}
