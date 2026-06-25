package io.github.liquidcatmofu.abs.data;

public enum RedstoneMode {
    LEVEL,
    PULSE;

    public static RedstoneMode fromString(String name) {
        if (name == null) {
            return LEVEL;
        }

        try {
            return valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return LEVEL;
        }
    }
}
