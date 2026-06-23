package io.github.liquidcatmofu.abs.data;

import net.minecraft.nbt.CompoundTag;

/**
 * スピーカーの音響エリアを定義するデータクラス。
 * 形状ごとに使用するパラメータが異なる:
 *   SPHERE      : radius
 *   BOX         : width, depth, height
 *   CYLINDER    : radius, height
 *   HEMISPHERE  : radius, height (ドーム高さ)
 */
public final class AudioBounds {
    public static final AudioBounds DEFAULT = new AudioBounds(BoundsShape.SPHERE, 16, 16, 16, 16);

    private static final String KEY_SHAPE  = "Shape";
    private static final String KEY_RADIUS = "Radius";
    private static final String KEY_WIDTH  = "Width";
    private static final String KEY_DEPTH  = "Depth";
    private static final String KEY_HEIGHT = "Height";

    private final BoundsShape shape;
    private final double radius;
    private final double width;
    private final double depth;
    private final double height;

    public AudioBounds(BoundsShape shape, double radius, double width, double depth, double height) {
        this.shape  = shape;
        this.radius = radius;
        this.width  = width;
        this.depth  = depth;
        this.height = height;
    }

    public BoundsShape getShape()  { return shape;  }
    public double      getRadius() { return radius; }
    public double      getWidth()  { return width;  }
    public double      getDepth()  { return depth;  }
    public double      getHeight() { return height; }

    /** 中心からの距離が境界の何割か (0.0〜1.0) を返す。境界外は 1.0 より大きい値になる */
    public double normalizedDistance(double dx, double dy, double dz) {
        return switch (shape) {
            case SPHERE -> Math.sqrt(dx * dx + dy * dy + dz * dz) / radius;
            case BOX    -> Math.max(Math.max(Math.abs(dx) / (width  / 2),
                                             Math.abs(dy) / (height / 2)),
                                             Math.abs(dz) / (depth  / 2));
            case CYLINDER -> Math.max(Math.sqrt(dx * dx + dz * dz) / radius,
                                      Math.abs(dy) / (height / 2));
            case HEMISPHERE -> {
                double horiz = Math.sqrt(dx * dx + dz * dz) / radius;
                double vert  = dy < 0 ? 1.0 : dy / height;
                yield Math.max(horiz, vert);
            }
        };
    }

    public void save(CompoundTag tag) {
        tag.putString(KEY_SHAPE,  shape.name());
        tag.putDouble(KEY_RADIUS, radius);
        tag.putDouble(KEY_WIDTH,  width);
        tag.putDouble(KEY_DEPTH,  depth);
        tag.putDouble(KEY_HEIGHT, height);
    }

    public static AudioBounds load(CompoundTag tag) {
        BoundsShape shape = BoundsShape.fromString(tag.getString(KEY_SHAPE));
        return new AudioBounds(
            shape,
            tag.getDouble(KEY_RADIUS),
            tag.getDouble(KEY_WIDTH),
            tag.getDouble(KEY_DEPTH),
            tag.getDouble(KEY_HEIGHT)
        );
    }
}
