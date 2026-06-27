package io.github.liquidcatmofu.abs.ttsbridge;

/** TTS エンジンの話者（VOICEVOX のスタイル単位など）。 */
public class TTSSpeaker {
    public String id;
    public String name;

    public TTSSpeaker() {}

    public TTSSpeaker(String id, String name) {
        this.id = id;
        this.name = name;
    }
}
