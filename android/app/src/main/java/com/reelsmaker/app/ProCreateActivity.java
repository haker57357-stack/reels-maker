package com.reelsmaker.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** إنشاء ريلز في «ميديا برو»: كل ميزة بوضع بسيط وبدايل وستايلات ومفتاح تشغيل/إيقاف، والمعالجة كلها على السيرفر. */
public class ProCreateActivity extends Activity {
    SharedPreferences sp;
    JSONObject cfg;
    LinearLayout paramBox, warnBox;
    TextView progMsg;
    ProgressBar bar;
    Button goBtn, prevBtn, resumeBtn, cancelBtn, openBtn;
    LinearLayout progCard;

    final SharedPreferences.OnSharedPreferenceChangeListener listener = (p, k) -> runOnUiThread(this::refreshRun);

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        sp = ProKit.sp(this);
        cfg = ProKit.cfg(this);
        getWindow().setStatusBarColor(ProKit.BG);
        getWindow().setNavigationBarColor(ProKit.BG);
        render();
    }

    @Override protected void onResume() {
        super.onResume();
        sp.registerOnSharedPreferenceChangeListener(listener);
        refreshRun();
    }

    @Override protected void onPause() {
        sp.unregisterOnSharedPreferenceChangeListener(listener);
        ProKit.saveCfg(this, cfg);
        super.onPause();
    }

    // ───────── مساعدات ─────────
    void bindText(EditText e, final String key) {
        e.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) {
                ProKit.put(cfg, key, s.toString());
                checkRules();
            }
        });
    }

    void bindInt(EditText e, final String key, final int def) {
        e.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) {
                int v = def;
                try {
                    v = Integer.parseInt(s.toString().trim());
                } catch (Exception ignored) { }
                ProKit.put(cfg, key, v);
            }
        });
    }

    View num(String label, String key, int def) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(ProKit.cap(this, label));
        EditText e = ProKit.input(this, "", String.valueOf(cfg.optInt(key, def)), InputType.TYPE_CLASS_NUMBER, true);
        e.setGravity(Gravity.CENTER);
        bindInt(e, key, def);
        col.addView(e, new LinearLayout.LayoutParams(-1, ProKit.dp(this, 44)));
        col.setLayoutParams(ProKit.lp(this, 0, -2, 1f, 3, 0));
        return col;
    }

    LinearLayout row(View... vs) {
        LinearLayout r = new LinearLayout(this);
        for (View v : vs) r.addView(v);
        return r;
    }

    View chips(String[] labels, String[] values, final String key, final Runnable after) {
        return ProKit.chips(this, labels, values, cfg.optString(key), v -> {
            ProKit.put(cfg, key, v);
            checkRules();
            if (after != null) after.run();
        });
    }

    View sw(String icon, String title, String desc, final String key) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, ProKit.dp(this, 5), 0, ProKit.dp(this, 5));
        r.addView(ProKit.iconChip(this, icon, 32));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        ProKit.pad(col, 10, 0, 8, 0, this);
        col.addView(ProKit.text(this, title, 13.5f, ProKit.TXT, true));
        if (desc != null) col.addView(ProKit.text(this, desc, 11, ProKit.DIM, false));
        r.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
        final android.widget.Switch s = ProKit.sw(this, cfg.optBoolean(key, false));
        s.setOnCheckedChangeListener((x, on) -> {
            ProKit.put(cfg, key, on);
            checkRules();
        });
        r.addView(s);
        r.setOnClickListener(v -> s.toggle());
        return r;
    }

    LinearLayout.LayoutParams gap() {
        return ProKit.lp(this, -1, -2, 0, 0, 10);
    }

    // ───────── البناء ─────────
    void render() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(ProKit.BG);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        LinearLayout hd = new LinearLayout(this);
        hd.setGravity(Gravity.CENTER_VERTICAL);
        ProKit.pad(hd, 14, 10, 14, 6, this);
        TextView back = ProKit.text(this, "›", 28, ProKit.ACC, true);
        back.setOnClickListener(v -> finish());
        ProKit.pad(back, 4, 0, 14, 0, this);
        hd.addView(back);
        hd.addView(ProKit.text(this, "إنشاء ريلز", 20, ProKit.TXT, true), new LinearLayout.LayoutParams(0, -2, 1f));
        Button presets = ProKit.soft(this, "🎨 قوالب");
        presets.setOnClickListener(v -> presetsDialog());
        hd.addView(presets);
        root.addView(hd);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        ProKit.pad(col, 14, 4, 14, 14, this);

        warnBox = new LinearLayout(this);
        warnBox.setOrientation(LinearLayout.VERTICAL);
        col.addView(warnBox);

        // التقدّم
        progCard = ProKit.card(this);
        progMsg = ProKit.text(this, "", 13, ProKit.TXT, false);
        bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        bar.setProgressTintList(android.content.res.ColorStateList.valueOf(ProKit.ACC));
        progCard.addView(progMsg);
        progCard.addView(bar, new LinearLayout.LayoutParams(-1, ProKit.dp(this, 8)));
        LinearLayout pr = new LinearLayout(this);
        resumeBtn = ProKit.soft(this, "↻ استكمال");
        cancelBtn = ProKit.soft(this, "✖ إلغاء");
        openBtn = ProKit.pill(this, "📂 افتح النتيجة", ProKit.OK, ProKit.ON_ACC);
        resumeBtn.setOnClickListener(v -> resume());
        cancelBtn.setOnClickListener(v -> cancelRun());
        openBtn.setOnClickListener(v -> startActivity(new Intent(this, ProLibActivity.class).putExtra("job", sp.getString("r_job", ""))));
        pr.addView(resumeBtn, ProKit.lp(this, 0, -2, 1, 3, 8));
        pr.addView(cancelBtn, ProKit.lp(this, 0, -2, 1, 3, 8));
        pr.addView(openBtn, ProKit.lp(this, 0, -2, 1, 3, 8));
        progCard.addView(pr);
        progCard.setVisibility(View.GONE);
        col.addView(progCard, gap());

        // المصدر
        LinearLayout src = ProKit.card(this);
        src.addView(ProKit.text(this, "🔗 الفيديو", 14.5f, ProKit.TXT, true));
        src.addView(ProKit.cap(this, "الصق رابط أو أكتر (كل رابط في سطر). يوتيوب • تيك توك • فيسبوك • رابط مباشر"));
        EditText url = ProKit.input(this, "https://...", cfg.optString("url"), InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_VARIATION_URI, false);
        url.setMinLines(2);
        url.setMaxLines(5);
        bindText(url, "url");
        src.addView(url, new LinearLayout.LayoutParams(-1, -2));
        Button paste = ProKit.soft(this, "📋 لصق رابط");
        paste.setOnClickListener(v -> {
            String t = ProKit.paste(this);
            if (!t.isEmpty()) {
                String cur = url.getText().toString().trim();
                url.setText(cur.isEmpty() ? t : cur + "\n" + t);
            }
        });
        src.addView(paste, ProKit.lp(this, -1, -2, 0, 0, 6));
        src.addView(sw("⚡", "الأقصر الأول", "لو أكتر من رابط: أول ريلز تخلص بسرعة", "shortest"));
        src.addView(ProKit.cap(this, "المشروع (اختياري)"));
        src.addView(projectChips());
        col.addView(src, gap());

        // القص
        LinearLayout cut = ProKit.card(this);
        cut.addView(ProKit.text(this, "✂️ طريقة القص", 14.5f, ProKit.TXT, true));
        cut.addView(ProKit.cap(this, "بالطول هو الأساسي. الباقي بدايل تختار منها."));
        cut.addView(chips(new String[]{"بالطول", "بالمشهد", "بالجمل", "بالصمت", "أفضل اللحظات", "أجزاء", "تقسيم متساوي"},
                new String[]{"len", "scene", "sentence", "silence", "best", "series", "equal"}, "cut", this::rebuildParams));
        paramBox = new LinearLayout(this);
        paramBox.setOrientation(LinearLayout.VERTICAL);
        cut.addView(paramBox);
        col.addView(cut, gap());
        rebuildParams();

        // الشكل
        LinearLayout fmt = ProKit.card(this);
        fmt.addView(ProKit.text(this, "📐 الشكل والجودة", 14.5f, ProKit.TXT, true));
        fmt.addView(ProKit.cap(this, "المقاس"));
        fmt.addView(chips(new String[]{"رأسي 9:16", "4:5", "مربع 1:1", "أفقي 16:9"}, new String[]{"9:16", "4:5", "1:1", "16:9"}, "ratio", null));
        fmt.addView(ProKit.cap(this, "الجودة"));
        fmt.addView(chips(new String[]{"1080 عالية", "720 أسرع"}, new String[]{"1080", "720"}, "res", null));
        fmt.addView(ProKit.cap(this, "ملء الشاشة"));
        fmt.addView(chips(new String[]{"تلقائي", "خلفية مغبشة", "قص", "أشرطة"}, new String[]{"auto", "blur", "crop", "bars"}, "fill", null));
        fmt.addView(sw("🎯", "تتبّع الوش", "يحافظ على الوش في الكادر عند القص (أبطأ شوية)", "track"));
        fmt.addView(sw("🎥", "تثبيت الصورة", "يقلل رعشة الكاميرا (أبطأ شوية)", "stab"));
        fmt.addView(sw("↔️", "مرآة (عكس الفيديو)", null, "rev"));
        fmt.addView(ProKit.cap(this, "السرعة"));
        fmt.addView(ProKit.chips(this, new String[]{"0.75x", "عادية", "1.25x", "1.5x"}, new String[]{"0.75", "1.0", "1.25", "1.5"},
                String.valueOf(cfg.optDouble("speed", 1.0)), v -> ProKit.put(cfg, "speed", Double.parseDouble(v))));
        fmt.addView(sw("📉", "وضع التوفير", "جودة 720 وحجم أصغر — مناسب للنت الضعيف", "saver"));
        col.addView(fmt, gap());

        // الصوت
        LinearLayout au = ProKit.card(this);
        au.addView(ProKit.text(this, "🔊 الصوت", 14.5f, ProKit.TXT, true));
        au.addView(sw("♻️", "تنظيف الصوت", "يقلل الضوضاء والهسهسة", "clean"));
        au.addView(sw("🔇", "حذف الصمت", "يشيل الفراغات الطويلة بين الكلام", "nosil"));
        au.addView(sw("⚖️", "توحيد مستوى الصوت", "صوت ثابت من أول الريل لآخره", "norm"));
        col.addView(au, gap());

        // موسيقى
        EditText murl = ProKit.input(this, "رابط موسيقى بدل المكتبة (اختياري)", cfg.optString("murl"), InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI, true);
        bindText(murl, "murl");
        col.addView(ProKit.feature(this, cfg, "🎼", "موسيقى", "مكتبة مولّدة من غير حقوق — مع خفض تلقائي وقت الكلام", "music",
                chips(new String[]{"هادئة", "دافئة", "نبض", "بسيطة"}, new String[]{"calm", "warm", "pulse", "minimal"}, "mstyle", null),
                ProKit.cap(this, "علو الموسيقى"),
                ProKit.chips(this, new String[]{"خفيف", "وسط", "عالي"}, new String[]{"8", "14", "22"}, String.valueOf(cfg.optInt("mvol", 8)),
                        v -> ProKit.put(cfg, "mvol", Integer.parseInt(v))),
                sw("📉", "خفض الموسيقى وقت الكلام", null, "duck"), murl), gap());

        // ألوان
        col.addView(ProKit.feature(this, cfg, "🎨", "ألوان الصورة", "بدون = الأصل", null,
                chips(new String[]{"بدون", "دافئ", "سينمائي", "ساطع", "أبيض وأسود", "حيوي"},
                        new String[]{"none", "warm", "cinema", "bright", "bw", "vivid"}, "look", null)), gap());

        // نص
        EditText tx = ProKit.input(this, "سطر يظهر على كل ريل", cfg.optString("text"), InputType.TYPE_CLASS_TEXT, true);
        bindText(tx, "text");
        col.addView(ProKit.feature(this, cfg, "📝", "نص على الفيديو", "البسيط: نص أبيض فقط", "text_on", tx,
                chips(new String[]{"بسيط (نص فقط)", "صندوق", "نيون", "ذهبي"}, new String[]{"plain", "box", "neon", "gold"}, "text_style", null)), gap());

        // ترجمة / كلام
        col.addView(ProKit.feature(this, cfg, "💬", "ترجمة وتعليق تلقائي", "بيكتب الكلام المنطوق على الفيديو (وقت أطول على السيرفر)", "cap",
                chips(new String[]{"بسيط (نص فقط)", "كلمة بكلمة", "صندوق", "نيون"}, new String[]{"plain", "word", "box", "neon"}, "cap_style", null),
                ProKit.cap(this, "لغة الكلام"),
                chips(new String[]{"تلقائي", "عربي", "إنجليزي"}, new String[]{"auto", "ar", "en"}, "cap_lang", null),
                ProKit.cap(this, "ترجمة إلى"),
                chips(new String[]{"من غير ترجمة", "عربي", "إنجليزي"}, new String[]{"none", "ar", "en"}, "cap_tr", null)), gap());

        // علامة مائية
        EditText wm = ProKit.input(this, "@اسمك أو اسم القناة", cfg.optString("wm_text"), InputType.TYPE_CLASS_TEXT, true);
        bindText(wm, "wm_text");
        col.addView(ProKit.feature(this, cfg, "💧", "علامة مائية", "البسيط: نص ثابت في الركن", "wm", wm,
                chips(new String[]{"بسيط (ثابتة)", "متحركة"}, new String[]{"plain", "moving"}, "wm_style", null)), gap());

        // مقدمة وخاتمة
        EditText it = ProKit.input(this, "نص المقدمة", cfg.optString("intro_text"), InputType.TYPE_CLASS_TEXT, true);
        bindText(it, "intro_text");
        col.addView(ProKit.feature(this, cfg, "🎬", "مقدمة ثابتة", "كارت نص قصير في أول كل ريل", "intro", it,
                chips(new String[]{"بسيط (نص فقط)", "صندوق", "ذهبي"}, new String[]{"plain", "box", "gold"}, "intro_style", null),
                ProKit.chips(this, new String[]{"1 ث", "2 ث", "3 ث"}, new String[]{"1", "2", "3"}, String.valueOf(cfg.optInt("intro_sec", 2)),
                        v -> ProKit.put(cfg, "intro_sec", Integer.parseInt(v)))), gap());
        EditText ot = ProKit.input(this, "نص الخاتمة", cfg.optString("outro_text"), InputType.TYPE_CLASS_TEXT, true);
        bindText(ot, "outro_text");
        col.addView(ProKit.feature(this, cfg, "🏁", "خاتمة ثابتة", "كارت نص قصير في آخر كل ريل", "outro", ot,
                chips(new String[]{"بسيط (نص فقط)", "صندوق", "ذهبي"}, new String[]{"plain", "box", "gold"}, "outro_style", null),
                ProKit.chips(this, new String[]{"1 ث", "2 ث", "3 ث"}, new String[]{"1", "2", "3"}, String.valueOf(cfg.optInt("outro_sec", 2)),
                        v -> ProKit.put(cfg, "outro_sec", Integer.parseInt(v)))), gap());

        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        sv.addView(col);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout bar2 = new LinearLayout(this);
        ProKit.pad(bar2, 12, 8, 12, 10, this);
        bar2.setBackgroundColor(ProKit.BG);
        prevBtn = ProKit.soft(this, "👁 معاينة سريعة");
        goBtn = ProKit.primary(this, "ابدأ الآن ☁️");
        prevBtn.setOnClickListener(v -> startRun(true));
        goBtn.setOnClickListener(v -> startRun(false));
        bar2.addView(prevBtn, ProKit.lp(this, 0, ProKit.dp(this, 50), 1f, 4, 0));
        bar2.addView(goBtn, ProKit.lp(this, 0, ProKit.dp(this, 50), 1.4f, 4, 0));
        root.addView(bar2);
        setContentView(root);
        checkRules();
        refreshRun();
    }

    View projectChips() {
        JSONArray ps = ProKit.arr(this, "projects");
        List<String> labels = new ArrayList<>(), vals = new ArrayList<>();
        labels.add("بدون");
        vals.add("");
        for (int i = 0; i < ps.length(); i++) {
            labels.add(ps.optString(i));
            vals.add(ps.optString(i));
        }
        labels.add("＋ مشروع جديد");
        vals.add("__new__");
        String cur = cfg.optString("project");
        if (!vals.contains(cur)) cur = "";
        return ProKit.chips(this, labels.toArray(new String[0]), vals.toArray(new String[0]), cur, v -> {
            if ("__new__".equals(v)) {
                final EditText e = ProKit.input(this, "اسم المشروع", "", InputType.TYPE_CLASS_TEXT, true);
                ProKit.dlg(this).setTitle("مشروع جديد").setView(e).setPositiveButton("إضافة", (d, w) -> {
                    String n = e.getText().toString().trim();
                    if (n.isEmpty()) return;
                    JSONArray a = ProKit.arr(this, "projects");
                    a.put(n);
                    ProKit.saveArr(this, "projects", a);
                    ProKit.put(cfg, "project", n);
                    ProKit.saveCfg(this, cfg);
                    render();
                }).setNegativeButton("إلغاء", (d, w) -> render()).show();
            } else {
                ProKit.put(cfg, "project", v);
            }
        });
    }

    void rebuildParams() {
        paramBox.removeAllViews();
        String cut = cfg.optString("cut", "len");
        LinearLayout r = new LinearLayout(this);
        if ("equal".equals(cut)) {
            r.addView(num("عدد الأجزاء", "parts", 4));
        } else if ("len".equals(cut)) {
            r.addView(num("عدد الريلز", "n", 5));
            r.addView(num("طول الريل (ث)", "len", 20));
        } else if ("series".equals(cut)) {
            r.addView(num("عدد الأجزاء", "n", 5));
            r.addView(num("طول الجزء (ث)", "len", 20));
            r.addView(num("تداخل (ث)", "overlap", 0));
        } else {
            r.addView(num("عدد الريلز", "n", 5));
            r.addView(num("أقل مدة (ث)", "min", 15));
            r.addView(num("أقصى مدة (ث)", "max", 25));
        }
        paramBox.addView(r, ProKit.lp(this, -1, -2, 0, 0, 6));
        String hint = "";
        if ("scene".equals(cut)) hint = "بيقص عند تغيّر اللقطة.";
        else if ("sentence".equals(cut)) hint = "بيقص عند نهاية الجملة المنطوقة (بيستخدم التفريغ الصوتي).";
        else if ("silence".equals(cut)) hint = "بيقص عند وقفات الكلام.";
        else if ("best".equals(cut)) hint = "بيختار اللقطات الأقوى (حركة وصوت).";
        else if ("series".equals(cut)) hint = "أجزاء متسلسلة مكتوب عليها «الجزء 1/5».";
        else if ("equal".equals(cut)) hint = "بيقسم الفيديو لأجزاء متساوية.";
        if (!hint.isEmpty()) paramBox.addView(ProKit.cap(this, hint));
    }

    /** تنبيهات التوافق: بتظهر قبل التشغيل، ومفيش ميزة بتتلغي بصمت (السيرفر بيطبّق نفس القواعد). */
    void checkRules() {
        if (warnBox == null) return;
        warnBox.removeAllViews();
        List<String> w = new ArrayList<>();
        boolean track = cfg.optBoolean("track"), wide = "16:9".equals(cfg.optString("ratio"));
        String fill = cfg.optString("fill");
        if (track && wide) w.add("تتبّع الوش مش بيشتغل مع الشكل الأفقي.");
        else if (track && ("blur".equals(fill) || "bars".equals(fill))) w.add("التتبّع محتاج قص يملا الشاشة، فهيتحوّل الملء لـ «قص».");
        if (cfg.optBoolean("saver")) w.add("وضع التوفير: الجودة 720 وحجم أصغر.");
        if (cfg.optBoolean("cap") || "sentence".equals(cfg.optString("cut"))) w.add("الترجمة/التفريغ بتزوّد وقت المعالجة على السيرفر.");
        if (cfg.optBoolean("wm") && cfg.optString("wm_text").trim().isEmpty()) w.add("العلامة المائية محتاجة نص عشان تشتغل.");
        if (cfg.optBoolean("intro") && cfg.optString("intro_text").trim().isEmpty()) w.add("المقدمة محتاجة نص عشان تشتغل.");
        if (cfg.optBoolean("outro") && cfg.optString("outro_text").trim().isEmpty()) w.add("الخاتمة محتاجة نص عشان تشتغل.");
        if (w.isEmpty()) return;
        LinearLayout c = ProKit.card(this);
        c.setBackground(ProKit.shape(this, 0x22E9B872, 18, ProKit.WARN, 1));
        for (String s : w) c.addView(ProKit.text(this, "• " + s, 12, ProKit.WARN, false));
        warnBox.addView(c, ProKit.lp(this, -1, -2, 0, 0, 0));
    }

    // ───────── القوالب ─────────
    void presetsDialog() {
        final List<String> names = new ArrayList<>();
        final List<JSONObject> data = new ArrayList<>();
        names.add("🌿 بسيط (الأساسي)");
        data.add(new JSONObject());
        JSONObject fast = new JSONObject();
        ProKit.put(fast, "saver", true);
        ProKit.put(fast, "res", "720");
        names.add("⚡ سريع وخفيف");
        data.add(fast);
        JSONObject talk = new JSONObject();
        ProKit.put(talk, "clean", true);
        ProKit.put(talk, "norm", true);
        ProKit.put(talk, "nosil", true);
        ProKit.put(talk, "cap", true);
        ProKit.put(talk, "cap_style", "word");
        ProKit.put(talk, "cut", "sentence");
        names.add("🎙 كلام وحوار");
        data.add(talk);
        JSONObject hq = new JSONObject();
        ProKit.put(hq, "stab", true);
        ProKit.put(hq, "clean", true);
        ProKit.put(hq, "norm", true);
        ProKit.put(hq, "look", "bright");
        names.add("💎 جودة عالية");
        data.add(hq);
        final JSONObject saved = ProKit.obj(this, "presets");
        Iterator<String> it = saved.keys();
        while (it.hasNext()) {
            String k = it.next();
            names.add("⭐ " + k);
            data.add(saved.optJSONObject(k) == null ? new JSONObject() : saved.optJSONObject(k));
        }
        final int builtin = 4;
        ProKit.dlg(this).setTitle("القوالب")
                .setItems(names.toArray(new String[0]), (d, i) -> {
                    JSONObject base = ProKit.defaults();
                    String keepUrl = cfg.optString("url"), keepProj = cfg.optString("project");
                    JSONObject n = ProKit.merge(data.get(i));
                    // القالب مابيغيّرش الرابط ولا المشروع
                    JSONObject out = ProKit.merge(base);
                    Iterator<String> kk = n.keys();
                    while (kk.hasNext()) {
                        String k = kk.next();
                        ProKit.put(out, k, n.opt(k));
                    }
                    ProKit.put(out, "url", keepUrl);
                    ProKit.put(out, "project", keepProj);
                    cfg = out;
                    ProKit.saveCfg(this, cfg);
                    render();
                    if (i >= builtin) ProKit.toast(this, "اتطبّق القالب");
                })
                .setPositiveButton("＋ احفظ الحالي كقالب", (d, w) -> {
                    final EditText e = ProKit.input(this, "اسم القالب", "", InputType.TYPE_CLASS_TEXT, true);
                    ProKit.dlg(this).setTitle("حفظ قالب").setView(e).setPositiveButton("حفظ", (d2, w2) -> {
                        String n = e.getText().toString().trim();
                        if (n.isEmpty()) return;
                        JSONObject c = ProKit.merge(ProKit.parseObj(cfgString()));
                        c.remove("url");
                        c.remove("project");
                        c.remove("preview");
                        JSONObject all = ProKit.obj(this, "presets");
                        ProKit.put(all, n, c);
                        ProKit.saveObj(this, "presets", all);
                        ProKit.toast(this, "اتحفظ القالب ✓");
                    }).setNegativeButton("إلغاء", null).show();
                })
                .setNeutralButton("🗑 مسح قوالبي", (d, w) -> {
                    ProKit.saveObj(this, "presets", new JSONObject());
                    ProKit.toast(this, "اتمسحت");
                })
                .setNegativeButton("قفل", null).show();
    }

    String cfgString() {
        return cfg.toString();
    }

    // ───────── التشغيل ─────────
    boolean running() {
        String s = sp.getString("r_stage", "");
        return "NEW".equals(s) || "RUN".equals(s);
    }

    void startRun(boolean preview) {
        if (running()) return;
        if (Gh.TOK.isEmpty() || Gh.REPO.isEmpty()) {
            ProKit.toast(this, "التطبيق اتبنى من غير بيانات الخدمة.");
            return;
        }
        String urls = cfg.optString("url").trim();
        if (!urls.contains("http") && !new File(urls).isFile()) {
            ProKit.toast(this, "الصق رابط الفيديو الأول.");
            return;
        }
        JSONObject out;
        try {
            out = new JSONObject(cfg.toString());
        } catch (Exception e) {
            return;
        }
        ProKit.put(out, "preview", preview);
        ProKit.saveCfg(this, cfg);
        String job = ProKit.newId();
        String first = urls.split("[\\r\\n]+")[0].trim();
        String title = !cfg.optString("project").isEmpty() ? cfg.optString("project") : ProKit.host(first);
        sp.edit().putString("r_job", job).putString("r_cfg", out.toString()).putString("r_stage", "NEW").putBoolean("r_sent", false)
                .putBoolean("r_prev", preview).putBoolean("r_shown", false).putString("r_title", title).putString("r_proj", cfg.optString("project"))
                .putString("r_msg", preview ? "بجهّز المعاينة..." : "بجهّز الطلب...").putInt("r_pct", 0)
                .putLong("r_t0", System.currentTimeMillis()).apply();
        startSvc(this);
        refreshRun();
    }

    static void startSvc(Context c) {
        Intent i = new Intent(c, ProService.class);
        if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i);
        else c.startService(i);
    }

    void resume() {
        sp.edit().putString("r_stage", "RUN").putString("r_msg", "بكمّل...").apply();
        startSvc(this);
        refreshRun();
    }

    void cancelRun() {
        final String job = sp.getString("r_job", "");
        sp.edit().putString("r_stage", "FAIL").putString("r_msg", "اتلغت.").apply();
        new Thread(() -> {
            try {
                ProNet.cancel(job);
            } catch (Exception ignored) { }
        }).start();
        refreshRun();
    }

    void refreshRun() {
        if (progCard == null) return;
        String st = sp.getString("r_stage", "");
        if (st.isEmpty() || sp.getString("r_job", "").isEmpty()) {
            progCard.setVisibility(View.GONE);
            return;
        }
        boolean run = "NEW".equals(st) || "RUN".equals(st);
        boolean done = "DONE".equals(st), wait = "WAIT".equals(st);
        progCard.setVisibility(View.VISIBLE);
        String icon = run ? "⏳ " : done ? "✅ " : wait ? "⏸ " : "❌ ";
        progMsg.setText(icon + sp.getString("r_msg", ""));
        bar.setProgress(sp.getInt("r_pct", 0));
        bar.setVisibility(run ? View.VISIBLE : View.GONE);
        resumeBtn.setVisibility(wait ? View.VISIBLE : View.GONE);
        cancelBtn.setVisibility(run || wait ? View.VISIBLE : View.GONE);
        openBtn.setVisibility(done ? View.VISIBLE : View.GONE);
        goBtn.setEnabled(!run);
        prevBtn.setEnabled(!run);
        goBtn.setAlpha(run ? 0.5f : 1f);
        prevBtn.setAlpha(run ? 0.5f : 1f);
        if (done && sp.getBoolean("r_prev", false) && !sp.getBoolean("r_shown", false)) {
            sp.edit().putBoolean("r_shown", true).apply();
            File f = new File(ProKit.jobDir(this, sp.getString("r_job", "")), "preview.mp4");
            if (f.isFile()) ProKit.play(this, f);
        }
    }
}
