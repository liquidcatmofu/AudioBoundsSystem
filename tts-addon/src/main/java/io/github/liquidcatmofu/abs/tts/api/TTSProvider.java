package io.github.liquidcatmofu.abs.tts.api;

import java.io.IOException;

public interface TTSProvider {
    String getId();
    boolean isAvailable();
    /** テキストを合成し、abs_cache 直下からの相対パス（例: "tts/abc123.ogg"）を返す */
    SynthesisResult synthesize(String text, String speakerId) throws IOException;
}
