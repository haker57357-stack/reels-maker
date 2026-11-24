package com.reelsmaker.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/** السجل والمشاريع والإحصائيات: كل عملية بريلزها وتنبيهاتها + إعادة تشغيل بنفس الإعدادات + جدولة + تنظيف المساحة. */
public class ProLibActivity extends Activity {
    LinearLayout list;
    String filter = "__all__";
    String focus = "";

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(ProKit.BG);
        getWindow().setNavigationBarColor(ProKit.BG);
        if (getIntent().getStringExtra("job") != null) focus = getIntent().getStringExtra("job");
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
        hd.addView(ProKit.text(this, "السجل والمشاريع", 20, ProKit.TXT, true));
        root.addView(hd);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        ProKit.pad(list, 14, 4, 14, 16, this);
        ScrollView sv = new ScrollView(this);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        sv.addView(list);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);
    }

    @Override protected void onResume() {
        super.onResume();
        build();
    }

    static long dirSize(File d) {
        long s = 0;
        File[] fs = d.listFiles();
        if (fs != null) for (File f : fs) s += f.isFile() ? f.length() : dirSize(f);
        return s;
    }

    void build() {
        list.removeAllViews();
        JSONArray hist = ProKit.arr(this, "hist");

        // ───── إحصائيات ─────
        Calendar now = Calendar.getInstance();
        int made = 0, fail = 0;
        double mins = 0;
        for (int i = 0; i < hist.length(); i++) {
            JSONObject o = hist.optJSONObject(i);
            if (o == null) continue;
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(o.optLong("t"));
            boolean month = c.get(Calendar.YEAR) == now.get(Calendar.YEAR) && c.get(Calendar.MONTH) == now.get(Calendar.MONTH);
            if (month) mins += o.optDouble("mins", 0);
            if ("done".equals(o.optString("status")) && !o.optBoolean("preview")) {
                JSONArray f = o.optJSONArray("files");
                if (f != null) for (int k = 0; k < f.length(); k++) if (f.optString(k).endsWith(".mp4")) made++;
            } else if ("fail".equals(o.optString("status"))) {
                fail++;
            }
        }
        JSONArray pubs = ProKit.arr(this, "pubs");
        int posted = 0;
        for (int i = 0; i < pubs.length(); i++) {
            JSONObject o = pubs.optJSONObject(i);
            if (o != null && "done".equals(o.optString("s"))) posted++;
        }
        LinearLayout st = ProKit.card(this);
        st.addView(ProKit.text(this, "📊 إحصائيات", 14.5f, ProKit.TXT, true));
        st.addView(ProKit.text(this, "ريلز اتعملت: " + made + "   •   اتنشرت: " + posted + "   •   عمليات فشلت: " + fail, 12.5f, ProKit.TXT, false));
        st.addView(ProKit.text(this, "دقايق السيرفر الشهر ده: " + Math.round(mins) + " د   •   المساحة: " + ProKit.bytes(dirSize(ProKit.dir(this))), 12, ProKit.DIM, false));
        st.addView(ProKit.cap(this, "تنظيف تلقائي لملفات العمليات الأقدم من:"));
        int kd = ProKit.sp(this).getInt("keep_days", 14);
        st.addView(ProKit.chips(this, new String[]{"3 أيام", "7 أيام", "14 يوم", "30 يوم", "ماتمسحش"}, new String[]{"3", "7", "14", "30", "0"},
                String.valueOf(kd), v -> ProKit.sp(this).edit().putInt("keep_days", Integer.parseInt(v)).apply()));
        Button clean = ProKit.soft(this, "♻️ نظّف دلوقتي");
        clean.setOnClickListener(v -> {
            ProService.cleanup(this);
            ProKit.toast(this, "اتنضّفت.");
            build();
        });
        st.addView(clean, ProKit.lp(this, -1, -2, 0, 0, 6));
        list.addView(st, ProKit.lp(this, -1, -2, 0, 0, 6));

        // ───── فلتر المشاريع ─────
        JSONArray ps = ProKit.arr(this, "projects");
        List<String> labels = new ArrayList<>(), vals = new ArrayList<>();
        labels.add("الكل");
        vals.add("__all__");
        for (int i = 0; i < ps.length(); i++) {
            labels.add(ps.optString(i));
            vals.add(ps.optString(i));
        }
        if (labels.size() > 1) {
            list.addView(ProKit.chips(this, labels.toArray(new String[0]), vals.toArray(new String[0]), filter, v -> {
                filter = v;
                build();
            }), ProKit.lp(this, -1, -2, 0, 0, 8));
        }

        int shown = 0;
        for (int i = 0; i < hist.length(); i++) {
            JSONObject o = hist.optJSONObject(i);
            if (o == null) continue;
            if (!"__all__".equals(filter) && !filter.equals(o.optString("project"))) continue;
            list.addView(jobCard(o), ProKit.lp(this, -1, -2, 0, 0, 10));
            shown++;
        }
        if (shown == 0) list.addView(ProKit.text(this, "\nمفيش عمليات لسه.", 13, ProKit.DIM, false));
    }

    View jobCard(final JSONObject o) {
        final String job = o.optString("job");
        boolean ok = "done".equals(o.optString("status"));
        LinearLayout c = ProKit.card(this);
        if (job.equals(focus)) c.setBackground(ProKit.shape(this, ProKit.CARD, 20, ProKit.ACC, 2));
        String t = o.optString("title");
        c.addView(ProKit.text(this, (ok ? "✅ " : "❌ ") + (t.isEmpty() ? "عملية" : t) + (o.optBoolean("preview") ? "  (معاينة)" : ""), 14.5f, ProKit.TXT, true));
        String pr = o.optString("project");
        c.addView(ProKit.text(this, "من " + ProKit.ago(o.optLong("t")) + (pr.isEmpty() ? "" : "  •  مشروع: " + pr) + "  •  " + o.optDouble("mins", 0) + " د سيرفر", 11.5f, ProKit.DIM, false));
        if (!ok) c.addView(ProKit.text(this, o.optString("msg"), 12, ProKit.BAD, false));
        JSONArray w = o.optJSONArray("warn");
        if (w != null) for (int i = 0; i < w.length(); i++) c.addView(ProKit.text(this, "⚠️ " + w.optString(i), 11.5f, ProKit.WARN, false));
        if (o.optBoolean("gone")) {
            c.addView(ProKit.text(this, "♻️ الملفات اتمسحت (تنظيف تلقائي).", 11.5f, ProKit.DIM, false));
        } else {
            final List<String> reels = new ArrayList<>();
            JSONArray f = o.optJSONArray("files");
            if (f != null) for (int i = 0; i < f.length(); i++) if (f.optString(i).endsWith(".mp4")) reels.add(f.optString(i));
            for (final String r : reels) {
                LinearLayout row = new LinearLayout(this);
                row.setGravity(Gravity.CENTER_VERTICAL);
                ProKit.pad(row, 0, 6, 0, 0, this);
                File rf = new File(ProKit.jobDir(this, job), r);
                row.addView(ProKit.text(this, "🎞 " + r + "  " + (rf.isFile() ? ProKit.bytes(rf.length()) : "—"), 12.5f, ProKit.TXT, false), new LinearLayout.LayoutParams(0, -2, 1f));
                Button pl = ProKit.soft(this, "▶");
                pl.setOnClickListener(v -> ProKit.play(this, new File(ProKit.jobDir(this, job), r)));
                row.addView(pl, ProKit.lp(this, -2, -2, 0, 2, 0));
                if (!o.optBoolean("preview")) {
                    Button pb = ProKit.pill(this, "📤 انشر", ProKit.ACC, ProKit.ON_ACC);
                    pb.setOnClickListener(v -> startActivity(new Intent(this, ProShareActivity.class).putExtra("job", job).putExtra("file", r)));
                    row.addView(pb, ProKit.lp(this, -2, -2, 0, 2, 0));
                }
                c.addView(row);
            }
            if (o.optBoolean("preview")) {
                File pv = new File(ProKit.jobDir(this, job), "preview.mp4");
                if (pv.isFile()) {
                    Button b = ProKit.soft(this, "▶ شغّل المعاينة");
                    b.setOnClickListener(v -> ProKit.play(this, pv));
                    c.addView(b, ProKit.lp(this, -1, -2, 0, 0, 6));
                }
            }
            if (reels.size() > 1 && !o.optBoolean("preview")) {
                Button sch = ProKit.soft(this, "📅 جدولة كل الريلز على أيام");
                sch.setOnClickListener(v -> ProKit.dlg(this).setTitle("كام ريل في اليوم؟")
                        .setItems(new String[]{"1 في اليوم", "2 في اليوم", "3 في اليوم", "4 في اليوم"}, (d, i) -> {
                            ProShareActivity.scheduleBatch(this, job, reels, i + 1, System.currentTimeMillis());
                            ProKit.toast(this, "اتجدولت التذكيرات ✓ (شوفها في مركز النشر)");
                        }).show());
                c.addView(sch, ProKit.lp(this, -1, -2, 0, 0, 6));
            }
        }
        LinearLayout act = new LinearLayout(this);
        Button re = ProKit.soft(this, "↻ أعد التشغيل");
        re.setOnClickListener(v -> {
            ProKit.saveCfg(this, ProKit.merge(ProKit.parseObj(o.optString("cfg"))));
            startActivity(new Intent(this, ProCreateActivity.class));
        });
        Button keep = ProKit.soft(this, o.optBoolean("keep") ? "📌 مثبّت" : "📍 ثبّت");
        keep.setOnClickListener(v -> {
            toggle(job, "keep");
            build();
        });
        Button del = ProKit.soft(this, "🗑");
        del.setOnClickListener(v -> ProKit.dlg(this).setMessage("تمسح العملية دي وملفاتها؟").setPositiveButton("امسح", (d, w2) -> {
            File[] fs = ProKit.jobDir(this, job).listFiles();
            if (fs != null) for (File x : fs) x.delete();
            ProKit.jobDir(this, job).delete();
            JSONArray h = ProKit.arr(this, "hist");
            JSONArray n = new JSONArray();
            for (int i = 0; i < h.length(); i++) {
                JSONObject q = h.optJSONObject(i);
                if (q != null && !job.equals(q.optString("job"))) n.put(q);
            }
            ProKit.saveArr(this, "hist", n);
            build();
        }).setNegativeButton("إلغاء", null).show());
        act.addView(re, ProKit.lp(this, 0, -2, 1.3f, 2, 8));
        act.addView(keep, ProKit.lp(this, 0, -2, 1f, 2, 8));
        act.addView(del, ProKit.lp(this, -2, -2, 0, 2, 8));
        c.addView(act);
        return c;
    }

    void toggle(String job, String key) {
        JSONArray h = ProKit.arr(this, "hist");
        for (int i = 0; i < h.length(); i++) {
            JSONObject q = h.optJSONObject(i);
            if (q != null && job.equals(q.optString("job"))) ProKit.put(q, key, !q.optBoolean(key));
        }
        ProKit.saveArr(this, "hist", h);
    }
}
