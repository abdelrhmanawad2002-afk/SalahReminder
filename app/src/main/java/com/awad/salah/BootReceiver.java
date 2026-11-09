package com.awad.salah;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Re-arms alarms after reboot, app update, or time/time-zone change. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent intent) {
        Scheduler.scheduleAll(c);
    }
}
