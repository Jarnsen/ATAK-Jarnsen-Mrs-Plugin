package com.jarnsen.atak.mrs.plugin;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure-Java helpers kept separate from ATAK/Android so the core formatting
 * and geometry rules can be covered by fast JVM tests.
 */
final class MrsCoreLogic {

    static final double MAX_RANGE_M = 8000.0;
    static final double HALF_SECTOR_MIL = 600.0;
    static final double HALF_SECTOR_DEG =
            HALF_SECTOR_MIL * 360.0 / 6400.0;

    private static final Pattern MGRS_PATTERN = Pattern.compile(
            "^(\\d{1,2})([C-HJ-NP-X])([A-HJ-NP-Z]{2})(\\d{0,10})$"
    );

    private MrsCoreLogic() {
    }

    static String normalizeMgrsInput(String value) {
        if (value == null) {
            return "";
        }

        String compact = value.toUpperCase(Locale.US)
                .replaceAll("[^A-Z0-9]", "");

        Matcher matcher = MGRS_PATTERN.matcher(compact);
        if (!matcher.matches()) {
            return compact;
        }

        String zone = matcher.group(1);
        String band = matcher.group(2);
        String square = matcher.group(3);
        String digits = matcher.group(4);

        if ((digits.length() & 1) != 0) {
            return compact;
        }

        if (digits.isEmpty()) {
            return zone + band + " " + square;
        }

        int half = digits.length() / 2;
        return zone + band + " " + square + " "
                + digits.substring(0, half) + " "
                + digits.substring(half);
    }

    static boolean isStructurallyValidMgrs(String value) {
        String compact = value == null
                ? ""
                : value.toUpperCase(Locale.US)
                        .replaceAll("[^A-Z0-9]", "");
        Matcher matcher = MGRS_PATTERN.matcher(compact);
        if (!matcher.matches()) {
            return false;
        }
        String digits = matcher.group(4);
        return (digits.length() & 1) == 0;
    }

    static int degreesToMil(double degrees) {
        int mil = (int) Math.round(
                normalizeDegrees(degrees) * 6400.0 / 360.0
        );
        mil %= 6400;
        return mil < 0 ? mil + 6400 : mil;
    }

    static int snapMilToStep(int mil, int step) {
        if (step <= 0 || 6400 % step != 0) {
            throw new IllegalArgumentException(
                    "Mil step must be a positive divisor of 6400"
            );
        }
        int normalized = ((mil % 6400) + 6400) % 6400;
        return ((normalized + step / 2) / step * step) % 6400;
    }

    static double milToDegrees(int mil) {
        return normalizeDegrees(mil * 360.0 / 6400.0);
    }

    static double normalizeDegrees(double value) {
        double out = value % 360.0;
        return out < 0.0 ? out + 360.0 : out;
    }

    static double annotationLengthMeters(
            double textWidthPixels,
            double textSizePixels,
            double metersPerPixel) {
        if (!Double.isFinite(textWidthPixels)
                || !Double.isFinite(textSizePixels)
                || !Double.isFinite(metersPerPixel)
                || textWidthPixels < 0.0
                || textSizePixels <= 0.0
                || metersPerPixel <= 0.0) {
            return Double.POSITIVE_INFINITY;
        }
        return (textWidthPixels + 2.0 * textSizePixels) * metersPerPixel;
    }

    static boolean annotationFits(
            double availableMeters,
            double textWidthPixels,
            double textSizePixels,
            double metersPerPixel) {
        return Double.isFinite(availableMeters)
                && availableMeters >= 0.0
                && availableMeters >= annotationLengthMeters(
                        textWidthPixels,
                        textSizePixels,
                        metersPerPixel
                );
    }
}
