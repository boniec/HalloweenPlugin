/*
 * Decompiled with CFR 0.152.
 */
package dev.darkness.utilities.time;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TimeUtil {
    private static final Pattern TIME_PATTERN = Pattern.compile("(?:(\\d+)d)?(?:(\\d+)[gh])?(?:(\\d+)m)?(?:(\\d+)s)?", 2);

    private TimeUtil() {
    }

    public static long parseMillis(String input) {
        if (input == null || input.isBlank()) {
            return 0L;
        }
        Matcher m = TIME_PATTERN.matcher(input.trim().toLowerCase());
        if (!m.matches()) {
            return 0L;
        }
        long totalSeconds = 0L;
        totalSeconds += TimeUtil.parseGroup(m.group(1)) * 86400L;
        totalSeconds += TimeUtil.parseGroup(m.group(2)) * 3600L;
        totalSeconds += TimeUtil.parseGroup(m.group(3)) * 60L;
        return (totalSeconds += TimeUtil.parseGroup(m.group(4))) * 1000L;
    }

    private static long parseGroup(String g) {
        return g != null && !g.isEmpty() ? Long.parseLong(g) : 0L;
    }

    public static String formatDuration(long ms) {
        String result;
        if (ms < 1000L) {
            return "0s";
        }
        long s = ms / 1000L;
        long d = s / 86400L;
        long h = s % 86400L / 3600L;
        long m = s % 3600L / 60L;
        long sec = s % 60L;
        StringBuilder sb = new StringBuilder();
        if (d > 0L) {
            sb.append(d).append("d ");
            if (h > 0L) {
                sb.append(h).append("h");
            }
        } else if (h > 0L) {
            sb.append(h).append("h ");
            if (m > 0L) {
                sb.append(m).append("m");
            }
        } else {
            if (m > 0L) {
                sb.append(m).append("m ");
            }
            if (sec > 0L) {
                sb.append(sec).append("s");
            }
        }
        return (result = sb.toString().trim()).isEmpty() ? "0s" : result;
    }

    public static String formatRemaining(long targetEpoch) {
        long diff = targetEpoch - System.currentTimeMillis();
        return diff <= 0L ? "teraz" : TimeUtil.formatDuration(diff);
    }

    public static LocalDateTime fromEpoch(long ms) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneId.systemDefault());
    }

    public static long toEpoch(LocalDateTime dt) {
        return dt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
