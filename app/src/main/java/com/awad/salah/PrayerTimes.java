package com.awad.salah;

import java.util.Calendar;
import java.util.TimeZone;

/** Prayer times using the Egyptian General Authority of Survey method (Fajr 19.5°, Isha 17.5°, Shafi'i Asr). */
public final class PrayerTimes {
    public static final String[] KEYS = {"fajr", "sunrise", "dhuhr", "asr", "maghrib", "isha"};
    public static final String[] NAMES = {"الفجر", "الشروق", "الظهر", "العصر", "المغرب", "العشاء"};

    private PrayerTimes() {}

    public static String nameOf(String key) {
        for (int i = 0; i < KEYS.length; i++) if (KEYS[i].equals(key)) return NAMES[i];
        return key;
    }

    private static double rad(double d) { return d * Math.PI / 180.0; }
    private static double deg(double r) { return r * 180.0 / Math.PI; }
    private static double fix(double a, double b) { a = a - b * Math.floor(a / b); return a < 0 ? a + b : a; }

    private static double julian(int y, int m, int d) {
        if (m <= 2) { y -= 1; m += 12; }
        double a = Math.floor(y / 100.0), b = 2 - a + Math.floor(a / 4.0);
        return Math.floor(365.25 * (y + 4716)) + Math.floor(30.6001 * (m + 1)) + d + b - 1524.5;
    }

    /** returns {declination, equationOfTime} */
    private static double[] sun(double jd) {
        double D = jd - 2451545.0;
        double g = fix(357.529 + 0.98560028 * D, 360);
        double q = fix(280.459 + 0.98564736 * D, 360);
        double L = fix(q + 1.915 * Math.sin(rad(g)) + 0.020 * Math.sin(rad(2 * g)), 360);
        double e = 23.439 - 0.00000036 * D;
        double ra = fix(deg(Math.atan2(Math.cos(rad(e)) * Math.sin(rad(L)), Math.cos(rad(L)))) / 15.0, 24);
        double decl = deg(Math.asin(Math.sin(rad(e)) * Math.sin(rad(L))));
        return new double[]{decl, q / 15.0 - ra};
    }

    private static double noon(double jd0, double t) { return fix(12 - sun(jd0 + t / 24.0)[1], 24); }

    private static double ha(double jd0, double lat, double angle, double t) {
        double dc = rad(sun(jd0 + t / 24.0)[0]), la = rad(lat);
        double v = (-Math.sin(rad(angle)) - Math.sin(dc) * Math.sin(la)) / (Math.cos(dc) * Math.cos(la));
        v = Math.max(-1, Math.min(1, v));
        return deg(Math.acos(v)) / 15.0;
    }

    private static double asrHa(double jd0, double lat, double t) {
        double decl = sun(jd0 + t / 24.0)[0];
        double alt = -deg(Math.atan(1.0 / (1.0 + Math.tan(Math.abs(rad(lat - decl))))));
        return ha(jd0, lat, alt, t);
    }

    /** Times (epoch millis) for the given calendar date, in KEYS order. */
    public static long[] compute(int y, int m, int d, double lat, double lng) {
        double jd0 = julian(y, m, d) - lng / (15.0 * 24.0);
        double[] T = {5, 6, 12, 13, 18, 18};
        for (int i = 0; i < 2; i++) {
            double[] n = new double[6];
            n[0] = noon(jd0, T[0] / 24) - ha(jd0, lat, 19.5, T[0] / 24);
            n[1] = noon(jd0, T[1] / 24) - ha(jd0, lat, 0.833, T[1] / 24);
            n[2] = noon(jd0, T[2] / 24);
            n[3] = noon(jd0, T[3] / 24) + asrHa(jd0, lat, T[3] / 24);
            n[4] = noon(jd0, T[4] / 24) + ha(jd0, lat, 0.833, T[4] / 24);
            n[5] = noon(jd0, T[5] / 24) + ha(jd0, lat, 17.5, T[5] / 24);
            T = n;
        }
        double[] adj = {0, 0, 1.0 / 60, 0, 1.0 / 60, 0};
        Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        utc.clear();
        utc.set(y, m - 1, d, 0, 0, 0);
        long base = utc.getTimeInMillis();
        long[] out = new long[6];
        for (int i = 0; i < 6; i++) {
            double h = T[i] - lng / 15.0 + adj[i];
            long ms = base + Math.round(h * 3600.0) * 1000L;
            out[i] = Math.round(ms / 60000.0) * 60000L;
        }
        return out;
    }

    /** Times for the local calendar day containing `when` (device time zone) plus `dayOffset` days. */
    public static long[] forDay(long when, int dayOffset, double lat, double lng) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(when);
        c.add(Calendar.DAY_OF_MONTH, dayOffset);
        return compute(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH), lat, lng);
    }
}
