package io.github.liquidcatmofu.abs.blockentity;

final class SubtitleDisplayText {
    private SubtitleDisplayText() {
    }

    static String trackTitle(String configuredTitle, String audioDisplayName) {
        String title = trim(configuredTitle);
        return title.isEmpty() ? trim(audioDisplayName) : title;
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
