package io.github.liquidcatmofu.abs.tts.api;

/** TTS 合成結果。cacheFileName は abs_cache/ からの相対パス（例: "tts/abc123.ogg"）。 */
public record SynthesisResult(String cacheFileName) {}
