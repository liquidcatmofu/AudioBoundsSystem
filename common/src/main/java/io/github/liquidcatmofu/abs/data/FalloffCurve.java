package io.github.liquidcatmofu.abs.data;

public enum FalloffCurve {
    LINEAR,
    LOGARITHMIC,
    SMOOTH_STEP,
    INVERSE_SQUARE;

    public static FalloffCurve fromString(String name) {
        try {
            return valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return LOGARITHMIC;
        }
    }

    /** 距離 t (0.0 = 中心, 1.0 = 境界) に対するゲイン [0.0, 1.0] を返す */
    public double gain(double t) {
        t = Math.max(0.0, Math.min(1.0, t));
        return switch (this) {
            case LINEAR -> 1.0 - t;
            case LOGARITHMIC -> 1.0 - Math.log1p(t * (Math.E - 1)) / 1.0;
            case SMOOTH_STEP -> {
                double s = 1.0 - t;
                yield s * s * (3.0 - 2.0 * s);
            }
            case INVERSE_SQUARE -> 1.0 / (1.0 + t * t * 9.0);
        };
    }
}
