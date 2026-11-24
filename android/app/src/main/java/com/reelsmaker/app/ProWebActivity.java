package com.reelsmaker.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * موقع المنصة الرسمي جوه التطبيق (بوضع الكمبيوتر) بجلسة منفصلة لكل حساب. بتسجّل دخولك بإيدك،
 * ولو جاي من «مركز النشر» بيجهّز الوصف والفيديو (نافذة اختيار الملف بتاخد الفيديو تلقائيًا).
 */
public class ProWebActivity extends Activity {
    static final String DESKTOP_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

    ProPlat plat;
    String accId, job = "", file = "", caption = "", tags = "";
    JSONObject acc;
    WebView web;
    ProgressBar bar;
    TextView title;
    String mobileUa;
    boolean desktop = true;
    static final int[] VW = {0, 1100, 1400, 1800};
    int vwIdx = 2;   // 1400: يجبر المواقع على تخطيط الكمبيوتر الحقيقي (إنستجرام بيعرض شكل الموبايل لو العرض أقل من ~1000)
    ValueCallback<Uri[]> fileCb;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(ProKit.BG);
        getWindow().setNavigationBarColor(ProKit.BG);
        Intent in = getIntent();
        plat = ProPlat.by(in.getStringExtra("pid"));
        accId = in.getStringExtra("acc") == null ? "" : in.getStringExtra("acc");
        if (in.getStringExtra("job") != null) job = in.getStringExtra("job");
        if (in.getStringExtra("file") != null) file = in.getStringExtra("file");
        if (in.getStringExtra("cap") != null) caption = in.getStringExtra("cap");
        if (in.getStringExtra("tags") != null) tags = in.getStringExtra("tags");
        desktop = ProKit.sp(this).getBoolean("desk", true);
        loadAcc();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(ProKit.BG);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        ProKit.pad(top, 10, 6, 10, 6, this);
        TextView back = ProKit.text(this, "›", 26, ProKit.ACC, true);
        ProKit.pad(back, 2, 0, 12, 0, this);
        back.setOnClickListener(v -> onBackPressed());
        top.addView(back);
        title = ProKit.text(this, plat.icon + " " + plat.name + " — " + acc.optString("name", "حساب"), 14, ProKit.TXT, true);
        title.setSingleLine(true);
        top.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        Button up = ProKit.soft(this, "📤 الرفع");
        up.setOnClickListener(v -> web.loadUrl(plat.upload));
        Button dk = ProKit.soft(this, desktop ? "🖥" : "📱");
        dk.setOnClickListener(v -> {
            desktop = !desktop;
            ProKit.sp(this).edit().putBoolean("desk", desktop).apply();
            dk.setText(desktop ? "🖥" : "📱");
            applyUa();
            web.reload();
        });
        final Button zm = ProKit.soft(this, "🔍");
        zm.setOnClickListener(v -> {
            vwIdx = (vwIdx + 1) % VW.length;
            if (VW[vwIdx] == 0) {
                ProKit.toast(this, "عرض الصفحة: الأصلي");
                web.reload();
            } else {
                ProKit.toast(this, "عرض الصفحة: " + VW[vwIdx] + " (اضغط تاني لتصغير أكتر)");
                applyViewport();
            }
        });
        Button ext = ProKit.soft(this, "🌐");
        ext.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(web.getUrl() == null ? plat.upload : web.getUrl())));
            } catch (Throwable ignored) { }
        });
        top.addView(up, ProKit.lp(this, -2, -2, 0, 2, 0));
        top.addView(zm, ProKit.lp(this, -2, -2, 0, 2, 0));
        top.addView(dk, ProKit.lp(this, -2, -2, 0, 2, 0));
        top.addView(ext, ProKit.lp(this, -2, -2, 0, 2, 0));
        root.addView(top);

        bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        bar.setProgressTintList(android.content.res.ColorStateList.valueOf(ProKit.ACC));
        root.addView(bar, new LinearLayout.LayoutParams(-1, ProKit.dp(this, 3)));

        web = new WebView(this);
        setupWeb();
        root.addView(web, new LinearLayout.LayoutParams(-1, 0, 1f));

        if (!file.isEmpty()) root.addView(assistBar());
        setContentView(root);

        if (!job.isEmpty()) {
            if (acc.optBoolean("locked")) {
                ProKit.toast(this, "الحساب ده مقفول للنشر. افتحه من «المنصات والحسابات».");
                finish();
                return;
            }
            int day = 0;
            JSONArray ps = acc.optJSONArray("posts");
            long lim = System.currentTimeMillis() - 86400000L;
            if (ps != null) for (int i = 0; i < ps.length(); i++) if (ps.optLong(i) > lim) day++;
            if (day >= plat.soft) ProKit.toast(this, "⚠️ نشرت " + day + " النهاردة على الحساب ده — الإكثار ممكن يقيّد الحساب (تنبيه تقريبي).");
            if (alreadyPosted()) {
                ProKit.dlg(this).setTitle("اتنشر قبل كده").setMessage("الريل ده اتنشر على الحساب ده قبل كده. تكمل برضه؟")
                        .setPositiveButton("أكمل", null).setNegativeButton("ارجع", (d, w) -> finish()).show();
            }
        }
        startSession();
    }

    // ───────── الحساب والجلسة ─────────
    void loadAcc() {
        JSONArray accs = ProAccountsActivity.accounts(this, plat.id);
        acc = null;
        for (int i = 0; i < accs.length(); i++) {
            JSONObject o = accs.optJSONObject(i);
            if (o != null && o.optString("id").equals(accId)) acc = o;
        }
        if (acc == null && accs.length() > 0) {
            acc = accs.optJSONObject(0);
            accId = acc == null ? "" : acc.optString("id");
        }
        if (acc == null) {
            acc = new JSONObject();
            accId = ProKit.newId();
            ProKit.put(acc, "id", accId);
            ProKit.put(acc, "name", "حساب 1");
            ProKit.put(acc, "locked", false);
            ProKit.put(acc, "ck", new JSONObject());
            ProKit.put(acc, "posts", new JSONArray());
            accs.put(acc);
            ProKit.saveArr(this, "accs_" + plat.id, accs);
        }
    }

    void saveAcc() {
        JSONArray accs = ProAccountsActivity.accounts(this, plat.id);
        for (int i = 0; i < accs.length(); i++) {
            JSONObject o = accs.optJSONObject(i);
            if (o != null && o.optString("id").equals(accId)) {
                try {
                    accs.put(i, acc);
                } catch (Exception ignored) { }
            }
        }
        ProKit.saveArr(this, "accs_" + plat.id, accs);
    }

    /** بنمسح جلسة المتصفح ونحط جلسة الحساب ده بس (عشان الحسابات مايتلخبطوش) وبعدين نفتح الموقع. */
    void startSession() {
        final CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(web, true);
        WebStorage.getInstance().deleteAllData();
        cm.removeAllCookies(ok -> {
            JSONObject ck = acc.optJSONObject("ck");
            if (ck != null) {
                java.util.Iterator<String> it = ck.keys();
                while (it.hasNext()) {
                    String u = it.next();
                    String all = ck.optString(u);
                    for (String pair : all.split(";\\s*")) {
                        if (pair.contains("=")) cm.setCookie(u, pair + "; Path=/");
                    }
                }
            }
            cm.flush();
            runOnUiThread(() -> web.loadUrl(job.isEmpty() ? plat.upload : plat.upload));
        });
    }

    void snapshot() {
        try {
            CookieManager cm = CookieManager.getInstance();
            JSONObject ck = new JSONObject();
            boolean any = false;
            for (String u : plat.cookieUrls) {
                String s = cm.getCookie(u);
                if (s != null && !s.isEmpty()) {
                    ProKit.put(ck, u, s);
                    any = true;
                }
            }
            if (any) {
                ProKit.put(acc, "ck", ck);
                ProKit.put(acc, "last", System.currentTimeMillis());
                saveAcc();
            }
            cm.flush();
        } catch (Throwable ignored) { }
    }

    // ───────── WebView ─────────
    /** نفس نسخة Chromium الحقيقية للجهاز (WebView القديم) بدل رقم وهمي، عشان المواقع ماتتعاملش معاه غلط. */
    String desktopUa() {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("Chrome/([0-9.]+)").matcher(mobileUa == null ? "" : mobileUa);
        String v = m.find() ? m.group(1) : "124.0.0.0";
        return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/" + v + " Safari/537.36";
    }

    /** بيوسّع عرض الصفحة المنطقي عشان نوافذ الموقع الكبيرة (زي تعديل الريل) تظهر كاملة بزرارها على الشاشة الصغيرة. */
    void applyViewport() {
        if (!desktop || vwIdx <= 0 || VW[vwIdx] == 0 || web == null) return;
        String js = "(function(){var m=document.querySelector('meta[name=viewport]');if(!m){m=document.createElement('meta');m.name='viewport';document.head.appendChild(m);}m.content='width=" + VW[vwIdx] + "';})();";
        web.evaluateJavascript(js, null);
    }

    void applyUa() {
        WebSettings s = web.getSettings();
        s.setUserAgentString(desktop ? desktopUa() : mobileUa);
        s.setUseWideViewPort(desktop);
        s.setLoadWithOverviewMode(desktop);
    }

    void setupWeb() {
        WebSettings s = web.getSettings();
        mobileUa = s.getUserAgentString();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setAllowFileAccess(false);
        s.setSupportMultipleWindows(false);
        applyUa();
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                String sc = r.getUrl().getScheme();
                return sc != null && !sc.equals("http") && !sc.equals("https");
            }

            @Override public void onPageStarted(WebView v, String u, Bitmap f) {
                bar.setVisibility(View.VISIBLE);
            }

            @Override public void onPageFinished(WebView v, String u) {
                bar.setVisibility(View.GONE);
                applyViewport();
                v.postDelayed(() -> applyViewport(), 1200);
                snapshot();
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView v, int p) {
                bar.setProgress(p);
            }

            @Override public boolean onShowFileChooser(WebView w, ValueCallback<Uri[]> cb, FileChooserParams fp) {
                if (fileCb != null) fileCb.onReceiveValue(null);
                fileCb = cb;
                Uri reel = reelUri();
                if (reel != null && wantsVideo(fp)) {
                    fileCb.onReceiveValue(new Uri[]{reel});
                    fileCb = null;
                    ProKit.toast(ProWebActivity.this, "الفيديو اتحط تلقائيًا ✓");
                    return true;
                }
                try {
                    startActivityForResult(fp.createIntent(), 77);
                } catch (Exception e) {
                    fileCb = null;
                    return false;
                }
                return true;
            }
        });
    }

    boolean wantsVideo(WebChromeClient.FileChooserParams fp) {
        String[] t = fp.getAcceptTypes();
        if (t == null || t.length == 0) return true;
        for (String s : t) {
            if (s == null || s.isEmpty() || s.contains("video") || s.contains("*/*") || s.contains("mp4")) return true;
        }
        return false;
    }

    Uri reelUri() {
        if (job.isEmpty() || file.isEmpty()) return null;
        File src = new File(ProKit.jobDir(this, job), file);
        if (!src.isFile()) return null;
        File dst = new File(ProProvider.shareDir(this), file);
        try {
            if (!dst.isFile() || dst.length() != src.length()) {
                try (InputStream in = new FileInputStream(src); OutputStream out = new FileOutputStream(dst)) {
                    byte[] buf = new byte[65536];
                    int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                }
            }
        } catch (Exception e) {
            return null;
        }
        return ProProvider.uriFor(dst);
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        if (req == 77 && fileCb != null) {
            fileCb.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(res, data));
            fileCb = null;
            return;
        }
        super.onActivityResult(req, res, data);
    }

    // ───────── شريط المساعدة السفلي (مركز النشر) ─────────
    View assistBar() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        ProKit.pad(box, 10, 8, 10, 10, this);
        box.setBackground(ProKit.shape(this, ProKit.CARD, 0, 0, 0));
        box.addView(ProKit.text(this, "🎞 " + file + (reelUri() != null ? " — جاهز: نافذة اختيار الملف هتاخده لوحدها" : " — الملف مش موجود!"), 11.5f, ProKit.DIM, false));
        LinearLayout r1 = new LinearLayout(this);
        Button c1 = ProKit.soft(this, "📋 الوصف");
        c1.setOnClickListener(v -> ProKit.copy(this, "caption", caption));
        Button c2 = ProKit.soft(this, "# هاشتاجات");
        c2.setOnClickListener(v -> ProKit.copy(this, "tags", tags));
        Button c3 = ProKit.soft(this, "الكل");
        c3.setOnClickListener(v -> ProKit.copy(this, "all", (caption + (tags.isEmpty() ? "" : "\n\n" + tags)).trim()));
        r1.addView(c1, ProKit.lp(this, 0, -2, 1f, 2, 6));
        r1.addView(c2, ProKit.lp(this, 0, -2, 1f, 2, 6));
        r1.addView(c3, ProKit.lp(this, 0, -2, 1f, 2, 6));
        box.addView(r1);
        LinearLayout r2 = new LinearLayout(this);
        Button done = ProKit.pill(this, "✅ تم النشر", ProKit.OK, ProKit.ON_ACC);
        done.setOnClickListener(v -> mark("done"));
        Button skip = ProKit.soft(this, "⏭ تخطّي");
        skip.setOnClickListener(v -> mark("skip"));
        r2.addView(done, ProKit.lp(this, 0, -2, 1.4f, 2, 6));
        r2.addView(skip, ProKit.lp(this, 0, -2, 1f, 2, 6));
        box.addView(r2);
        return box;
    }

    boolean alreadyPosted() {
        JSONArray p = ProKit.arr(this, "pubs");
        for (int i = 0; i < p.length(); i++) {
            JSONObject o = p.optJSONObject(i);
            if (o != null && job.equals(o.optString("job")) && file.equals(o.optString("file")) && plat.id.equals(o.optString("pid"))
                    && accId.equals(o.optString("acc")) && "done".equals(o.optString("s"))) return true;
        }
        return false;
    }

    void mark(String state) {
        JSONArray p = ProKit.arr(this, "pubs");
        JSONObject o = new JSONObject();
        ProKit.put(o, "job", job);
        ProKit.put(o, "file", file);
        ProKit.put(o, "pid", plat.id);
        ProKit.put(o, "acc", accId);
        ProKit.put(o, "s", state);
        ProKit.put(o, "t", System.currentTimeMillis());
        p.put(o);
        JSONArray keep = new JSONArray();
        int from = Math.max(0, p.length() - 600);
        for (int i = from; i < p.length(); i++) keep.put(p.opt(i));
        ProKit.saveArr(this, "pubs", keep);
        if ("done".equals(state)) {
            JSONArray ps = acc.optJSONArray("posts");
            if (ps == null) ps = new JSONArray();
            ps.put(System.currentTimeMillis());
            JSONArray n = new JSONArray();
            for (int i = Math.max(0, ps.length() - 40); i < ps.length(); i++) n.put(ps.opt(i));
            ProKit.put(acc, "posts", n);
            saveAcc();
        }
        snapshot();
        setResult(RESULT_OK);
        finish();
    }

    @Override public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    @Override protected void onPause() {
        snapshot();
        super.onPause();
    }

    @Override protected void onDestroy() {
        if (fileCb != null) {
            fileCb.onReceiveValue(null);
            fileCb = null;
        }
        if (web != null) {
            web.stopLoading();
            web.destroy();
        }
        super.onDestroy();
    }
}
