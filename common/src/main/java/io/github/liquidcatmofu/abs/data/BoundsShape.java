package io.github.liquidcatmofu.abs.data;

public enum BoundsShape {
    SPHERE,
    BOX,
    CYLINDER,
    HEMISPHERE;

    public static BoundsShape fromString(String name) {
        try {
            return valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return SPHERE;
        }
    }
}
