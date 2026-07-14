package io.github.liquidcatmofu.abs.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AudioBoundsTest {
    private static final double EPSILON = 1.0e-9;

    @Test
    void computesNormalizedDistanceForEveryShape() {
        assertEquals(0.5, new AudioBounds(BoundsShape.SPHERE, 10, 1, 1, 1)
                .normalizedDistance(3, 4, 0), EPSILON);
        assertEquals(1.0, new AudioBounds(BoundsShape.BOX, 1, 20, 10, 8)
                .normalizedDistance(10, 0, 0), EPSILON);
        assertEquals(1.0, new AudioBounds(BoundsShape.CYLINDER, 5, 1, 1, 8)
                .normalizedDistance(3, 4, 4), EPSILON);
        assertEquals(0.5, new AudioBounds(BoundsShape.HEMISPHERE, 10, 1, 1, 8)
                .normalizedDistance(3, 4, 4), EPSILON);
    }

    @Test
    void hemisphereRejectsPointsBelowItsBase() {
        assertEquals(1.0, new AudioBounds(BoundsShape.HEMISPHERE, 10, 1, 1, 8)
                .normalizedDistance(0, -0.01, 0), EPSILON);
    }

    @Test
    void curvesStayWithinUnitGainAndDecrease() {
        for (FalloffCurve curve : FalloffCurve.values()) {
            double start = curve.gain(0.0);
            double middle = curve.gain(0.5);
            double end = curve.gain(1.0);
            assertEquals(1.0, start, EPSILON, curve.name());
            assertTrue(start >= middle, curve.name());
            assertTrue(middle >= end, curve.name());
            assertTrue(end >= 0.0 && end <= 1.0, curve.name());
        }
    }

    @Test
    void curveInputsAreClamped() {
        for (FalloffCurve curve : FalloffCurve.values()) {
            assertEquals(curve.gain(0.0), curve.gain(-10.0), EPSILON, curve.name());
            assertEquals(0.0, curve.gain(1.0), EPSILON, curve.name());
            assertEquals(0.0, curve.gain(10.0), EPSILON, curve.name());
        }
    }

    @Test
    void everyCurveIsSilentAtAndOutsideConfiguredBounds() {
        for (FalloffCurve curve : FalloffCurve.values()) {
            assertEquals(0.0, curve.gain(1.0), EPSILON, curve.name());
            assertEquals(0.0, curve.gain(1.01), EPSILON, curve.name());
        }
    }
}
