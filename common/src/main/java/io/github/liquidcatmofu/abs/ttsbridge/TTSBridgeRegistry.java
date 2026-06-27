package io.github.liquidcatmofu.abs.ttsbridge;

/** tts-addon が実装を差し込むためのスロット。未導入時は null。 */
public final class TTSBridgeRegistry {
    private static volatile TTSBridge bridge;

    private TTSBridgeRegistry() {}

    public static void set(TTSBridge b) {
        bridge = b;
    }

    public static TTSBridge get() {
        return bridge;
    }

    public static boolean isPresent() {
        return bridge != null;
    }
}
