package com.reelsmaker.app;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Calendar;
import java.util.Iterator;

/** الفحص والإعدادات: فحص الاتصال بالسيرفر خطوة خطوة، تنبيه دقايق Actions، ونسخة احتياطية للإعدادات. */
public class ProToolsActivity extends Activity {
    TextView out;
    Button run;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(ProKit.BG);
        getWindow().setNavigationBarColor(ProKit.BG);
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
        hd.addView(ProKit.text(this, "الفحص والإعدادات", 20, ProKit.TXT, true));
        root.addView(hd);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        ProKit.pad(col, 14, 4, 14, 16, this);

        LinearLayout dg = ProKit.card(this);
        dg.addView(ProKit.text(this, "🔧 فحص الاتصال", 14.5f, ProKit.TXT, true));
        dg.addView(ProKit.cap(this, "بيفحص التوكن والمستودع وملف pro.yml وصلاحية التشغيل — من غير ما يشغّل ريلز."));
        run = ProKit.primary(this, "افحص دلوقتي");
        run.setOnClickListener(v -> diagnose());
        dg.addView(run, ProKit.lp(this, -1, -2, 0, 0, 8));
        out = ProKit.text(this, "", 12.5f, ProKit.TXT, false);
        out.setTextIsSelectable(true);
        dg.addView(out);
        col.addView(dg, ProKit.lp(this, -1, -2, 0, 0, 6));

        LinearLayout st = ProKit.card(this);
        st.addView(ProKit.text(this, "🔧 حالة العملية", 14.5f, ProKit.TXT, true));
        st.addView(ProKit.cap(this, "لو عملية عالقة وزرّ «ابدأ» مقفول، صفّر حالتها (السيرفر مش هيتأثر)."));
        Button rs = ProKit.soft(this, "صفّر العملية العالقة");
        rs.setOnClickListener(v -> {
            ProKit.sp(this).edit().putString("r_stage", "").apply();
            ProKit.toast(this, "اتصفّرت.");
        });
        st.addView(rs, ProKit.lp(this, -1, -2, 0, 0, 6));
        col.addView(st, ProKit.lp(this, -1, -2, 0, 0, 10));

        LinearLayout bk = ProKit.card(this);
        bk.addView(ProKit.text(this, "💾 نسخة احتياطية للإعدادات", 14.5f, ProKit.TXT, true));
        bk.addView(ProKit.cap(this, "بتشمل القوالب والمشاريع وأسماء الحسابات والإعدادات. مفيهاش جلسات الدخول ولا كلمات سر (أمان)."));
        Button ex = ProKit.soft(this, "📤 انسخ النسخة الاحتياطية");
        ex.setOnClickListener(v -> ProKit.copy(this, "backup", exportJson()));
        Button im = ProKit.soft(this, "📥 استرجع من الحافظة");
        im.setOnClickListener(v -> importJson(ProKit.paste(this)));
        bk.addView(ex, ProKit.lp(this, -1, -2, 0, 0, 6));
        bk.addView(im, ProKit.lp(this, -1, -2, 0, 0, 6));
        col.addView(bk, ProKit.lp(this, -1, -2, 0, 0, 10));

        ScrollView sv = new ScrollView(this);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        sv.addView(col);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);
    }

    // ───────── فحص ─────────
    void line(final String s) {
        runOnUiThread(() -> out.append(s + "\n"));
    }

    void diagnose() {
        out.setText("");
        run.setEnabled(false);
        new Thread(() -> {
            try {
                check();
            } catch (Throwable t) {
                line("❌ خطأ في الفحص: " + t.getClass().getSimpleName());
            }
            runOnUiThread(() -> run.setEnabled(true));
        }).start();
    }

    void check() {
        if (Gh.TOK.isEmpty() || Gh.REPO.isEmpty()) {
            line("❌ التطبيق اتبنى من غير توكن أو مستودع. اعمل بناء جديد.");
            return;
        }
        line("✅ التوكن والمستودع موجودين: " + Gh.REPO);
        try {
            String[] x = Gh.http("GET", Gh.API + "/rate_limit", null);
            if ("401".equals(x[0])) {
                line("❌ التوكن منتهي أو اتلغى. اعمل توكن جديد: RESET_TOKEN=1 bash ~/reels-maker-main/termux-update.sh");
                return;
            }
            line("✅ الاتصال بـ GitHub شغّال (" + x[0] + ")");
        } catch (Exception e) {
            line("❌ مفيش اتصال بـ GitHub: " + e.getClass().getSimpleName() + " (النت؟)");
            return;
        }
        boolean priv = false;
        try {
            JSONObject r = ProNet.repoInfo();
            priv = r.optBoolean("private");
            line("• المستودع " + (priv ? "خاص (2000 دقيقة/شهر مجانًا)" : "عام (دقايق مجانية بلا حد)"));
        } catch (Exception e) {
            line("⚠️ مقدرتش أقرا بيانات المستودع.");
        }
        try {
            String[] x = Gh.http("GET", Gh.API + "/repos/" + Gh.REPO + "/actions/workflows/pro.yml", null);
            if ("404".equals(x[0])) line("❌ ملف pro.yml مش موجود في المستودع. شغّل termux-update.sh الجديد.");
            else if ("200".equals(x[0])) line("✅ ملف pro.yml موجود");
            else line("⚠️ فحص pro.yml رجّع " + x[0]);
        } catch (Exception e) {
            line("⚠️ فحص pro.yml فشل.");
        }
        try {
            String[] x = Gh.http("POST", Gh.API + "/repos/" + Gh.REPO + "/actions/workflows/pro.yml/dispatches", "{\"ref\":\"__probe__\"}");
            if ("422".equals(x[0])) line("✅ التوكن يقدر يشغّل العمليات (قراءة وكتابة)");
            else if ("403".equals(x[0])) line("❌ التوكن ناقصه صلاحية «Actions: Read and write». اعمل توكن جديد بالأمر: RESET_TOKEN=1 bash ~/reels-maker-main/termux-update.sh");
            else if ("401".equals(x[0])) line("❌ التوكن منتهي.");
            else if ("404".equals(x[0])) line("⚠️ 404: المستودع أو pro.yml مش ظاهرين للتوكن.");
            else line("⚠️ رد غريب من فحص الصلاحية: " + x[0]);
        } catch (Exception e) {
            line("⚠️ فحص الصلاحية فشل.");
        }
        try {
            String[] x = Gh.http("GET", Gh.API + "/repos/" + Gh.REPO + "/actions/workflows/pro.yml/runs?per_page=3", null);
            if ("200".equals(x[0])) {
                JSONArray a = new JSONObject(x[1]).getJSONArray("workflow_runs");
                if (a.length() == 0) line("• لسه مفيش تشغيلات سابقة.");
                for (int i = 0; i < a.length(); i++) {
                    JSONObject o = a.getJSONObject(i);
                    String c = o.optString("conclusion");
                    line("• آخر تشغيل " + (i + 1) + ": " + o.optString("status") + (c.isEmpty() || "null".equals(c) ? "" : " / " + c));
                }
            }
        } catch (Exception e) {
            line("⚠️ مقدرتش أقرا آخر التشغيلات.");
        }
        try {
            ProUpdate.Info u = ProUpdate.check();
            if (u == null) line("• مفيش إصدار APK منشور.");
            else if (u.newer) line("📣 فيه نسخة أحدث من التطبيق — اضغط «حدّث» في الشاشة الرئيسية لميديا برو.");
            else line("✅ التطبيق على آخر نسخة");
        } catch (Exception e) {
            line("⚠️ مقدرتش أفحص التحديثات.");
        }
        // دقايق الشهر
        JSONArray hist = ProKit.arr(this, "hist");
        Calendar now = Calendar.getInstance();
        double mins = 0;
        for (int i = 0; i < hist.length(); i++) {
            JSONObject o = hist.optJSONObject(i);
            if (o == null) continue;
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(o.optLong("t"));
            if (c.get(Calendar.YEAR) == now.get(Calendar.YEAR) && c.get(Calendar.MONTH) == now.get(Calendar.MONTH)) mins += o.optDouble("mins", 0);
        }
        line("• دقايق السيرفر اللي استهلكتها الشهر ده من التطبيق: " + Math.round(mins));
        if (priv && mins > 1500) line("⚠️ قربت من حد الـ 2000 دقيقة الشهرية للمستودع الخاص.");
        line("— خلص الفحص —");
    }

    // ───────── نسخة احتياطية ─────────
    String exportJson() {
        JSONObject o = new JSONObject();
        ProKit.put(o, "v", 1);
        ProKit.put(o, "presets", ProKit.obj(this, "presets"));
        ProKit.put(o, "projects", ProKit.arr(this, "projects"));
        ProKit.put(o, "cfg", ProKit.obj(this, "cfg"));
        ProKit.put(o, "keep_days", ProKit.sp(this).getInt("keep_days", 14));
        ProKit.put(o, "gap", ProKit.sp(this).getInt("gap", 0));
        JSONObject accs = new JSONObject();
        for (ProPlat p : ProPlat.ALL) {
            JSONArray a = ProAccountsActivity.accounts(this, p.id);
            JSONArray n = new JSONArray();
            for (int i = 0; i < a.length(); i++) {
                JSONObject x = a.optJSONObject(i);
                if (x == null) continue;
                JSONObject y = new JSONObject();
                ProKit.put(y, "id", x.optString("id"));
                ProKit.put(y, "name", x.optString("name"));
                ProKit.put(y, "locked", x.optBoolean("locked"));
                ProKit.put(y, "ck", new JSONObject());
                ProKit.put(y, "posts", new JSONArray());
                n.put(y);
            }
            ProKit.put(accs, p.id, n);
        }
        ProKit.put(o, "accs", accs);
        return o.toString();
    }

    void importJson(String s) {
        JSONObject o = ProKit.parseObj(s);
        if (o.optInt("v", 0) != 1) {
            ProKit.toast(this, "الحافظة مفيهاش نسخة احتياطية صالحة.");
            return;
        }
        ProKit.dlg(this).setMessage("هتتدمج القوالب والمشاريع والإعدادات مع الحالية. تكمل؟").setPositiveButton("كمّل", (d, w) -> {
            JSONObject mine = ProKit.obj(this, "presets");
            JSONObject theirs = o.optJSONObject("presets");
            if (theirs != null) {
                Iterator<String> it = theirs.keys();
                while (it.hasNext()) {
                    String k = it.next();
                    ProKit.put(mine, k, theirs.opt(k));
                }
            }
            ProKit.saveObj(this, "presets", mine);
            JSONArray pa = ProKit.arr(this, "projects");
            JSONArray po = o.optJSONArray("projects");
            if (po != null) {
                for (int i = 0; i < po.length(); i++) {
                    boolean has = false;
                    for (int k = 0; k < pa.length(); k++) if (pa.optString(k).equals(po.optString(i))) has = true;
                    if (!has) pa.put(po.optString(i));
                }
            }
            ProKit.saveArr(this, "projects", pa);
            if (o.optJSONObject("cfg") != null) ProKit.saveObj(this, "cfg", o.optJSONObject("cfg"));
            ProKit.sp(this).edit().putInt("keep_days", o.optInt("keep_days", 14)).putInt("gap", o.optInt("gap", 0)).apply();
            JSONObject accs = o.optJSONObject("accs");
            if (accs != null) {
                for (ProPlat p : ProPlat.ALL) {
                    JSONArray cur = ProAccountsActivity.accounts(this, p.id);
                    JSONArray add = accs.optJSONArray(p.id);
                    if (add == null) continue;
                    for (int i = 0; i < add.length(); i++) {
                        JSONObject x = add.optJSONObject(i);
                        if (x == null) continue;
                        boolean has = false;
                        for (int k = 0; k < cur.length(); k++) {
                            JSONObject c = cur.optJSONObject(k);
                            if (c != null && c.optString("id").equals(x.optString("id"))) has = true;
                        }
                        if (!has) cur.put(x);
                    }
                    ProKit.saveArr(this, "accs_" + p.id, cur);
                }
            }
            ProKit.toast(this, "اتسترجعت الإعدادات ✓ (سجّل دخول الحسابات تاني)");
        }).setNegativeButton("إلغاء", null).show();
    }
}
