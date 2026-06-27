package io.github.liquidcatmofu.abs.library;

import java.util.HashMap;
import java.util.Map;

/** フォルダ内の TTS セリフ。スクリプト（engine/speaker/text/params）を保持し再合成に備える。 */
public class TtsEntry {
    public String id;
    public String displayName;
    public String engineId;
    public String speakerId;
    public String speakerName;
    public String text;
    public Map<String, Double> params = new HashMap<>();
    public String cacheFile;      // abs_cache/ からの相対パス（SpeakerBlock が参照）
    public long   durationTicks;
    public String createdBy;
    public long   createdAt;
}
