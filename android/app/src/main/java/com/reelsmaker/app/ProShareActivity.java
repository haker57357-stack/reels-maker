package com.reelsmaker.app;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** مركز النشر: اختار الريل → اكتب/ولّد الوصف والهاشتاجات → المنصات بالترتيب بفاصل زمني → افتح وانشر بضغطة. + تذكيرات مجدولة. */
public class ProShareActivity extends Activity {
    String job = "", file = "";
    EditText title, tags;
    LinearLayout platBox, schedBox, headBox;
    int gapMin = 0;
    final Handler h = new Handler(Looper.getMainLooper());
    final Runnable tick = new Runnable() {
        @Override public void run() {
            buildPlats();
            h.postDelayed(this, 20000);
        }
    };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(ProKit.BG);
        getWindow().setNavigationBarColor(ProKit.BG);
        if (getIntent().getStringExtra("job") != null) job = getIntent().getStringExtra("job");
        if (getIntent().getStringExtra("file") != null) file = getIntent().getStringExtra("file");
        if (job.isEmpty() || file.isEmpty()) pickLatest();
        gapMin = ProKit.sp(this).getInt("gap", 0);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(ProKit.BG);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        LinearLayout hd = new LinearLayout(this);
        hd.setGravity(Gravity.CENTER_VERTICAL);
        ProKit.pad(hd, 14, 10, 14, 4, this);
        TextView back = ProKit.text(this, "›", 28, ProKit.ACC, true);
        ProKit.pad(back, 4, 0, 14, 0, this);
        back.setOnClickListener(v -> finish());
        hd.addView(back);
        hd.addView(ProKit.text(this, "مركز النشر والجدولة", 20, ProKit.TXT, true));
        root.addView(hd);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        ProKit.pad(col, 14, 4, 14, 16, this);
        headBox = new LinearLayout(this);
        headBox.setOrientation(LinearLayout.VERTICAL);
        col.addView(headBox);

        // الوصف
        LinearLayout tx = ProKit.card(this);
        tx.addView(ProKit.text(this, "✍️ العنوان والوصف والهاشتاجات", 14.5f, ProKit.TXT, true));
        title = ProKit.input(this, "عنوان/وصف الريل", ProKit.sp(this).getString("cap_" + job + file, ""), InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE, false);
        title.setMinLines(2);
        tags = ProKit.input(this, "#هاشتاجات", ProKit.sp(this).getString("tag_" + job + file, ""), InputType.TYPE_CLASS_TEXT, true);
        tx.addView(title, ProKit.lp(this, -1, -2, 0, 0, 6));
        tx.addView(tags, ProKit.lp(this, -1, -2, 0, 0, 6));
        Button gen = ProKit.soft(this, "✨ ولّد هاشتاجات ووصف (قواعد بسيطة)");
        gen.setOnClickListener(v -> generate());
        tx.addView(gen, ProKit.lp(this, -1, -2, 0, 0, 6));
        tx.addView(ProKit.cap(this, "فاصل بين كل منصة والتانية (عشان الحساب مايتعلّمش سبام)"));
        tx.addView(ProKit.chips(this, new String[]{"بدون", "2 د", "5 د", "10 د"}, new String[]{"0", "2", "5", "10"}, String.valueOf(gapMin), v -> {
            gapMin = Integer.parseInt(v);
            ProKit.sp(this).edit().putInt("gap", gapMin).apply();
            buildPlats();
        }));
        col.addView(tx, ProKit.lp(this, -1, -2, 0, 0, 10));

        LinearLayout pc = ProKit.card(this);
        pc.addView(ProKit.text(this, "📤 المنصات (بالترتيب)", 14.5f, ProKit.TXT, true));
        platBox = new LinearLayout(this);
        platBox.setOrientation(LinearLayout.VERTICAL);
        pc.addView(platBox);
        col.addView(pc, ProKit.lp(this, -1, -2, 0, 0, 10));

        LinearLayout sc = ProKit.card(this);
        sc.addView(ProKit.text(this, "⏰ جدولة تذكير", 14.5f, ProKit.TXT, true));
        sc.addView(ProKit.cap(this, "الإشعار بيفتحلك مركز النشر على الريل ده وقت ما تحدده. (النشر نفسه بيتم بإيدك.)"));
        LinearLayout qr = new LinearLayout(this);
        Button b1 = ProKit.soft(this, "بعد ساعة");
        b1.setOnClickListener(v -> addSched(System.currentTimeMillis() + 3600000L));
        Button b2 = ProKit.soft(this, "الليلة 9 م");
        b2.setOnClickListener(v -> addSched(at(0, 21)));
        Button b3 = ProKit.soft(this, "بكرة 10 ص");
        b3.setOnClickListener(v -> addSched(at(1, 10)));
        Button b4 = ProKit.soft(this, "وقت تاني...");
        b4.setOnClickListener(v -> pickTime(null));
        qr.addView(b1, ProKit.lp(this, 0, -2, 1f, 2, 6));
        qr.addView(b2, ProKit.lp(this, 0, -2, 1f, 2, 6));
        qr.addView(b3, ProKit.lp(this, 0, -2, 1f, 2, 6));
        qr.addView(b4, ProKit.lp(this, 0, -2, 1f, 2, 6));
        sc.addView(qr);
        schedBox = new LinearLayout(this);
        schedBox.setOrientation(LinearLayout.VERTICAL);
        sc.addView(schedBox);
        col.addView(sc, ProKit.lp(this, -1, -2, 0, 0, 10));

        ScrollView sv = new ScrollView(this);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        sv.addView(col);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);
    }

    @Override protected void onResume() {
        super.onResume();
        buildHead();
        buildPlats();
        buildSched();
        h.postDelayed(tick, 20000);
    }

    @Override protected void onPause() {
        h.removeCallbacks(tick);
        if (!job.isEmpty() && !file.isEmpty()) {
            ProKit.sp(this).edit().putString("cap_" + job + file, title.getText().toString())
                    .putString("tag_" + job + file, tags.getText().toString()).apply();
        }
        super.onPause();
    }

    /** لو مفيش ريل متحدد: آخر ريل اتعمل. */
    void pickLatest() {
        JSONArray hist = ProKit.arr(this, "hist");
        for (int i = 0; i < hist.length(); i++) {
            JSONObject o = hist.optJSONObject(i);
            if (o == null || !"done".equals(o.optString("status")) || o.optBoolean("preview") || o.optBoolean("gone")) continue;
            JSONArray f = o.optJSONArray("files");
            if (f == null) continue;
            for (int k = 0; k < f.length(); k++) {
                if (f.optString(k).endsWith(".mp4")) {
                    job = o.optString("job");
                    file = f.optString(k);
                    return;
                }
            }
        }
    }

    List<String[]> allReels() {
        List<String[]> out = new ArrayList<>();
        JSONArray hist = ProKit.arr(this, "hist");
        for (int i = 0; i < hist.length() && out.size() < 60; i++) {
            JSONObject o = hist.optJSONObject(i);
            if (o == null || !"done".equals(o.optString("status")) || o.optBoolean("preview") || o.optBoolean("gone")) continue;
            JSONArray f = o.optJSONArray("files");
            if (f == null) continue;
            for (int k = 0; k < f.length(); k++) {
                if (f.optString(k).endsWith(".mp4")) {
                    String t = o.optString("title");
                    out.add(new String[]{o.optString("job"), f.optString(k), (t.isEmpty() ? "ريل" : t) + " · " + f.optString(k)});
                }
            }
        }
        return out;
    }

    File reelFile() {
        return new File(ProKit.jobDir(this, job), file);
    }

    void buildHead() {
        headBox.removeAllViews();
        LinearLayout c = ProKit.card(this);
        if (job.isEmpty() || !reelFile().isFile()) {
            c.addView(ProKit.text(this, "مفيش ريل جاهز للنشر. اعمل ريلز من «إنشاء ريلز» الأول.", 13, ProKit.DIM, false));
        } else {
            c.addView(ProKit.text(this, "🎞 " + file, 15, ProKit.TXT, true));
            c.addView(ProKit.text(this, ProKit.bytes(reelFile().length()), 11.5f, ProKit.DIM, false));
            LinearLayout r = new LinearLayout(this);
            Button pl = ProKit.soft(this, "▶ شغّل");
            pl.setOnClickListener(v -> ProKit.play(this, reelFile()));
            Button sh = ProKit.soft(this, "↗ مشاركة النظام");
            sh.setOnClickListener(v -> systemShare());
            Button sv = ProKit.soft(this, "⬇ حفظ بالمعرض");
            sv.setOnClickListener(v -> saveToGallery(reelFile()));
            Button ch = ProKit.soft(this, "غيّر الريل");
            ch.setOnClickListener(v -> chooseReel());
            r.addView(pl, ProKit.lp(this, 0, -2, 1f, 2, 8));
            r.addView(sh, ProKit.lp(this, 0, -2, 1.3f, 2, 8));
            r.addView(sv, ProKit.lp(this, 0, -2, 1.3f, 2, 8));
            c.addView(r);
            c.addView(ch, ProKit.lp(this, -1, -2, 0, 0, 6));
        }
        headBox.addView(c, ProKit.lp(this, -1, -2, 0, 0, 6));
    }

    void chooseReel() {
        final List<String[]> all = allReels();
        if (all.isEmpty()) {
            ProKit.toast(this, "مفيش ريلز.");
            return;
        }
        String[] names = new String[all.size()];
        for (int i = 0; i < names.length; i++) names[i] = all.get(i)[2];
        ProKit.dlg(this).setTitle("اختار الريل").setItems(names, (d, i) -> {
            onPause();
            job = all.get(i)[0];
            file = all.get(i)[1];
            title.setText(ProKit.sp(this).getString("cap_" + job + file, ""));
            tags.setText(ProKit.sp(this).getString("tag_" + job + file, ""));
            buildHead();
            buildPlats();
        }).setNegativeButton("قفل", null).show();
    }

    void systemShare() {
        try {
            File src = reelFile();
            File dst = new File(ProProvider.shareDir(this), src.getName());
            copyFile(src, dst);
            Intent in = new Intent(Intent.ACTION_SEND);
            in.setType("video/mp4");
            in.putExtra(Intent.EXTRA_STREAM, ProProvider.uriFor(dst));
            in.putExtra(Intent.EXTRA_TEXT, (title.getText().toString() + "\n" + tags.getText().toString()).trim());
            in.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(in, "شارك الريل"));
        } catch (Exception e) {
            ProKit.toast(this, "مقدرتش أجهّز المشاركة.");
        }
    }

    static void copyFile(File src, File dst) throws Exception {
        try (InputStream in = new FileInputStream(src); OutputStream out = new java.io.FileOutputStream(dst)) {
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
    }

    void saveToGallery(File f) {
        if (Build.VERSION.SDK_INT < 29 && checkSelfPermission("android.permission.WRITE_EXTERNAL_STORAGE") != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.WRITE_EXTERNAL_STORAGE"}, 21);
            ProKit.toast(this, "اسمح بالتخزين ثم اضغط حفظ تاني.");
            return;
        }
        try {
            String name = "ReelsMaker_" + f.getName();
            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues cv = new ContentValues();
                cv.put(MediaStore.Video.Media.DISPLAY_NAME, name);
                cv.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
                cv.put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/ReelsMaker");
                Uri u = getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, cv);
                if (u == null) throw new Exception("no uri");
                try (OutputStream o = getContentResolver().openOutputStream(u); InputStream in = new FileInputStream(f)) {
                    byte[] buf = new byte[65536];
                    int n;
                    while ((n = in.read(buf)) > 0) o.write(buf, 0, n);
                }
            } else {
                File d = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "ReelsMaker");
                d.mkdirs();
                copyFile(f, new File(d, name));
            }
            ProKit.toast(this, "اتحفظ في Movies/ReelsMaker ✓");
        } catch (Exception e) {
            ProKit.toast(this, "مقدرتش أحفظ الملف.");
        }
    }

    // ───────── الوصف والهاشتاجات ─────────
    void generate() {
        String base = title.getText().toString().trim();
        if (base.isEmpty()) {
            ProKit.toast(this, "اكتب جملة عن الريل الأول (حتى كلمتين).");
            return;
        }
        List<String> ts = new ArrayList<>();
        Matcher m = Pattern.compile("[\\p{L}\\p{N}_]{3,}").matcher(base);
        while (m.find() && ts.size() < 5) {
            String w = m.group();
            if (!ts.contains("#" + w)) ts.add("#" + w);
        }
        for (String d : new String[]{"#ريلز", "#فيديو", "#اكسبلور"}) if (!ts.contains(d)) ts.add(d);
        StringBuilder sb = new StringBuilder();
        for (String t : ts) sb.append(t).append(' ');
        tags.setText(sb.toString().trim());
        if (!base.contains("\n")) title.setText(base + "\nتابعنا لمزيد من الفيديوهات 🎬");
    }

    /** وصف مضبوط على حدود كل منصة. */
    String captionFor(ProPlat p) {
        String t = title.getText().toString().trim();
        String tg = tags.getText().toString().trim();
        if ("youtube".equals(p.id)) {
            String ln = t.split("\n")[0].trim();
            if (ln.length() > 90) ln = ln.substring(0, 90);
            return (ln + " #Shorts").trim();
        }
        if ("instagram".equals(p.id)) tg = limitTags(tg, 30);
        else if ("linkedin".equals(p.id)) tg = limitTags(tg, 3);
        else if ("x".equals(p.id)) tg = limitTags(tg, 3);
        String full = (t + (tg.isEmpty() ? "" : "\n\n" + tg)).trim();
        if (full.length() > p.capMax) {
            int room = Math.max(0, p.capMax - (tg.isEmpty() ? 0 : tg.length() + 2));
            String cut = t.length() > room ? t.substring(0, Math.max(0, room - 1)) + "…" : t;
            full = (cut + (tg.isEmpty() ? "" : "\n\n" + tg)).trim();
            if (full.length() > p.capMax) full = full.substring(0, p.capMax);
        }
        return full;
    }

    String tagsFor(ProPlat p) {
        String tg = tags.getText().toString().trim();
        return "instagram".equals(p.id) ? limitTags(tg, 30) : tg;
    }

    static String limitTags(String tg, int n) {
        String[] a = tg.split("\\s+");
        StringBuilder sb = new StringBuilder();
        int c = 0;
        for (String s : a) {
            if (s.isEmpty()) continue;
            if (c++ >= n) break;
            sb.append(s).append(' ');
        }
        return sb.toString().trim();
    }

    // ───────── المنصات ─────────
    String state(String pid, String acc) {
        JSONArray p = ProKit.arr(this, "pubs");
        String st = "";
        for (int i = 0; i < p.length(); i++) {
            JSONObject o = p.optJSONObject(i);
            if (o != null && job.equals(o.optString("job")) && file.equals(o.optString("file")) && pid.equals(o.optString("pid")) && acc.equals(o.optString("acc"))) {
                st = o.optString("s");
            }
        }
        return st;
    }

    long lastDone() {
        JSONArray p = ProKit.arr(this, "pubs");
        long t = 0;
        for (int i = 0; i < p.length(); i++) {
            JSONObject o = p.optJSONObject(i);
            if (o != null && job.equals(o.optString("job")) && file.equals(o.optString("file")) && "done".equals(o.optString("s"))) t = Math.max(t, o.optLong("t"));
        }
        return t;
    }

    void buildPlats() {
        if (platBox == null) return;
        platBox.removeAllViews();
        long last = lastDone();
        long wait = last == 0 ? 0 : Math.max(0, last + gapMin * 60000L - System.currentTimeMillis());
        boolean any = false;
        for (ProPlat p : ProPlat.ALL) {
            final ProPlat pl = p;
            JSONArray accs = ProAccountsActivity.accounts(this, pl.id);
            for (int i = 0; i < accs.length(); i++) {
                final JSONObject a = accs.optJSONObject(i);
                if (a == null) continue;
                any = true;
                final String st = state(pl.id, a.optString("id"));
                LinearLayout r = new LinearLayout(this);
                r.setGravity(Gravity.CENTER_VERTICAL);
                ProKit.pad(r, 0, 7, 0, 7, this);
                r.addView(ProKit.iconChip(this, pl.icon, 34));
                LinearLayout c = new LinearLayout(this);
                c.setOrientation(LinearLayout.VERTICAL);
                ProKit.pad(c, 10, 0, 6, 0, this);
                c.addView(ProKit.text(this, pl.name + " — " + a.optString("name") + (a.optBoolean("locked") ? " 🔒" : ""), 13.5f, ProKit.TXT, true));
                String s2 = "done".equals(st) ? "✅ اتنشر" : "skip".equals(st) ? "⏭ اتخطّى" : "لسه";
                c.addView(ProKit.text(this, s2, 11, "done".equals(st) ? ProKit.OK : ProKit.DIM, false));
                r.addView(c, new LinearLayout.LayoutParams(0, -2, 1f));
                Button go = ProKit.pill(this, "done".equals(st) ? "أعد" : "افتح وانشر", "done".equals(st) ? ProKit.CARD2 : ProKit.ACC, "done".equals(st) ? ProKit.TXT : ProKit.ON_ACC);
                boolean blocked = a.optBoolean("locked") || job.isEmpty() || !reelFile().isFile();
                if (wait > 0 && !"done".equals(st)) {
                    go.setText("⏳ " + ((wait + 59999) / 60000) + " د");
                    blocked = true;
                }
                go.setEnabled(!blocked);
                go.setAlpha(blocked ? 0.5f : 1f);
                go.setOnClickListener(v -> openPublish(pl, a.optString("id")));
                r.addView(go);
                platBox.addView(r);
            }
        }
        if (!any) platBox.addView(ProKit.text(this, "مفيش حسابات لسه. سجّل دخولك من «المنصات والحسابات».", 12.5f, ProKit.DIM, false));
    }

    void openPublish(ProPlat p, String accId) {
        onPause();
        Intent in = new Intent(this, ProWebActivity.class).putExtra("pid", p.id).putExtra("acc", accId)
                .putExtra("job", job).putExtra("file", file)
                .putExtra("cap", captionFor(p)).putExtra("tags", tagsFor(p));
        startActivity(in);
    }

    // ───────── الجدولة ─────────
    static long at(int daysAhead, int hour) {
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_YEAR, daysAhead);
        c.set(Calendar.HOUR_OF_DAY, hour);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        if (c.getTimeInMillis() < System.currentTimeMillis()) c.add(Calendar.DAY_OF_YEAR, 1);
        return c.getTimeInMillis();
    }

    void pickTime(final Runnable unused) {
        final Calendar c = Calendar.getInstance();
        new DatePickerDialog(this, (dp, y, mo, d) -> new TimePickerDialog(this, (tp, hh, mm) -> {
            Calendar t = Calendar.getInstance();
            t.set(y, mo, d, hh, mm, 0);
            addSched(t.getTimeInMillis());
        }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true).show(), c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
    }

    void addSched(long t) {
        if (job.isEmpty() || file.isEmpty()) {
            ProKit.toast(this, "اختار ريل الأول.");
            return;
        }
        if (t <= System.currentTimeMillis()) {
            ProKit.toast(this, "الوقت ده عدّى.");
            return;
        }
        add(this, job, file, t);
        buildSched();
        ProKit.toast(this, "اتجدول التذكير ✓");
    }

    static void add(android.content.Context c, String job, String file, long t) {
        JSONObject s = new JSONObject();
        ProKit.put(s, "id", ProKit.newId());
        ProKit.put(s, "job", job);
        ProKit.put(s, "file", file);
        ProKit.put(s, "t", t);
        JSONArray a = ProKit.arr(c, "sched");
        a.put(s);
        ProKit.saveArr(c, "sched", a);
        ProService.channels(c);
        ProRemind.schedule(c, s);
    }

    /** جدولة كل ريلز العملية على أيام: perDay ريل في اليوم بمواعيد موزّعة. */
    static void scheduleBatch(android.content.Context c, String job, List<String> files, int perDay, long start) {
        int[] hrs = {10, 15, 20, 12, 17, 22};
        Calendar base = Calendar.getInstance();
        base.setTimeInMillis(start);
        for (int i = 0; i < files.size(); i++) {
            Calendar t = (Calendar) base.clone();
            t.add(Calendar.DAY_OF_YEAR, i / perDay);
            t.set(Calendar.HOUR_OF_DAY, hrs[(i % perDay) % hrs.length]);
            t.set(Calendar.MINUTE, 0);
            t.set(Calendar.SECOND, 0);
            if (t.getTimeInMillis() > System.currentTimeMillis()) add(c, job, files.get(i), t.getTimeInMillis());
        }
    }

    void buildSched() {
        schedBox.removeAllViews();
        JSONArray a = ProKit.arr(this, "sched");
        java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("EEE d/M HH:mm", new java.util.Locale("ar"));
        for (int i = 0; i < a.length(); i++) {
            final JSONObject s = a.optJSONObject(i);
            if (s == null) continue;
            LinearLayout r = new LinearLayout(this);
            r.setGravity(Gravity.CENTER_VERTICAL);
            ProKit.pad(r, 0, 6, 0, 0, this);
            r.addView(ProKit.text(this, f.format(new java.util.Date(s.optLong("t"))) + " — " + s.optString("file"), 12, ProKit.TXT, false), new LinearLayout.LayoutParams(0, -2, 1f));
            Button pp = ProKit.soft(this, "+يوم");
            pp.setOnClickListener(v -> editSched(s, 1));
            Button cc = ProKit.soft(this, "إلغاء");
            cc.setOnClickListener(v -> editSched(s, 0));
            r.addView(pp, ProKit.lp(this, -2, -2, 0, 2, 0));
            r.addView(cc, ProKit.lp(this, -2, -2, 0, 2, 0));
            schedBox.addView(r);
        }
    }

    void editSched(JSONObject s, int days) {
        ProRemind.cancel(this, s);
        JSONArray a = ProKit.arr(this, "sched");
        JSONArray n = new JSONArray();
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o == null) continue;
            if (o.optString("id").equals(s.optString("id"))) {
                if (days > 0) {
                    ProKit.put(o, "t", o.optLong("t") + days * 86400000L);
                    ProRemind.schedule(this, o);
                    n.put(o);
                }
            } else {
                n.put(o);
            }
        }
        ProKit.saveArr(this, "sched", n);
        buildSched();
    }
}
