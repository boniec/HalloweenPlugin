/*
 * Decompiled with CFR 0.152.
 */
package dev.darkness.utilities.math;

public final class MathUtil {
    private MathUtil() {
    }

    public static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    public static double inverseLerp(double a, double b, double v) {
        if (a == b) {
            return 0.0;
        }
        return (v - a) / (b - a);
    }

    public static double map(double value, double inMin, double inMax, double outMin, double outMax) {
        return MathUtil.lerp(outMin, outMax, MathUtil.inverseLerp(inMin, inMax, value));
    }

    public static double roundTo(double value, int decimals) {
        if (decimals < 0) {
            throw new IllegalArgumentException("decimals < 0");
        }
        double pow = Math.pow(10.0, decimals);
        return (double)Math.round(value * pow) / pow;
    }

    public static double percent(double current, double max) {
        if (max == 0.0) {
            return 0.0;
        }
        return current / max * 100.0;
    }

    public static double percentNormalized(double current, double max) {
        if (max == 0.0) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, current / max));
    }

    public static boolean isEven(int value) {
        return (value & 1) == 0;
    }

    public static boolean isOdd(int value) {
        return (value & 1) != 0;
    }

    public static boolean inRange(double value, double min, double max) {
        return value >= min && value <= max;
    }

    public static boolean inRange(int value, int min, int max) {
        return value >= min && value <= max;
    }

    public static int ceilToMultiple(int value, int multiple) {
        if (multiple <= 0) {
            throw new IllegalArgumentException("multiple must be positive");
        }
        return (int)Math.ceil((double)value / (double)multiple) * multiple;
    }

    public static int floorToMultiple(int value, int multiple) {
        if (multiple <= 0) {
            throw new IllegalArgumentException("multiple must be positive");
        }
        return value / multiple * multiple;
    }

    public static double distance2D(double x1, double z1, double x2, double z2) {
        double dx = x2 - x1;
        double dz = z2 - z1;
        return Math.sqrt(dx * dx + dz * dz);
    }

    public static double distance3D(double x1, double y1, double z1, double x2, double y2, double z2) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double dz = z2 - z1;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
