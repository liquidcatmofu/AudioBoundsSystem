package io.github.liquidcatmofu.abs.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * サーバーからクライアントへ送信されるライブラリエントリのサマリー。
 * type は "audio" または "tts"。extra は TTS の場合に話者名を持つ。
 */
@Environment(EnvType.CLIENT)
public record LibraryEntryInfo(String id, String displayName, int durationTicks, String type, String extra) {
    public boolean isAudio() { return "audio".equals(type); }
    public boolean isTts()   { return "tts".equals(type);   }
}
