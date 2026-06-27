package io.github.liquidcatmofu.abs.tts.api;

import io.github.liquidcatmofu.abs.ttsbridge.TTSParam;
import io.github.liquidcatmofu.abs.ttsbridge.TTSSpeaker;

import java.util.List;
import java.util.Map;

public interface TTSProvider {
    String getId();
    String getDisplayName();
    boolean isAvailable();

    /** 話者一覧（エンジン未起動時は空リストでよい）。 */
    List<TTSSpeaker> listSpeakers();

    /** GUI 生成用のパラメータスキーマ。 */
    List<TTSParam> paramSchema();

    /** テキストを合成し Ogg Vorbis のバイト列を返す。 */
    byte[] synthesizeToOgg(String text, String speakerId, Map<String, Double> params) throws Exception;
}
