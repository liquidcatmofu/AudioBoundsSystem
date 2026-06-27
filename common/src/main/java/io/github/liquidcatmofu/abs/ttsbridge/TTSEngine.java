package io.github.liquidcatmofu.abs.ttsbridge;

import java.util.ArrayList;
import java.util.List;

/** 1 つの TTS エンジン（話者一覧 + パラメータスキーマ）。 */
public class TTSEngine {
    public String id;
    public String name;
    public List<TTSSpeaker> speakers = new ArrayList<>();
    public List<TTSParam> params = new ArrayList<>();
}
