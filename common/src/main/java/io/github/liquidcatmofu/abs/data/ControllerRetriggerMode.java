package io.github.liquidcatmofu.abs.data;

public enum ControllerRetriggerMode {
    STOP,
    RESTART;

    public static ControllerRetriggerMode fromString(String name) {
        if (name == null) {
            return RESTART;
        }

        try {
            return valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return RESTART;
        }
    }
}
