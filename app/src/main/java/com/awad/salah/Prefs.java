package com.awad.salah;

import android.content.Context;
import android.content.SharedPreferences;

public final class Prefs {
    private Prefs() {}

    public static final String[] CITY_NAMES = {
            "القاهرة", "القاهرة الجديدة", "الجيزة", "6 أكتوبر", "العاشر من رمضان", "الإسكندرية",
            "طنطا", "المنصورة", "الزقازيق", "شبين الكوم", "بنها", "دمياط", "بورسعيد", "الإسماعيلية",
            "السويس", "الفيوم", "بني سويف", "المنيا", "أسيوط", "سوهاج", "قنا", "الأقصر", "أسوان",
            "الغردقة", "شرم الشيخ", "مرسى مطروح"};
    public static final double[][] CITY_COORDS = {
            {30.0444, 31.2357}, {30.0300, 31.4700}, {30.0131, 31.2089}, {29.9726, 30.9440},
            {30.2964, 31.7426}, {31.2001, 29.9187}, {30.7865, 31.0004}, {31.0409, 31.3785},
            {30.5877, 31.5020}, {30.5503, 31.0106}, {30.4660, 31.1858}, {31.4165, 31.8133},
            {31.2653, 32.3019}, {30.5965, 32.2715}, {29.9668, 32.5498}, {29.3084, 30.8428},
            {29.0661, 31.0994}, {28.1099, 30.7503}, {27.1783, 31.1859}, {26.5591, 31.6957},
            {26.1551, 32.7160}, {25.6872, 32.6396}, {24.0889, 32.8998}, {27.2579, 33.8116},
            {27.9158, 34.3299}, {31.3543, 27.2373}};

    public static final int[] INTERVALS = {5, 10, 15, 30, 60, 120};
    public static final String[] INTERVAL_LABELS = {"كل 5 دقايق", "كل 10 دقايق", "كل ربع ساعة", "كل نص ساعة", "كل ساعة", "كل ساعتين"};
    public static final int[] BEFORE = {0, 5, 10, 15};
    public static final String[] BEFORE_LABELS = {"من غير تنبيه قبل الأذان", "قبل الأذان بـ 5 دقايق", "قبل الأذان بـ 10 دقايق", "قبل الأذان بربع ساعة"};
    public static final String[] TYPE_LABELS = {"الصلاة على النبي ﷺ بس", "الصلاة على النبي + أذكار متنوعة"};

    public static final String[] SALAH = {
            "اللهم صلِّ وسلِّم وبارك على سيدنا محمد",
            "صلُّوا على النبي ﷺ",
            "اللهم صلِّ على محمد وعلى آل محمد كما صليت على إبراهيم وعلى آل إبراهيم إنك حميد مجيد"};
    public static final String[] AZKAR = {
            "سبحان الله وبحمده، سبحان الله العظيم",
            "لا إله إلا الله وحده لا شريك له، له الملك وله الحمد وهو على كل شيء قدير",
            "أستغفر الله العظيم وأتوب إليه",
            "لا حول ولا قوة إلا بالله العلي العظيم",
            "الحمد لله رب العالمين",
            "سبحان الله، والحمد لله، ولا إله إلا الله، والله أكبر",
            "حسبي الله لا إله إلا هو، عليه توكلت وهو رب العرش العظيم",
            "لا إله إلا أنت سبحانك إني كنت من الظالمين",
            "اللهم إنك عفوٌّ تحب العفو فاعفُ عني",
            "ربنا آتنا في الدنيا حسنة وفي الآخرة حسنة وقنا عذاب النار",
            "يا حي يا قيوم برحمتك أستغيث"};

    public static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("salah", Context.MODE_PRIVATE);
    }

    public static int city(Context c) { return clamp(sp(c).getInt("city", 1), CITY_NAMES.length); }
    public static double[] coords(Context c) { return CITY_COORDS[city(c)]; }
    public static int intervalIdx(Context c) { return clamp(sp(c).getInt("interval", 2), INTERVALS.length); }
    public static int intervalMin(Context c) { return INTERVALS[intervalIdx(c)]; }
    public static int beforeIdx(Context c) { return clamp(sp(c).getInt("before", 2), BEFORE.length); }
    public static int beforeMin(Context c) { return BEFORE[beforeIdx(c)]; }
    public static int type(Context c) { return clamp(sp(c).getInt("type", 1), 2); }
    public static boolean zikrOn(Context c) { return sp(c).getBoolean("zikrOn", true); }
    public static boolean adhanOn(Context c) { return sp(c).getBoolean("adhanOn", true); }
    public static boolean quietNight(Context c) { return sp(c).getBoolean("quietNight", true); }
    public static String adhanSound(Context c) { return sp(c).getString("adhanSound", null); }
    public static String zikrSound(Context c) { return sp(c).getString("zikrSound", null); }

    public static void putInt(Context c, String k, int v) { sp(c).edit().putInt(k, v).apply(); }
    public static void putBool(Context c, String k, boolean v) { sp(c).edit().putBoolean(k, v).apply(); }
    public static void putString(Context c, String k, String v) { sp(c).edit().putString(k, v).apply(); }

    private static int clamp(int i, int n) { return (i < 0 || i >= n) ? 0 : i; }

    public static String randomZikr(Context c) {
        int last = sp(c).getInt("lastZikr", -1);
        int total = type(c) == 0 ? SALAH.length : SALAH.length + AZKAR.length;
        int i;
        int guard = 0;
        do { i = (int) (Math.random() * total); } while (total > 1 && i == last && guard++ < 10);
        putInt(c, "lastZikr", i);
        return i < SALAH.length ? SALAH[i] : AZKAR[i - SALAH.length];
    }
}
