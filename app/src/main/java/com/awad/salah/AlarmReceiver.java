package com.awad.salah;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent intent) {
        String kind = intent.getStringExtra(Scheduler.EXTRA_KIND);
        String prayer = intent.getStringExtra(Scheduler.EXTRA_PRAYER);
        if (kind == null) return;

        boolean play = true;
        if ("zikr".equals(kind)) {
            if (!Prefs.zikrOn(c) || Scheduler.inQuiet(c, System.currentTimeMillis())) play = false;
            Scheduler.scheduleZikr(c, true);
        } else {
            Scheduler.schedulePrayer(c);
            if (!Prefs.adhanOn(c)) play = false;
            // after the adhan, push the next zikr back a bit so they don't overlap
            if ("adhan".equals(kind)) {
                long next = Scheduler.nextZikrAt(c);
                long min = System.currentTimeMillis() + 5 * 60000L;
                if (next < min) { Prefs.sp(c).edit().putLong("nextZikrAt", min).apply(); Scheduler.scheduleZikr(c, false); }
            }
        }
        if (!play) return;

        Intent s = new Intent(c, ReminderService.class)
                .putExtra(Scheduler.EXTRA_KIND, kind)
                .putExtra(Scheduler.EXTRA_PRAYER, prayer);
        try {
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(s); else c.startService(s);
        } catch (Exception e) {
            ReminderService.postOnly(c, kind, prayer);
        }
    }
}
