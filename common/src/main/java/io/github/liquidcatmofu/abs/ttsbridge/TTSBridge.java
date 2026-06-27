package io.github.liquidcatmofu.abs.ttsbridge;

import java.util.List;

/**
 * common と tts-addon を繋ぐブリッジ。
 * tts-addon 側が実装を {@link TTSBridgeRegistry} に登録する。未導入なら登録されない。
 */
public interface TTSBridge {
    /** いずれかのエンジンが利用可能か（VOICEVOX 起動中など）。 */
    boolean isAvailable();

    /** 利用可能なエンジン一覧（話者・パラメータスキーマ込み）。 */
    List<TTSEngine> listEngines();

    /** 合成して Ogg Vorbis のバイト列を返す。 */
    byte[] synthesize(TTSSynthesisRequest request) throws Exception;
}
