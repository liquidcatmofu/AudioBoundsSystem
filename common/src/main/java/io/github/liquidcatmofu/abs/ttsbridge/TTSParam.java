package io.github.liquidcatmofu.abs.ttsbridge;

/** GUI 動的生成用の数値パラメータスキーマ（速度・ピッチ・音量など）。 */
public class TTSParam {
    public String key;
    public String label;
    public double min;
    public double max;
    public double step;
    public double def;

    public TTSParam() {}

    public TTSParam(String key, String label, double min, double max, double step, double def) {
        this.key = key;
        this.label = label;
        this.min = min;
        this.max = max;
        this.step = step;
        this.def = def;
    }
}
