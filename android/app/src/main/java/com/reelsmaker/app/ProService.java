package com.reelsmaker.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;

/**
 * خدمة «ميديا برو» الخلفية: بتبعت الطلب للسيرفر (مع إعادة محاولة تلقائية) وبتتابع التقدم الحقيقي وبتنزّل الريلز أولًا بأول
 * (مع استكمال لو النت قطع) وبتكمّل حتى لو قفلت التطبيق. حالة العملية كلها محفوظة في SharedPreferences "pro".
 */
public class ProService extends Service {
    static volatile boolean alive = false;
    static final String CH_RUN = "pro_run", CH_DONE = "pro_done";
    static final int NID = 4101, NID_DONE = 4102;

    private SharedPreferences sp;
    private Thread worker;
    private PowerManager.WakeLock wake;

    @Override public IBinder onBind(Intent i) { return null; }

    @Override public int onStartCommand(Intent i, int flags, int startId) {
        sp = ProKit.sp(this);
        channels(this);
        startFg(sp.getString("r_msg", "بشتغل..."), sp.getInt("r_pct", 0));
        if (worker == null || !worker.isAlive()) {
            worker = new Thread(this::work, "pro-work");
            worker.start();
        }
        return START_STICKY;
    }

    static void channels(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        nm.createNotificationChannel(new NotificationChannel(CH_RUN, "متابعة ميديا برو", NotificationManager.IMPORTANCE_LOW));
        nm.createNotificationChannel(new NotificationChannel(CH_DONE, "نتيجة ميديا برو والتذكيرات", NotificationManager.IMPORTANCE_DEFAULT));
    }

    private Notification build(String msg, int pct, boolean ongoing, String chan) {
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, chan) : new Notification.Builder(this);
        b.setSmallIcon(ongoing ? android.R.drawable.stat_sys_download : android.R.drawable.stat_sys_download_done)
                .setContentTitle("✨ ميديا برو")
                .setContentText(msg)
                .setStyle(new Notification.BigTextStyle().bigText(msg))
                .setOngoing(ongoing)
                .setOnlyAlertOnce(true);
        if (ongoing) b.setProgress(100, pct, pct <= 0);
        Intent open = new Intent(this, ProActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        b.setContentIntent(PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        return b.build();
    }

    private void startFg(String msg, int pct) {
        Notification n = build(msg, pct, true, CH_RUN);
        if (Build.VERSION.SDK_INT >= 29) startForeground(NID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        else startForeground(NID, n);
    }

    private void progress(String msg, int pct) {
        sp.edit().putString("r_msg", msg).putInt("r_pct", pct).apply();
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NID, build(msg, pct, true, CH_RUN));
    }

    private boolean active() {
        String s = sp.getString("r_stage", "");
        return "NEW".equals(s) || "RUN".equals(s);
    }

    private void work() {
        alive = true;
        try {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm != null) {
                wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "pro:job");
                wake.acquire(90L * 60L * 1000L);
            }
            loop();
        } catch (Throwable t) {
            finish(false, "خطأ غير متوقع: " + t.getClass().getSimpleName(), null);
        } finally {
            try {
                if (wake != null && wake.isHeld()) wake.release();
            } catch (Throwable ignored) { }
            alive = false;
            stopForeground(true);
            stopSelf();
        }
    }

    private void loop() throws InterruptedException {
        if (!active()) return;
        String job = sp.getString("r_job", "");
        if (job.isEmpty()) return;
        if (Gh.TOK.isEmpty() || Gh.REPO.isEmpty()) {
            finish(false, "التطبيق اتبنى من غير بيانات الخدمة (التوكن/المستودع).", null);
            return;
        }
        if (!sp.getBoolean("r_sent", false)) {
            if (!dispatch(job)) return;
        }
        poll(job);
    }

    /** إرسال الطلب مع إعادة محاولة بتأخير متزايد لو النت/السيرفر مشغول. */
    private boolean dispatch(String job) throws InterruptedException {
        String cfg = sp.getString("r_cfg", "{}");
        for (int i = 0; i < 6; i++) {
            if (!active()) return false;
            try {
                progress("ببعت الطلب للسيرفر...", 1);
                ProNet.dispatch(job, cfg);
                sp.edit().putBoolean("r_sent", true).putString("r_stage", "RUN").putLong("r_sentt", System.currentTimeMillis()).apply();
                progress("السيرفر استلم الطلب ✅", 2);
                return true;
            } catch (Gh.Fatal f) {
                finish(false, f.getMessage(), null);
                return false;
            } catch (Exception e) {
                progress("النت أو السيرفر مشغول، بحاول تاني (" + (i + 1) + "/6)...", 1);
                Thread.sleep(3000L << Math.min(i, 4));
            }
        }
        sp.edit().putString("r_stage", "WAIT").putString("r_msg", "مقدرتش أبعت الطلب (النت ضعيف). اضغط «استكمال» لما النت يرجع.").apply();
        notifyDone("مقدرتش أبعت الطلب. افتح ميديا برو واضغط استكمال.");
        return false;
    }

    private void poll(String job) throws InterruptedException {
        long t0 = sp.getLong("r_t0", System.currentTimeMillis());
        long sent = sp.getLong("r_sentt", System.currentTimeMillis());
        int errs = 0, missing = 0;
        long lastRun = 0;
        while (active()) {
            if (System.currentTimeMillis() - t0 > 80L * 60L * 1000L) {
                finish(false, "العملية خدت وقت طويل جدًا (أكتر من 80 دقيقة).", null);
                return;
            }
            try {
                JSONObject rel = ProNet.release(job);
                errs = 0;
                if (rel != null) {
                    JSONObject st = ProNet.status(rel);
                    String stage = st.optString("stage", "run");
                    progress(st.optString("msg", "السيرفر شغّال..."), st.optInt("pct", 0));
                    downloadNew(job, rel);
                    if ("done".equals(stage) || "fail".equals(stage)) {
                        boolean ok = "done".equals(stage);
                        if (ok && filesMissing(job, st) && missing < 6) {
                            missing++;
                        } else {
                            finish(ok, st.optString("msg", ok ? "خلصنا" : "فشلت العملية"), st);
                            return;
                        }
                    }
                } else if (System.currentTimeMillis() - lastRun > 20000L) {
                    lastRun = System.currentTimeMillis();
                    JSONObject run = ProNet.findRun(job);
                    long now = System.currentTimeMillis();
                    if (run == null) {
                        if (now - sent > 5L * 60L * 1000L) {
                            finish(false, "السيرفر ماشغّلش العملية. اتأكد إن ملف pro.yml موجود وإن التوكن بصلاحية Actions: Read and write.", null);
                            return;
                        }
                        progress("في انتظار السيرفر...", 2);
                    } else if ("completed".equals(run.optString("status")) && !"success".equals(run.optString("conclusion"))) {
                        finish(false, "السيرفر وقف قبل ما ينتهي (" + run.optString("conclusion") + "). جرّب تاني أو افتح صفحة الفحص.", null);
                        return;
                    } else {
                        progress("السيرفر بيجهّز الأدوات...", 3);
                    }
                }
            } catch (Gh.Fatal f) {
                finish(false, f.getMessage(), null);
                return;
            } catch (Exception e) {
                errs++;
                progress("النت ضعيف — بكمّل أول ما يرجع (" + errs + ")", sp.getInt("r_pct", 0));
                if (errs >= 45) {
                    sp.edit().putString("r_stage", "WAIT").putString("r_msg", "النت قطع. اضغط «استكمال» لما يرجع، والسيرفر بيكمّل لوحده.").apply();
                    notifyDone("النت قطع. افتح ميديا برو واضغط استكمال.");
                    return;
                }
            }
            Thread.sleep(errs > 0 ? Math.min(60000L, 5000L + errs * 3000L) : 7000L);
        }
    }

    private boolean filesMissing(String job, JSONObject st) {
        JSONArray f = st.optJSONArray("files");
        if (f == null) return false;
        File d = ProKit.jobDir(this, job);
        for (int i = 0; i < f.length(); i++) {
            if (!new File(d, f.optString(i)).isFile()) return true;
        }
        return false;
    }

    private static boolean wanted(String n) {
        return n.matches("reel_\\d+\\.mp4") || n.matches("cover_\\d+\\.jpg") || n.equals("preview.mp4");
    }

    /** بينزّل أي ملف جديد ظهر في الإصدار (أول ريلز بتنزل وباقي الشغل لسه شغّال). */
    private void downloadNew(String job, JSONObject rel) throws IOException {
        JSONArray a = rel.optJSONArray("assets");
        if (a == null) return;
        File dir = ProKit.jobDir(this, job);
        for (int i = 0; i < a.length(); i++) {
            JSONObject as = a.optJSONObject(i);
            if (as == null) continue;
            String name = as.optString("name");
            if (!wanted(name) || !"uploaded".equals(as.optString("state", "uploaded"))) continue;
            File f = new File(dir, name);
            if (f.isFile()) continue;
            final String shown = name;
            final int base = sp.getInt("r_pct", 0);
            progress("بنزّل " + shown + "...", base);
            ProNet.download(as.optString("url"), f, as.optLong("size", 0), null);
        }
    }

    private void finish(boolean ok, String msg, JSONObject st) {
        String job = sp.getString("r_job", "");
        long t0 = sp.getLong("r_t0", System.currentTimeMillis());
        JSONObject h = new JSONObject();
        ProKit.put(h, "job", job);
        ProKit.put(h, "t", System.currentTimeMillis());
        ProKit.put(h, "status", ok ? "done" : "fail");
        ProKit.put(h, "msg", msg);
        ProKit.put(h, "title", sp.getString("r_title", ""));
        ProKit.put(h, "project", sp.getString("r_proj", ""));
        ProKit.put(h, "preview", sp.getBoolean("r_prev", false));
        ProKit.put(h, "cfg", sp.getString("r_cfg", "{}"));
        double mins = st != null ? st.optDouble("mins", (System.currentTimeMillis() - t0) / 60000.0) : (System.currentTimeMillis() - t0) / 60000.0;
        ProKit.put(h, "mins", Math.round(mins * 10) / 10.0);
        JSONArray files = new JSONArray();
        File[] fs = ProKit.jobDir(this, job).listFiles();
        if (fs != null) {
            java.util.Arrays.sort(fs);
            for (File f : fs) if (f.isFile() && wanted(f.getName())) files.put(f.getName());
        }
        ProKit.put(h, "files", files);
        ProKit.put(h, "warn", st != null && st.optJSONArray("warn") != null ? st.optJSONArray("warn") : new JSONArray());
        JSONArray hist = ProKit.arr(this, "hist");
        JSONArray nh = new JSONArray();
        nh.put(h);
        for (int i = 0; i < hist.length() && nh.length() < 200; i++) {
            JSONObject o = hist.optJSONObject(i);
            if (o != null && !job.equals(o.optString("job"))) nh.put(o);
        }
        ProKit.saveArr(this, "hist", nh);
        boolean prev = sp.getBoolean("r_prev", false);
        sp.edit().putString("r_stage", ok ? "DONE" : "FAIL").putString("r_msg", msg).putInt("r_pct", ok ? 100 : sp.getInt("r_pct", 0)).apply();
        notifyDone(ok ? (prev ? "المعاينة جاهزة ✅" : "الريلز جاهزة ✅ " + msg) : "❌ " + msg);
        cleanup(this);
    }

    private void notifyDone(String msg) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NID_DONE, build(msg, 0, false, CH_DONE));
    }

    /** تنظيف المساحة تلقائي: بيمسح ملفات العمليات الأقدم من المدة اللي حددتها (0 = ماتمسحش). */
    static void cleanup(Context c) {
        try {
            int days = ProKit.sp(c).getInt("keep_days", 14);
            if (days <= 0) return;
            long limit = System.currentTimeMillis() - days * 86400000L;
            JSONArray h = ProKit.arr(c, "hist");
            boolean ch = false;
            for (int i = 0; i < h.length(); i++) {
                JSONObject o = h.optJSONObject(i);
                if (o == null || o.optBoolean("gone") || o.optBoolean("keep") || o.optLong("t") > limit) continue;
                File d = ProKit.jobDir(c, o.optString("job"));
                File[] fs = d.listFiles();
                if (fs != null) for (File f : fs) f.delete();
                d.delete();
                ProKit.put(o, "gone", true);
                ch = true;
            }
            if (ch) ProKit.saveArr(c, "hist", h);
        } catch (Throwable ignored) { }
    }
}
