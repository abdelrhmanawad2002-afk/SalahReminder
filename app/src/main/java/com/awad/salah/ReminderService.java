package com.awad.salah;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import java.util.Locale;

/** Shows the notification and plays the sound (chosen audio file, or Arabic text-to-speech). */
public class ReminderService extends Service {
    public static final String ACTION_STOP = "com.awad.salah.STOP";
    static final String CH_ADHAN = "adhan_v1", CH_ZIKR = "zikr_v1";

    private TextToSpeech tts;
    private MediaPlayer player;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean finished = false;

    @Override public IBinder onBind(Intent intent) { return null; }

    static void ensureChannels(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        NotificationChannel a = new NotificationChannel(CH_ADHAN, "مواعيد الأذان", NotificationManager.IMPORTANCE_HIGH);
        a.setSound(null, null);
        a.enableVibration(true);
        NotificationChannel z = new NotificationChannel(CH_ZIKR, "الأذكار والصلاة على النبي", NotificationManager.IMPORTANCE_DEFAULT);
        z.setSound(null, null);
        nm.createNotificationChannel(a);
        nm.createNotificationChannel(z);
    }

    static String[] texts(Context c, String kind, String prayer) {
        String name = prayer == null ? "" : PrayerTimes.nameOf(prayer);
        if ("adhan".equals(kind)) {
            return new String[]{"أذان " + name, "حان الآن موعد صلاة " + name + " حسب توقيت " + Prefs.CITY_NAMES[Prefs.city(c)],
                    "حان الآن موعد أذان " + name + ". الله أكبر، الله أكبر"};
        } else if ("pre".equals(kind)) {
            int m = Prefs.beforeMin(c);
            return new String[]{"قرّب " + name, "باقي " + m + " دقايق على أذان " + name,
                    "باقي " + m + " دقائق على أذان " + name};
        } else {
            String z = Prefs.randomZikr(c);
            return new String[]{"ذكر", z, z.replace("ﷺ", "صلى الله عليه وسلم")};
        }
    }

    static Notification build(Context c, String kind, String title, String body, boolean withStop) {
        ensureChannels(c);
        String ch = "zikr".equals(kind) ? CH_ZIKR : CH_ADHAN;
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, ch) : new Notification.Builder(c);
        PendingIntent open = PendingIntent.getActivity(c, 1, new Intent(c, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        b.setSmallIcon(R.drawable.ic_stat).setContentTitle(title).setContentText(body)
                .setStyle(new Notification.BigTextStyle().bigText(body))
                .setContentIntent(open).setAutoCancel(true).setShowWhen(true);
        if (Build.VERSION.SDK_INT < 26) b.setPriority("zikr".equals(kind) ? Notification.PRIORITY_DEFAULT : Notification.PRIORITY_HIGH);
        if (withStop) {
            PendingIntent stop = PendingIntent.getService(c, 2, new Intent(c, ReminderService.class).setAction(ACTION_STOP),
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            b.addAction(new Notification.Action.Builder(null, "إيقاف الصوت", stop).build());
        }
        return b.build();
    }

    static int idFor(String kind) { return "zikr".equals(kind) ? 2 : ("pre".equals(kind) ? 3 : 1); }

    /** Fallback when the service can't start: silent notification only. */
    static void postOnly(Context c, String kind, String prayer) {
        String[] t = texts(c, kind, prayer);
        try {
            c.getSystemService(NotificationManager.class).notify(idFor(kind), build(c, kind, t[0], t[1], false));
        } catch (Exception ignored) {}
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            finish();
            return START_NOT_STICKY;
        }
        String kind = intent == null ? "zikr" : intent.getStringExtra(Scheduler.EXTRA_KIND);
        if (kind == null) kind = "zikr";
        String prayer = intent == null ? null : intent.getStringExtra(Scheduler.EXTRA_PRAYER);
        String[] t = texts(this, kind, prayer);
        Notification n = build(this, kind, t[0], t[1], true);
        int id = idFor(kind);
        try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(id, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            else startForeground(id, n);
        } catch (Exception e) {
            postOnly(this, kind, prayer);
            stopSelf();
            return START_NOT_STICKY;
        }

        stopPlayback();
        finished = false;
        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(this::finish, "adhan".equals(kind) ? 6 * 60000L : 90000L);

        String custom = "adhan".equals(kind) ? Prefs.adhanSound(this) : ("zikr".equals(kind) ? Prefs.zikrSound(this) : null);
        int usage = "zikr".equals(kind) ? AudioAttributes.USAGE_NOTIFICATION : AudioAttributes.USAGE_ALARM;
        if (custom != null) playUri(Uri.parse(custom), usage, t[2]);
        else speak(t[2], usage);
        return START_NOT_STICKY;
    }

    private AudioAttributes attrs(int usage) {
        return new AudioAttributes.Builder().setUsage(usage)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build();
    }

    private void playUri(Uri uri, int usage, String fallbackText) {
        try {
            player = new MediaPlayer();
            player.setAudioAttributes(attrs(usage));
            player.setDataSource(this, uri);
            player.setOnCompletionListener(mp -> finish());
            player.setOnErrorListener((mp, w, x) -> { speak(fallbackText, usage); return true; });
            player.setOnPreparedListener(MediaPlayer::start);
            player.prepareAsync();
        } catch (Exception e) {
            speak(fallbackText, usage);
        }
    }

    private void speak(String text, int usage) {
        if (tts != null) { try { tts.shutdown(); } catch (Exception ignored) {} tts = null; }
        tts = new TextToSpeech(getApplicationContext(), status -> {
            if (status != TextToSpeech.SUCCESS || tts == null) { beep(usage); return; }
            int r = tts.setLanguage(new Locale("ar", "EG"));
            if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED)
                r = tts.setLanguage(new Locale("ar"));
            if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) { beep(usage); return; }
            tts.setSpeechRate(0.85f);
            tts.setAudioAttributes(attrs(usage));
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String id) {}
                @Override public void onDone(String id) { handler.post(ReminderService.this::finish); }
                @Override public void onError(String id) { handler.post(() -> beep(usage)); }
            });
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "salah");
        });
    }

    /** Last resort when no Arabic voice is installed: the phone's default notification tone. */
    private void beep(int usage) {
        try {
            stopPlayer();
            Uri u = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder().setUsage(usage)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
            player.setDataSource(this, u);
            player.setOnCompletionListener(mp -> finish());
            player.setOnErrorListener((mp, w, x) -> { finish(); return true; });
            player.setOnPreparedListener(MediaPlayer::start);
            player.prepareAsync();
        } catch (Exception e) {
            finish();
        }
    }

    private void stopPlayer() {
        if (player != null) { try { player.stop(); } catch (Exception ignored) {} try { player.release(); } catch (Exception ignored) {} player = null; }
    }

    private void stopPlayback() {
        stopPlayer();
        if (tts != null) { try { tts.stop(); tts.shutdown(); } catch (Exception ignored) {} tts = null; }
    }

    private void finish() {
        if (finished) return;
        finished = true;
        handler.removeCallbacksAndMessages(null);
        stopPlayback();
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_DETACH); else stopForeground(false);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        stopPlayback();
        super.onDestroy();
    }
}
