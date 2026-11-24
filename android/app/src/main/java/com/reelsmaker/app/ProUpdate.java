package com.reelsmaker.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** زر التحديث: بيقارن رقم بناء النسخة المثبّتة بآخر إصدار APK على المستودع، وينزّله ويفتح شاشة التثبيت (نفس مفتاح التوقيع فبيتثبّت فوق القديمة). */
final class ProUpdate {
    private ProUpdate() { }

    static final class Info {
        String runId = "", assetUrl = "";
        long size;
        boolean newer;
    }

    /** null = مفيش إصدار منشور. Info.newer = في نسخة أحدث من اللي معاك. */
    static Info check() throws Exception {
        JSONObject rel = ProNet.latestApk();
        if (rel == null) return null;
        Info i = new Info();
        Matcher m = Pattern.compile("run:(\\d+)").matcher(rel.optString("body", ""));
        if (m.find()) i.runId = m.group(1);
        JSONArray a = rel.optJSONArray("assets");
        if (a != null) {
            for (int k = 0; k < a.length(); k++) {
                JSONObject as = a.optJSONObject(k);
                if (as != null && as.optString("name").endsWith(".apk")) {
                    i.assetUrl = as.optString("url");
                    i.size = as.optLong("size");
                }
            }
        }
        i.newer = !i.assetUrl.isEmpty() && !i.runId.isEmpty() && !i.runId.equals(BuildConfig.RUN_ID);
        return i;
    }

    /** بيتنادى من خيط خلفي. بيرجّع null لو تمام، أو رسالة الخطأ. */
    static String downloadAndInstall(final Activity a, Info i, ProNet.Prog p) {
        try {
            File out = new File(ProProvider.shareDir(a), "ReelsMaker.apk");
            ProNet.download(i.assetUrl, out, i.size, p);
            a.runOnUiThread(() -> install(a, out));
            return null;
        } catch (Exception e) {
            return "التنزيل فشل: " + e.getClass().getSimpleName() + " — جرّب تاني.";
        }
    }

    static void install(Activity a, File apk) {
        if (Build.VERSION.SDK_INT >= 26 && !a.getPackageManager().canRequestPackageInstalls()) {
            ProKit.toast(a, "اسمح للتطبيق بتثبيت التحديثات من الإعدادات ثم اضغط تحديث تاني.");
            a.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + a.getPackageName())));
            return;
        }
        Intent in = new Intent(Intent.ACTION_VIEW);
        in.setDataAndType(ProProvider.uriFor(apk), "application/vnd.android.package-archive");
        in.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            a.startActivity(in);
        } catch (Throwable t) {
            ProKit.toast(a, "مقدرتش أفتح شاشة التثبيت.");
        }
    }
}
