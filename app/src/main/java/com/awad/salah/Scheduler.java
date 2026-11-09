package com.awad.salah;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.util.Calendar;

/** Sets the next prayer alarm and the next zikr alarm. Called after every alarm, on boot and on settings change. */
public final class Scheduler {
    private Scheduler() {}

    public static final String EXTRA_KIND = "kind";      // adhan | pre | zikr
    public static final String EXTRA_PRAYER = "prayer";  // fajr, dhuhr, ...
    private static final int RC_PRAYER = 101, RC_ZIKR = 102;

    public static void scheduleAll(Context c) {
        schedulePrayer(c);
        scheduleZikr(c, false);
    }

    private static PendingIntent pi(Context c, int rc, Intent i) {
        return PendingIntent.getBroadcast(c, rc, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static boolean canExact(AlarmManager am) {
        return Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms();
    }

    public static void schedulePrayer(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        Intent base = new Intent(c, AlarmReceiver.class);
        if (!Prefs.adhanOn(c)) { am.cancel(pi(c, RC_PRAYER, base)); return; }

        long now = System.currentTimeMillis();
        double[] ll = Prefs.coords(c);
        int before = Prefs.beforeMin(c);
        long bestAt = Long.MAX_VALUE;
        String bestKind = null, bestPrayer = null;
        for (int day = 0; day <= 1; day++) {
            long[] t = PrayerTimes.forDay(now, day, ll[0], ll[1]);
            for (int i = 0; i < t.length; i++) {
                if (i == 1) continue; // sunrise
                if (before > 0) {
                    long pre = t[i] - before * 60000L;
                    if (pre > now + 1000 && pre < bestAt) { bestAt = pre; bestKind = "pre"; bestPrayer = PrayerTimes.KEYS[i]; }
                }
                if (t[i] > now + 1000 && t[i] < bestAt) { bestAt = t[i]; bestKind = "adhan"; bestPrayer = PrayerTimes.KEYS[i]; }
            }
        }
        if (bestKind == null) return;
        Intent i = new Intent(c, AlarmReceiver.class)
                .putExtra(EXTRA_KIND, bestKind).putExtra(EXTRA_PRAYER, bestPrayer);
        PendingIntent p = pi(c, RC_PRAYER, i);
        if (canExact(am)) {
            Intent open = new Intent(c, MainActivity.class);
            PendingIntent show = PendingIntent.getActivity(c, 0, open, PendingIntent.FLAG_IMMUTABLE);
            am.setAlarmClock(new AlarmManager.AlarmClockInfo(bestAt, show), p);
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, bestAt, p);
        }
    }

    /** @param fromNow true right after a zikr fired, so the next one is a full interval later. */
    public static void scheduleZikr(Context c, boolean fromNow) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        Intent i = new Intent(c, AlarmReceiver.class).putExtra(EXTRA_KIND, "zikr");
        PendingIntent p = pi(c, RC_ZIKR, i);
        if (!Prefs.zikrOn(c)) { am.cancel(p); return; }

        long now = System.currentTimeMillis();
        long interval = Prefs.intervalMin(c) * 60000L;
        long next = Prefs.sp(c).getLong("nextZikrAt", 0);
        if (fromNow || next <= now + 1000 || next > now + interval) next = now + interval;
        next = skipQuiet(c, next);
        Prefs.sp(c).edit().putLong("nextZikrAt", next).apply();
        if (canExact(am)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, p);
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, p);
    }

    /** Quiet hours: from midnight until Fajr. */
    public static boolean inQuiet(Context c, long when) {
        if (!Prefs.quietNight(c)) return false;
        double[] ll = Prefs.coords(c);
        long fajr = PrayerTimes.forDay(when, 0, ll[0], ll[1])[0];
        return when < fajr; // between local midnight and today's Fajr
    }

    private static long skipQuiet(Context c, long when) {
        if (!inQuiet(c, when)) return when;
        double[] ll = Prefs.coords(c);
        return PrayerTimes.forDay(when, 0, ll[0], ll[1])[0] + 60000L; // a minute after Fajr
    }

    public static long nextZikrAt(Context c) { return Prefs.sp(c).getLong("nextZikrAt", 0); }

    public static long startOfToday() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }
}
