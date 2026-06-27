package io.github.liquidcatmofu.abs.ttsbridge;

import java.util.HashMap;
import java.util.Map;

/** 合成リクエスト。params のキーは TTSParam.key に対応する。 */
public class TTSSynthesisRequest {
    public String engineId;
    public String speakerId;
    public String text;
    public Map<String, Double> params = new HashMap<>();
}
