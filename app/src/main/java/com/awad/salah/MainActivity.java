package com.awad.salah;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int GREEN = 0xFF0F3D33, GOLD = 0xFFB8892B, BG = 0xFFF4F1E8, MUTED = 0xFF6B7B75, TEXT = 0xFF1C2A26;
    private static final int REQ_ADHAN = 11, REQ_ZIKR = 12;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Locale ar = new Locale("ar", "EG");
    private TextView dateTv, clockTv, nextNameTv, nextAtTv, countdownTv, zikrInfoTv, warnTv, soundsTv;
    private final TextView[] timeTvs = new TextView[6];
    private final LinearLayout[] timeRows = new LinearLayout[6];
    private long[] today, tomorrow;
    private String dayKey = "";

    private final Runnable tick = new Runnable() {
        @Override public void run() { refresh(); handler.postDelayed(this, 1000); }
    };

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (Build.VERSION.SDK_INT >= 21) getWindow().setStatusBarColor(GREEN);
        setContentView(buildUi());
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        Scheduler.scheduleAll(this);
    }

    @Override protected void onResume() { super.onResume(); dayKey = ""; Scheduler.scheduleAll(this); updateWarnings(); updateSoundsLabel(); handler.post(tick); }
    @Override protected void onPause() { super.onPause(); handler.removeCallbacks(tick); }

    // ---------------- UI ----------------
    private View buildUi() {
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        sv.addView(root);

        // header
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.VERTICAL);
        head.setGravity(Gravity.CENTER_HORIZONTAL);
        head.setBackgroundColor(GREEN);
        head.setPadding(dp(16), dp(20), dp(16), dp(22));
        TextView title = tv("مواعيد الصلاة", 26, 0xFFF1D78F, true);
        dateTv = tv("", 14, 0xFFCFE0DA, false);
        clockTv = tv("", 34, Color.WHITE, true);
        head.addView(title); head.addView(dateTv); head.addView(clockTv);
        root.addView(head);

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(14), dp(14), dp(14), dp(30));
        root.addView(body);

        warnTv = tv("", 14, 0xFF8A4B00, false);
        warnTv.setBackground(round(0xFFFFF1D6, 14));
        warnTv.setPadding(dp(14), dp(12), dp(14), dp(12));
        warnTv.setGravity(Gravity.START);
        warnTv.setOnClickListener(v -> fixPermissions());
        body.addView(warnTv, lp(dp(12)));

        // next prayer
        LinearLayout next = card();
        next.setGravity(Gravity.CENTER_HORIZONTAL);
        next.addView(tv("الصلاة القادمة", 14, MUTED, false));
        nextNameTv = tv("", 30, GOLD, true);
        nextAtTv = tv("", 14, MUTED, false);
        countdownTv = tv("", 32, TEXT, true);
        countdownTv.setTextDirection(View.TEXT_DIRECTION_LTR);
        next.addView(nextNameTv); next.addView(nextAtTv); next.addView(countdownTv);
        body.addView(next, lp(dp(12)));

        // times
        LinearLayout times = card();
        for (int i = 0; i < 6; i++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(dp(12), dp(10), dp(12), dp(10));
            TextView n = tv(PrayerTimes.NAMES[i], 18, TEXT, true);
            n.setGravity(Gravity.START);
            TextView t = tv("", 18, TEXT, true);
            t.setGravity(Gravity.END);
            row.addView(n, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            row.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            timeTvs[i] = t; timeRows[i] = row;
            times.addView(row);
        }
        body.addView(times, lp(dp(12)));

        // zikr
        LinearLayout z = card();
        z.addView(section("الأذكار والصلاة على النبي ﷺ"));
        zikrInfoTv = tv("", 14, MUTED, false);
        z.addView(zikrInfoTv);
        z.addView(check("تشغيل التذكير الدوري", Prefs.zikrOn(this), on -> { Prefs.putBool(this, "zikrOn", on); Scheduler.scheduleZikr(this, true); }));
        z.addView(spinner(Prefs.INTERVAL_LABELS, Prefs.intervalIdx(this), i -> { Prefs.putInt(this, "interval", i); Scheduler.scheduleZikr(this, true); }));
        z.addView(spinner(Prefs.TYPE_LABELS, Prefs.type(this), i -> Prefs.putInt(this, "type", i)));
        z.addView(check("سكوت بالليل (من 12 بالليل لحد الفجر)", Prefs.quietNight(this), on -> { Prefs.putBool(this, "quietNight", on); Scheduler.scheduleZikr(this, true); }));
        z.addView(button("🔊 جرّب الذكر", v -> test("zikr", null)));
        body.addView(z, lp(dp(12)));

        // adhan
        LinearLayout a = card();
        a.addView(section("تنبيه الأذان"));
        a.addView(check("تنبيه وقت الأذان", Prefs.adhanOn(this), on -> { Prefs.putBool(this, "adhanOn", on); Scheduler.schedulePrayer(this); }));
        a.addView(spinner(Prefs.BEFORE_LABELS, Prefs.beforeIdx(this), i -> { Prefs.putInt(this, "before", i); Scheduler.schedulePrayer(this); }));
        a.addView(spinner(Prefs.CITY_NAMES, Prefs.city(this), i -> { Prefs.putInt(this, "city", i); dayKey = ""; Scheduler.scheduleAll(this); }));
        a.addView(button("🔊 جرّب الأذان", v -> test("adhan", "dhuhr")));
        body.addView(a, lp(dp(12)));

        // sounds
        LinearLayout s = card();
        s.addView(section("الأصوات"));
        soundsTv = tv("", 13, MUTED, false);
        soundsTv.setGravity(Gravity.START);
        s.addView(soundsTv);
        s.addView(button("🎵 اختار ملف صوت للأذان", v -> pick(REQ_ADHAN)));
        s.addView(button("🎵 اختار ملف صوت للذكر (مثلاً صلِّ على النبي)", v -> pick(REQ_ZIKR)));
        s.addView(button("↺ رجّع للصوت الآلي", v -> {
            Prefs.putString(this, "adhanSound", null); Prefs.putString(this, "zikrSound", null); updateSoundsLabel();
        }));
        body.addView(s, lp(dp(12)));

        TextView note = tv("طريقة الحساب: الهيئة المصرية العامة للمساحة (الفجر 19.5° — العشاء 17.5° — العصر شافعي). المواعيد بتتحسب تلقائي كل يوم من غير إنترنت.", 12, MUTED, false);
        body.addView(note, lp(dp(4)));
        return sv;
    }

    private interface IntCb { void on(int i); }
    private interface BoolCb { void on(boolean b); }

    private TextView tv(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextSize(sp); t.setTextColor(color);
        t.setGravity(Gravity.CENTER_HORIZONTAL);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private TextView section(String s) {
        TextView t = tv(s, 17, GREEN, true);
        t.setGravity(Gravity.START);
        t.setPadding(0, 0, 0, dp(6));
        return t;
    }

    private GradientDrawable round(int color, int r) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color); g.setCornerRadius(dp(r));
        return g;
    }

    private LinearLayout card() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setBackground(round(Color.WHITE, 18));
        l.setPadding(dp(14), dp(14), dp(14), dp(14));
        if (Build.VERSION.SDK_INT >= 21) l.setElevation(dp(2));
        return l;
    }

    private LinearLayout.LayoutParams lp(int bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.bottomMargin = bottom;
        return p;
    }

    private CheckBox check(String label, boolean value, BoolCb cb) {
        CheckBox c = new CheckBox(this);
        c.setText(label); c.setTextSize(15); c.setTextColor(TEXT); c.setChecked(value);
        c.setPadding(dp(4), dp(8), dp(4), dp(8));
        c.setOnCheckedChangeListener((v, on) -> cb.on(on));
        return c;
    }

    private Spinner spinner(String[] items, int sel, IntCb cb) {
        Spinner sp = new Spinner(this);
        ArrayAdapter<String> ad = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, items);
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sp.setAdapter(ad);
        sp.setSelection(sel, false);
        sp.setBackground(round(0xFFF0EEE6, 12));
        sp.setPadding(dp(8), dp(6), dp(8), dp(6));
        final int[] current = {sel};
        sp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                if (pos != current[0]) { current[0] = pos; cb.on(pos); }
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(6); p.bottomMargin = dp(6);
        sp.setLayoutParams(p);
        return sp;
    }

    private Button button(String label, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(label); b.setAllCaps(false); b.setTextColor(Color.WHITE); b.setTextSize(15);
        b.setBackground(round(GREEN, 12));
        b.setOnClickListener(l);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(8);
        b.setLayoutParams(p);
        return b;
    }

    // ---------------- logic ----------------
    private void refresh() {
        long now = System.currentTimeMillis();
        double[] ll = Prefs.coords(this);
        String key = new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date(now)) + Prefs.city(this);
        if (!key.equals(dayKey)) {
            dayKey = key;
            today = PrayerTimes.forDay(now, 0, ll[0], ll[1]);
            tomorrow = PrayerTimes.forDay(now, 1, ll[0], ll[1]);
            SimpleDateFormat tf = new SimpleDateFormat("h:mm a", ar);
            for (int i = 0; i < 6; i++) timeTvs[i].setText(tf.format(new Date(today[i])));
            dateTv.setText(dateLine(now) + "  •  " + Prefs.CITY_NAMES[Prefs.city(this)]);
        }
        clockTv.setText(new SimpleDateFormat("h:mm:ss a", ar).format(new Date(now)));

        int nextIdx = -1; long nextAt = 0; boolean tmr = false;
        for (int i = 0; i < 6; i++) { if (i == 1) continue; if (today[i] > now) { nextIdx = i; nextAt = today[i]; break; } }
        if (nextIdx < 0) { nextIdx = 0; nextAt = tomorrow[0]; tmr = true; }
        nextNameTv.setText(PrayerTimes.NAMES[nextIdx]);
        nextAtTv.setText((tmr ? "بكرة " : "") + "الساعة " + new SimpleDateFormat("h:mm a", ar).format(new Date(nextAt)));
        countdownTv.setText(hms(nextAt - now));
        for (int i = 0; i < 6; i++) {
            boolean cur = !tmr && i == nextIdx;
            timeRows[i].setBackground(cur ? round(0x22B8892B, 12) : null);
            timeTvs[i].setTextColor(cur ? GOLD : (i == 1 ? MUTED : TEXT));
        }

        if (Prefs.zikrOn(this)) {
            long nz = Scheduler.nextZikrAt(this);
            zikrInfoTv.setText(nz > now ? "الذكر الجاي الساعة " + new SimpleDateFormat("h:mm a", ar).format(new Date(nz)) : "");
        } else zikrInfoTv.setText("التذكير متوقف");
    }

    private String dateLine(long now) {
        String g = new SimpleDateFormat("EEEE d MMMM yyyy", ar).format(new Date(now));
        try {
            android.icu.text.DateFormat h = android.icu.text.DateFormat.getDateInstance(
                    android.icu.text.DateFormat.LONG, new android.icu.util.ULocale("ar_EG@calendar=islamic-umalqura"));
            return g + "\n" + h.format(new Date(now));
        } catch (Throwable t) {
            return g;
        }
    }

    private static String hms(long ms) {
        long s = Math.max(0, ms / 1000);
        return String.format(Locale.US, "%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60);
    }

    private void test(String kind, String prayer) {
        Intent i = new Intent(this, ReminderService.class).putExtra(Scheduler.EXTRA_KIND, kind).putExtra(Scheduler.EXTRA_PRAYER, prayer);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
    }

    private void pick(int req) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("audio/*");
        try { startActivityForResult(i, req); } catch (Exception e) { Toast.makeText(this, "مفيش تطبيق ملفات", Toast.LENGTH_SHORT).show(); }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null || data.getData() == null) return;
        Uri u = data.getData();
        try { getContentResolver().takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (Exception ignored) {}
        Prefs.putString(this, req == REQ_ADHAN ? "adhanSound" : "zikrSound", u.toString());
        updateSoundsLabel();
        Toast.makeText(this, "تم ✓", Toast.LENGTH_SHORT).show();
    }

    private void updateSoundsLabel() {
        if (soundsTv == null) return;
        soundsTv.setText("الأذان: " + (Prefs.adhanSound(this) != null ? "ملف صوت اخترته" : "صوت آلي")
                + "\nالذكر: " + (Prefs.zikrSound(this) != null ? "ملف صوت اخترته" : "صوت آلي بيقرا الذكر"));
    }

    private boolean needsExact() {
        if (Build.VERSION.SDK_INT < 31) return false;
        AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        return !am.canScheduleExactAlarms();
    }

    private boolean needsBattery() {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        return !pm.isIgnoringBatteryOptimizations(getPackageName());
    }

    private void updateWarnings() {
        StringBuilder sb = new StringBuilder();
        if (needsExact()) sb.append("⚠️ لازم تسمح للتطبيق بالمنبّهات عشان الأذان ييجي في ميعاده بالظبط. دوس هنا.\n");
        if (needsBattery()) sb.append("⚠️ عشان التنبيهات تشتغل والموبايل مقفول، استثني التطبيق من توفير البطارية. دوس هنا.");
        String w = sb.toString().trim();
        warnTv.setText(w);
        warnTv.setVisibility(w.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void fixPermissions() {
        try {
            if (needsExact()) {
                startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + getPackageName())));
            } else if (needsBattery()) {
                startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getPackageName())));
            }
        } catch (Exception e) {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())));
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        Scheduler.scheduleAll(this);
    }
}
