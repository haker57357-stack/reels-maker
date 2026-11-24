package com.reelsmaker.app;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

/** الشاشة الرئيسية لـ «ميديا برو»: كل الأقسام + بانر التحديث. (قسم جديد جوه نفس التطبيق) */
public class ProActivity extends Activity {
    SharedPreferences sp;
    TextView sub, upMsg;
    Button upBtn;
    LinearLayout upCard;
    ProUpdate.Info info;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        sp = ProKit.sp(this);
        getWindow().setStatusBarColor(ProKit.BG);
        getWindow().setNavigationBarColor(ProKit.BG);
        ProService.channels(this);
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 11);
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(ProKit.BG);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        ProKit.pad(root, 16, 12, 16, 16, this);

        LinearLayout hd = new LinearLayout(this);
        hd.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = ProKit.text(this, "›", 30, ProKit.ACC, true);
        ProKit.pad(back, 2, 0, 14, 0, this);
        back.setOnClickListener(v -> finish());
        hd.addView(back);
        LinearLayout tt = new LinearLayout(this);
        tt.setOrientation(LinearLayout.VERTICAL);
        tt.addView(ProKit.text(this, "ميديا برو ✨", 24, ProKit.TXT, true));
        sub = ProKit.text(this, "", 12, ProKit.DIM, false);
        tt.addView(sub);
        hd.addView(tt, new LinearLayout.LayoutParams(0, -2, 1f));
        root.addView(hd);

        // بانر التحديث
        upCard = ProKit.card(this);
        upCard.setBackground(ProKit.shape(this, 0x22E8A06A, 18, ProKit.ACC, 1));
        upMsg = ProKit.text(this, "", 13, ProKit.TXT, false);
        upBtn = ProKit.primary(this, "⬆️ حدّث التطبيق");
        upBtn.setOnClickListener(v -> doUpdate());
        upCard.addView(upMsg);
        upCard.addView(upBtn, ProKit.lp(this, -1, -2, 0, 0, 8));
        upCard.setVisibility(View.GONE);
        root.addView(upCard, ProKit.lp(this, -1, -2, 0, 0, 12));

        root.addView(tile("🎬", "إنشاء ريلز", "قص بالطول أو المشهد أو الجمل… كل ميزة ببسيط وبدايل", ProCreateActivity.class, true), ProKit.lp(this, -1, -2, 0, 0, 12));
        root.addView(tile("🌐", "المنصات والحسابات", "10 منصات — سجّل دخولك الرسمي جوه التطبيق", ProAccountsActivity.class, false), ProKit.lp(this, -1, -2, 0, 0, 10));
        root.addView(tile("📤", "مركز النشر والجدولة", "شير بضغطة • تذكيرات • نشر متتابع", ProShareActivity.class, false), ProKit.lp(this, -1, -2, 0, 0, 10));
        root.addView(tile("🗂", "السجل والمشاريع", "ريلزك • إعادة تشغيل • إحصائيات • تنظيف", ProLibActivity.class, false), ProKit.lp(this, -1, -2, 0, 0, 10));
        root.addView(tile("🔧", "الفحص والإعدادات", "فحص الاتصال • نسخة احتياطية • حدود الدقايق", ProToolsActivity.class, false), ProKit.lp(this, -1, -2, 0, 0, 10));

        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        sv.setBackgroundColor(ProKit.BG);
        sv.addView(root);
        setContentView(sv);
    }

    View tile(String icon, String title, String desc, final Class<?> cls, boolean hero) {
        LinearLayout t = new LinearLayout(this);
        t.setGravity(Gravity.CENTER_VERTICAL);
        ProKit.pad(t, 14, hero ? 16 : 12, 14, hero ? 16 : 12, this);
        t.setBackground(ProKit.shape(this, hero ? ProKit.ACC : ProKit.CARD, 20, ProKit.STROKE, hero ? 0 : 1));
        TextView ic = ProKit.iconChip(this, icon, 44);
        t.addView(ic);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        ProKit.pad(col, 12, 0, 8, 0, this);
        col.addView(ProKit.text(this, title, 16.5f, hero ? ProKit.ON_ACC : ProKit.TXT, true));
        col.addView(ProKit.text(this, desc, 11.5f, hero ? 0xFF5A3A22 : ProKit.DIM, false));
        t.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
        t.addView(ProKit.text(this, "‹", 24, hero ? ProKit.ON_ACC : ProKit.ACC, true));
        t.setOnClickListener(v -> startActivity(new Intent(this, cls)));
        return t;
    }

    @Override protected void onResume() {
        super.onResume();
        int n = ProKit.arr(this, "hist").length();
        String st = sp.getString("r_stage", "");
        String run = "NEW".equals(st) || "RUN".equals(st) ? " • ⏳ فيه عملية شغّالة" : "";
        sub.setText("ريلز اتعملت: " + n + run);
        checkUpdate();
    }

    void checkUpdate() {
        if (Gh.TOK.isEmpty() || Gh.REPO.isEmpty()) return;
        new Thread(() -> {
            try {
                final ProUpdate.Info i = ProUpdate.check();
                runOnUiThread(() -> {
                    info = i;
                    if (i != null && i.newer) {
                        upMsg.setText("📣 فيه نسخة أحدث من التطبيق جاهزة. اضغط تحديث وهتتثبّت فوق القديمة من غير ما تخسر حاجة.");
                        upCard.setVisibility(View.VISIBLE);
                    } else {
                        upCard.setVisibility(View.GONE);
                    }
                });
            } catch (Exception ignored) { }
        }).start();
    }

    void doUpdate() {
        if (info == null || !info.newer) return;
        upBtn.setEnabled(false);
        upBtn.setText("بنزّل التحديث...");
        final ProUpdate.Info i = info;
        new Thread(() -> {
            final String err = ProUpdate.downloadAndInstall(this, i, (done, total) -> {
                if (total > 0) runOnUiThread(() -> upBtn.setText("بنزّل التحديث... " + (done * 100 / total) + "%"));
            });
            runOnUiThread(() -> {
                upBtn.setEnabled(true);
                upBtn.setText("⬆️ حدّث التطبيق");
                if (err != null) upMsg.setText("❌ " + err);
            });
        }).start();
    }
}
