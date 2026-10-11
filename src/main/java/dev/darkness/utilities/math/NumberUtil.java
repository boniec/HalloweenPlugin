/*
 * Decompiled with CFR 0.152.
 */
package dev.darkness.utilities.math;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public final class NumberUtil {
    private static final DecimalFormat DF = new DecimalFormat("0.00", new DecimalFormatSymbols(Locale.US));

    private NumberUtil() {
    }

    public static int parseInt(String s, int def) {
        try {
            return Integer.parseInt(s.trim());
        }
        catch (Exception e) {
            return def;
        }
    }

    public static double parseDouble(String s, double def) {
        try {
            return Double.parseDouble(s.trim().replace(',', '.'));
        }
        catch (Exception e) {
            return def;
        }
    }

    public static String format2(double v) {
        return DF.format(v);
    }

    public static String formatCompact(double v) {
        if (v < 0.0) {
            return "-" + NumberUtil.formatCompact(-v);
        }
        if (v >= 1.0E9) {
            return DF.format(v / 1.0E9).replaceAll("\\.?0+$", "") + "B";
        }
        if (v >= 1000000.0) {
            return DF.format(v / 1000000.0).replaceAll("\\.?0+$", "") + "M";
        }
        if (v >= 1000.0) {
            return DF.format(v / 1000.0).replaceAll("\\.?0+$", "") + "K";
        }
        return DF.format(v).replaceAll("\\.?0+$", "");
    }

    public static int getRandom(int min, int max) {
        return (int)(Math.random() * (double)(max - min + 1) + (double)min);
    }

    static {
        DF.setRoundingMode(RoundingMode.HALF_UP);
    }
}
