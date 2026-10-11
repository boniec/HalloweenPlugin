/*
 * Decompiled with CFR 0.152.
 */
package dev.darkness.utilities.time;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

public final class DateUtil {
    public static final ZoneId ZONE = ZoneId.of("Europe/Warsaw");
    private static final DateTimeFormatter DF = DateTimeFormatter.ofPattern("dd.MM.yyyy").withZone(ZONE);
    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss").withZone(ZONE);
    private static final DateTimeFormatter TF = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZONE);

    private DateUtil() {
    }

    public static ZonedDateTime now() {
        return ZonedDateTime.now(ZONE);
    }

    public static LocalDate today() {
        return LocalDate.now(ZONE);
    }

    public static long currentEpoch() {
        return System.currentTimeMillis();
    }

    public static String formatDate(ZonedDateTime dt) {
        return dt == null ? "" : dt.format(DF);
    }

    public static String formatDateTime(ZonedDateTime dt) {
        return dt == null ? "" : dt.format(DTF);
    }

    public static String formatTime(ZonedDateTime dt) {
        return dt == null ? "" : dt.format(TF);
    }

    public static String format(long epoch, boolean includeTime) {
        ZonedDateTime dt = DateUtil.fromEpoch(epoch);
        return includeTime ? DateUtil.formatDateTime(dt) : DateUtil.formatDate(dt);
    }

    public static ZonedDateTime parse(String val) {
        try {
            return LocalDateTime.parse(val, DTF).atZone(ZONE);
        }
        catch (Exception e) {
            return null;
        }
    }

    public static boolean isSameDay(ZonedDateTime a, ZonedDateTime b) {
        return a != null && b != null && a.toLocalDate().isEqual(b.toLocalDate());
    }

    public static long daysBetween(ZonedDateTime f, ZonedDateTime t) {
        return f == null || t == null ? 0L : ChronoUnit.DAYS.between(f.toLocalDate(), t.toLocalDate());
    }

    public static boolean isToday(long epoch) {
        return DateUtil.isSameDay(DateUtil.fromEpoch(epoch), DateUtil.now());
    }

    public static boolean isFuture(long epoch) {
        return epoch > System.currentTimeMillis();
    }

    public static boolean isPast(long epoch) {
        return epoch < System.currentTimeMillis();
    }

    public static long toEpoch(ZonedDateTime dt) {
        return dt == null ? 0L : dt.toInstant().toEpochMilli();
    }

    public static ZonedDateTime fromEpoch(long epoch) {
        return Instant.ofEpochMilli(epoch).atZone(ZONE);
    }
}
