package com.reelsmaker.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.MediaController;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.UUID;

/** أدوات «ميديا برو» المشتركة: التخزين + الألوان الدافئة + مكوّنات الواجهة. (ملف جديد — مفيش حاجة قديمة اتلمست) */
final class ProKit {
    private ProKit() { }

    // ألوان بشرية دافئة (بني دافئ + مشمشي + عسلي) — مريحة للعين
    static final int BG = 0xFF1C1713, CARD = 0xFF2A221C, CARD2 = 0xFF372D25, STROKE = 0xFF4A3C30,
            ACC = 0xFFE8A06A, ACC2 = 0xFFF2C27C, TXT = 0xFFF6EBDD, DIM = 0xFFB9A592,
            OK = 0xFF9CCB8A, WARN = 0xFFE9B872, BAD = 0xFFE58A7A, ON_ACC = 0xFF2A1A0E;

    static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("pro", Context.MODE_PRIVATE);
    }

    static File dir(Context c) {
        File d = new File(c.getFilesDir(), "pro");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    static File jobDir(Context c, String job) {
        File d = new File(dir(c), job.replaceAll("[^a-z0-9]", ""));
        if (!d.exists()) d.mkdirs();
        return d;
    }

    static String newId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    // ───────── JSON ─────────
    static void put(JSONObject o, String k, Object v) {
        try {
            o.put(k, v);
        } catch (JSONException ignored) { }
    }

    static JSONObject parseObj(String s) {
        try {
            return new JSONObject(s);
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    static JSONArray parseArr(String s) {
        try {
            return new JSONArray(s);
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    static JSONArray arr(Context c, String k) {
        return parseArr(sp(c).getString(k, "[]"));
    }

    static void saveArr(Context c, String k, JSONArray a) {
        sp(c).edit().putString(k, a.toString()).apply();
    }

    static JSONObject obj(Context c, String k) {
        return parseObj(sp(c).getString(k, "{}"));
    }

    static void saveObj(Context c, String k, JSONObject o) {
        sp(c).edit().putString(k, o.toString()).apply();
    }

    /** نفس إعدادات سكريبت السيرفر (pro_core.DEFAULT) — الوضع البسيط هو الأصل. */
    static JSONObject defaults() {
        JSONObject o = new JSONObject();
        put(o, "url", "");
        put(o, "project", "");
        put(o, "preview", false);
        put(o, "saver", false);
        put(o, "shortest", true);
        put(o, "cut", "len");
        put(o, "n", 5);
        put(o, "min", 15);
        put(o, "max", 25);
        put(o, "len", 20);
        put(o, "parts", 4);
        put(o, "overlap", 0);
        put(o, "ratio", "9:16");
        put(o, "res", "1080");
        put(o, "fill", "auto");
        put(o, "track", false);
        put(o, "stab", false);
        put(o, "clean", false);
        put(o, "norm", false);
        put(o, "nosil", false);
        put(o, "music", false);
        put(o, "mstyle", "calm");
        put(o, "murl", "");
        put(o, "mvol", 8);
        put(o, "duck", true);
        put(o, "look", "none");
        put(o, "speed", 1.0);
        put(o, "rev", false);
        put(o, "text", "");
        put(o, "text_on", false);
        put(o, "text_style", "plain");
        put(o, "cap", false);
        put(o, "cap_style", "plain");
        put(o, "cap_lang", "auto");
        put(o, "cap_tr", "none");
        put(o, "wm", false);
        put(o, "wm_text", "");
        put(o, "wm_style", "plain");
        put(o, "intro", false);
        put(o, "intro_text", "");
        put(o, "intro_style", "plain");
        put(o, "intro_sec", 2);
        put(o, "outro", false);
        put(o, "outro_text", "");
        put(o, "outro_style", "plain");
        put(o, "outro_sec", 2);
        return o;
    }

    /** الإعدادات الحالية = الافتراضي + المحفوظ (أي مفتاح ناقص بياخد قيمته الافتراضية). */
    static JSONObject merge(JSONObject saved) {
        JSONObject o = defaults();
        java.util.Iterator<String> it = saved.keys();
        while (it.hasNext()) {
            String k = it.next();
            if (o.has(k)) put(o, k, saved.opt(k));
        }
        return o;
    }

    static JSONObject cfg(Context c) {
        return merge(obj(c, "cfg"));
    }

    static void saveCfg(Context c, JSONObject o) {
        saveObj(c, "cfg", o);
    }

    // ───────── مساعدات عامة ─────────
    static String ago(long t) {
        long s = Math.max(0, (System.currentTimeMillis() - t) / 1000);
        if (s < 60) return "دلوقتي";
        if (s < 3600) return (s / 60) + " د";
        if (s < 86400) return (s / 3600) + " س";
        return (s / 86400) + " يوم";
    }

    static String bytes(long b) {
        if (b < 1024) return b + " B";
        if (b < 1048576) return (b / 1024) + " KB";
        return String.format(java.util.Locale.US, "%.1f MB", b / 1048576.0);
    }

    static String host(String u) {
        try {
            String h = Uri.parse(u.trim()).getHost();
            return h == null ? "" : h.replaceFirst("^www\\.", "");
        } catch (Exception e) {
            return "";
        }
    }

    static void copy(Context c, String label, String text) {
        ClipboardManager cm = (ClipboardManager) c.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText(label, text));
        toast(c, "اتنسخ ✓");
    }

    static String paste(Context c) {
        ClipboardManager cm = (ClipboardManager) c.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip() != null && cm.getPrimaryClip().getItemCount() > 0) {
            CharSequence t = cm.getPrimaryClip().getItemAt(0).coerceToText(c);
            return t == null ? "" : t.toString().trim();
        }
        return "";
    }

    static void toast(Context c, String s) {
        Toast.makeText(c, s, Toast.LENGTH_SHORT).show();
    }

    // ───────── واجهة ─────────
    static int dp(Context c, int v) {
        return (int) (v * c.getResources().getDisplayMetrics().density + 0.5f);
    }

    static GradientDrawable shape(Context c, int fill, int r, int stroke, int strokeDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(c, r));
        if (strokeDp > 0) g.setStroke(dp(c, strokeDp), stroke);
        return g;
    }

    static TextView text(Context c, String s, float size, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    static LinearLayout card(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(c, 14), dp(c, 12), dp(c, 14), dp(c, 12));
        l.setBackground(shape(c, CARD, 20, STROKE, 1));
        return l;
    }

    static LinearLayout.LayoutParams lp(Context c, int w, int h, float wt, int mH, int mTop) {
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(w, h, wt);
        l.setMargins(dp(c, mH), dp(c, mTop), dp(c, mH), 0);
        return l;
    }

    static Button pill(Context c, String s, int fill, int txt) {
        Button b = new Button(c);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextColor(txt);
        b.setTextSize(13.5f);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setPadding(dp(c, 10), dp(c, 8), dp(c, 10), dp(c, 8));
        b.setBackground(shape(c, fill, 16, 0, 0));
        b.setStateListAnimator(null);
        return b;
    }

    static Button primary(Context c, String s) {
        Button b = pill(c, s, ACC, ON_ACC);
        b.setTextSize(15.5f);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        return b;
    }

    static Button soft(Context c, String s) {
        return pill(c, s, CARD2, TXT);
    }

    static EditText input(Context c, String hint, String val, int type, boolean single) {
        EditText e = new EditText(c);
        e.setText(val);
        e.setHint(hint);
        e.setInputType(type);
        e.setSingleLine(single);
        e.setTextColor(TXT);
        e.setHintTextColor(DIM);
        e.setTextSize(14);
        e.setBackground(shape(c, CARD2, 14, 0, 0));
        pad(e, 12, 10, 12, 10, c);
        return e;
    }

    // نسخة dp مبسّطة للبادينج (عشان نحافظ على السطر قصير فوق)
    static void pad(View v, int l, int t, int r, int b, Context c) {
        v.setPadding(dp(c, l), dp(c, t), dp(c, r), dp(c, b));
    }

    static Switch sw(Context c, boolean on) {
        Switch s = new Switch(c);
        s.setChecked(on);
        s.setShowText(false);
        int[][] st = {{android.R.attr.state_checked}, {}};
        s.setThumbTintList(new ColorStateList(st, new int[]{ACC, 0xFF8E7C6C}));
        s.setTrackTintList(new ColorStateList(st, new int[]{0x88E8A06A, 0xFF4A3C30}));
        return s;
    }

    static TextView iconChip(Context c, String emoji, int sizeDp) {
        TextView t = new TextView(c);
        t.setText(emoji);
        t.setTextSize(sizeDp * 0.45f);
        t.setGravity(Gravity.CENTER);
        t.setIncludeFontPadding(false);
        t.setBackground(shape(c, 0x2AE8A06A, sizeDp / 3, 0, 0));
        t.setLayoutParams(new LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp)));
        return t;
    }

    static void dimTree(View v, boolean on) {
        v.setAlpha(on ? 1f : 0.4f);
        v.setEnabled(on);
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) dimTree(g.getChildAt(i), on);
        }
    }

    interface Pick {
        void on(String value);
    }

    private static void paintChip(Context c, TextView t, boolean sel) {
        t.setTextColor(sel ? ON_ACC : DIM);
        t.setTypeface(sel ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        t.setBackground(shape(c, sel ? ACC : CARD2, 14, 0, 0));
    }

    /** صف اختيارات (شيبس) قابل للتمرير أفقيًا. أول اختيار دايمًا هو «البسيط». */
    static HorizontalScrollView chips(final Context c, final String[] labels, final String[] values, String cur, final Pick cb) {
        HorizontalScrollView hs = new HorizontalScrollView(c);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        final TextView[] tv = new TextView[labels.length];
        for (int i = 0; i < labels.length; i++) {
            final int k = i;
            TextView t = text(c, labels[i], 12.5f, DIM, false);
            pad(t, 12, 7, 12, 7, c);
            t.setOnClickListener(v -> {
                for (int j = 0; j < tv.length; j++) paintChip(c, tv[j], j == k);
                cb.on(values[k]);
            });
            tv[i] = t;
            paintChip(c, t, values[i].equals(cur));
            row.addView(t, lp(c, -2, -2, 0, 3, 2));
        }
        hs.addView(row);
        return hs;
    }

    static TextView cap(Context c, String s) {
        TextView t = text(c, s, 11, DIM, false);
        pad(t, 2, 6, 2, 2, c);
        return t;
    }

    /** كارت خاصية: أيقونة + عنوان + شرح + مفتاح تشغيل/إيقاف، وتحته الاختيارات (بتتعتّم لما الخاصية تتقفل). */
    static LinearLayout feature(final Context c, final JSONObject cfg, String icon, String title, String desc,
                                final String onKey, View... body) {
        LinearLayout card = card(c);
        LinearLayout head = new LinearLayout(c);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(iconChip(c, icon, 36));
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        pad(col, 10, 0, 8, 0, c);
        col.addView(text(c, title, 14.5f, TXT, true));
        if (desc != null && !desc.isEmpty()) col.addView(text(c, desc, 11, DIM, false));
        head.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
        final LinearLayout bodyBox = new LinearLayout(c);
        bodyBox.setOrientation(LinearLayout.VERTICAL);
        for (View v : body) bodyBox.addView(v);
        if (onKey != null) {
            final Switch s = sw(c, cfg.optBoolean(onKey, false));
            s.setOnCheckedChangeListener((b, on) -> {
                put(cfg, onKey, on);
                dimTree(bodyBox, on);
            });
            head.addView(s);
            head.setOnClickListener(v -> s.toggle());
            dimTree(bodyBox, s.isChecked());
        }
        card.addView(head);
        if (body.length > 0) card.addView(bodyBox);
        return card;
    }

    static AlertDialog.Builder dlg(Context c) {
        return new AlertDialog.Builder(c, android.R.style.Theme_DeviceDefault_Dialog_Alert);
    }

    /** مشغّل بسيط داخل التطبيق لمعاينة فيديو (من غير مكتبات). */
    static void play(Activity a, File f) {
        if (f == null || !f.isFile()) {
            toast(a, "الملف مش موجود.");
            return;
        }
        VideoView vv = new VideoView(a);
        MediaController mc = new MediaController(a);
        mc.setAnchorView(vv);
        vv.setMediaController(mc);
        vv.setVideoURI(Uri.fromFile(f));
        LinearLayout box = new LinearLayout(a);
        box.setBackgroundColor(0xFF000000);
        box.addView(vv, new LinearLayout.LayoutParams(-1, dp(a, 420)));
        vv.setOnPreparedListener(m -> vv.start());
        AlertDialog d = dlg(a).setView(box).setPositiveButton("قفل", null).create();
        d.setOnDismissListener(x -> vv.stopPlayback());
        d.show();
    }
}
