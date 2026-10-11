/*
 * Decompiled with CFR 0.152.
 */
package dev.darkness.utilities.time;

import java.util.concurrent.TimeUnit;

public final class TicksUtil {
    public static final int TICK_SECOND = 20;
    public static final int TICK_MINUTE = 1200;
    public static final int TICK_HOUR = 72000;

    private TicksUtil() {
    }

    public static long toTicks(long duration, TimeUnit unit) {
        return unit.toMillis(duration) * 20L / 1000L;
    }

    public static long secondsToTicks(double seconds) {
        return (long)(seconds * 20.0);
    }

    public static long minutesToTicks(double minutes) {
        return (long)(minutes * 1200.0);
    }

    public static String formatToSeconds(long ticks) {
        return String.format("%.1f", (double)ticks / 20.0);
    }

    public static double getProgress(long startTicks, int durationTicks, long currentTicks) {
        if (durationTicks <= 0) {
            return 1.0;
        }
        double progress = (double)(currentTicks - startTicks) / (double)durationTicks;
        return Math.min(1.0, Math.max(0.0, progress));
    }

    public static boolean hasElapsed(long startTicks, int requiredTicks, long currentTicks) {
        return currentTicks - startTicks >= (long)requiredTicks;
    }
}
