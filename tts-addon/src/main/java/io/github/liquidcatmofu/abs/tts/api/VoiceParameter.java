package io.github.liquidcatmofu.abs.tts.api;

/** GUI 動的生成用のパラメータスキーマ（Phase 4 で使用）。 */
public record VoiceParameter(String key, String type, Object defaultValue) {}
