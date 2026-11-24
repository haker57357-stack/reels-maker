package com.reelsmaker.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
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

/** المنصات والحسابات: كارت لكل منصة (وصف + حالة + فتح) وأكتر من حساب لكل منصة بجلسة منفصلة. */
public class ProAccountsActivity extends Activity {
    LinearLayout list;

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
        hd.addView(ProKit.text(this, "المنصات والحسابات", 20, ProKit.TXT, true));
        root.addView(hd);
        root.addView(ProKit.cap(this, "  بتدخل بحسابك من موقع المنصة الرسمي جوه التطبيق كأنك فاتحه من كمبيوتر. التطبيق مابيحفظش كلمات السر — بيحتفظ بجلسة الدخول بس على موبايلك."));
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        ProKit.pad(list, 14, 4, 14, 14, this);
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

    static JSONArray accounts(android.content.Context c, String pid) {
        return ProKit.arr(c, "accs_" + pid);
    }

    void build() {
        list.removeAllViews();
        for (ProPlat p : ProPlat.ALL) {
            final ProPlat pl = p;
            JSONArray accs = accounts(this, pl.id);
            String act = ProKit.sp(this).getString("act_" + pl.id, "");
            LinearLayout card = ProKit.card(this);
            LinearLayout head = new LinearLayout(this);
            head.setGravity(Gravity.CENTER_VERTICAL);
            head.addView(ProKit.iconChip(this, pl.icon, 42));
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            ProKit.pad(col, 12, 0, 8, 0, this);
            col.addView(ProKit.text(this, pl.name, 16, ProKit.TXT, true));
            col.addView(ProKit.text(this, pl.desc, 11.5f, ProKit.DIM, false));
            String status = accs.length() == 0 ? "لسه مفيش حساب" : "● " + accs.length() + (accs.length() == 1 ? " حساب" : " حسابات");
            col.addView(ProKit.text(this, status, 11.5f, accs.length() == 0 ? ProKit.DIM : ProKit.OK, false));
            head.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
            card.addView(head);

            for (int i = 0; i < accs.length(); i++) {
                final JSONObject a = accs.optJSONObject(i);
                if (a == null) continue;
                LinearLayout r = new LinearLayout(this);
                r.setGravity(Gravity.CENTER_VERTICAL);
                ProKit.pad(r, 0, 6, 0, 0, this);
                boolean isAct = a.optString("id").equals(act) || (act.isEmpty() && i == 0);
                String nm = (isAct ? "★ " : "") + a.optString("name") + (a.optBoolean("locked") ? "  🔒" : "");
                TextView t = ProKit.text(this, nm, 13.5f, a.optBoolean("locked") ? ProKit.DIM : ProKit.TXT, isAct);
                r.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
                Button open = ProKit.soft(this, "افتح");
                open.setOnClickListener(v -> openWeb(pl, a.optString("id")));
                Button more = ProKit.soft(this, "⋯");
                more.setOnClickListener(v -> accountMenu(pl, a));
                r.addView(open, ProKit.lp(this, -2, -2, 0, 2, 0));
                r.addView(more, ProKit.lp(this, -2, -2, 0, 2, 0));
                card.addView(r);
            }
            LinearLayout br = new LinearLayout(this);
            Button login = ProKit.primary(this, accs.length() == 0 ? "تسجيل دخول" : "＋ حساب جديد");
            login.setTextSize(13.5f);
            login.setOnClickListener(v -> newAccount(pl));
            br.addView(login, ProKit.lp(this, 0, -2, 1f, 0, 10));
            card.addView(br);
            list.addView(card, ProKit.lp(this, -1, -2, 0, 0, 10));
        }
    }

    void openWeb(ProPlat p, String accId) {
        ProKit.sp(this).edit().putString("act_" + p.id, accId).apply();
        startActivity(new Intent(this, ProWebActivity.class).putExtra("pid", p.id).putExtra("acc", accId));
    }

    void newAccount(final ProPlat p) {
        final EditText e = ProKit.input(this, "اسم الحساب (مثلًا: حسابي الأساسي)", "", InputType.TYPE_CLASS_TEXT, true);
        ProKit.dlg(this).setTitle("حساب جديد على " + p.name).setView(e)
                .setPositiveButton("فتح تسجيل الدخول", (d, w) -> {
                    String n = e.getText().toString().trim();
                    if (n.isEmpty()) n = "حساب " + (accounts(this, p.id).length() + 1);
                    JSONArray accs = accounts(this, p.id);
                    JSONObject a = new JSONObject();
                    ProKit.put(a, "id", ProKit.newId());
                    ProKit.put(a, "name", n);
                    ProKit.put(a, "locked", false);
                    ProKit.put(a, "ck", new JSONObject());
                    ProKit.put(a, "posts", new JSONArray());
                    accs.put(a);
                    ProKit.saveArr(this, "accs_" + p.id, accs);
                    ProKit.put(a, "fresh", true);
                    openWeb(p, a.optString("id"));
                }).setNegativeButton("إلغاء", null).show();
    }

    void accountMenu(final ProPlat p, final JSONObject a) {
        String[] items = {a.optBoolean("locked") ? "🔓 فتح القفل (يسمح بالنشر)" : "🔒 قفل الحساب مؤقتًا (يمنع النشر منه)",
                "✏️ تغيير الاسم", "★ خليه الحساب الأساسي", "🗑 حذف الحساب وجلسته"};
        ProKit.dlg(this).setTitle(a.optString("name")).setItems(items, (d, i) -> {
            JSONArray accs = accounts(this, p.id);
            for (int k = 0; k < accs.length(); k++) {
                JSONObject o = accs.optJSONObject(k);
                if (o == null || !o.optString("id").equals(a.optString("id"))) continue;
                if (i == 0) {
                    ProKit.put(o, "locked", !o.optBoolean("locked"));
                } else if (i == 2) {
                    ProKit.sp(this).edit().putString("act_" + p.id, o.optString("id")).apply();
                } else if (i == 3) {
                    JSONArray n = new JSONArray();
                    for (int j = 0; j < accs.length(); j++) if (j != k) n.put(accs.optJSONObject(j));
                    accs = n;
                } else if (i == 1) {
                    renameDialog(p, o.optString("id"), o.optString("name"));
                    return;
                }
                break;
            }
            ProKit.saveArr(this, "accs_" + p.id, accs);
            build();
        }).setNegativeButton("قفل", null).show();
    }

    void renameDialog(final ProPlat p, final String id, String cur) {
        final EditText e = ProKit.input(this, "الاسم", cur, InputType.TYPE_CLASS_TEXT, true);
        ProKit.dlg(this).setTitle("تغيير الاسم").setView(e).setPositiveButton("حفظ", (d, w) -> {
            JSONArray accs = accounts(this, p.id);
            for (int k = 0; k < accs.length(); k++) {
                JSONObject o = accs.optJSONObject(k);
                if (o != null && id.equals(o.optString("id")) && !e.getText().toString().trim().isEmpty()) {
                    ProKit.put(o, "name", e.getText().toString().trim());
                }
            }
            ProKit.saveArr(this, "accs_" + p.id, accs);
            build();
        }).setNegativeButton("إلغاء", null).show();
    }
}
