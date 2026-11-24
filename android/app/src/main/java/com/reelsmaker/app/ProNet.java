package com.reelsmaker.app;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** اتصال «ميديا برو» بـ GitHub: تشغيل pro.yml • متابعة الإصدار • تنزيل مع استكمال. (بيستخدم Gh القديم من غير ما يعدّله) */
final class ProNet {
    private ProNet() { }

    interface Prog {
        void on(long done, long total);
    }

    /** مجموعة 404 عندنا ليها معنى خاص: pro.yml مش موجود في المستودع. */
    static void checkWf(String[] x) throws Gh.Fatal, Gh.Transient {
        if ("404".equals(x[0])) {
            throw new Gh.Fatal("ملف pro.yml مش موجود في المستودع " + Gh.REPO + ". شغّل أمر التحديث الجديد (termux-update.sh) وهيرفعه.");
        }
        Gh.check(x);
    }

    static void dispatch(String job, String cfgJson) throws Exception {
        JSONObject in = new JSONObject();
        in.put("job", job);
        in.put("cfg", cfgJson);
        String body = new JSONObject().put("ref", "main").put("inputs", in).toString();
        String[] x = Gh.http("POST", Gh.API + "/repos/" + Gh.REPO + "/actions/workflows/pro.yml/dispatches", body);
        checkWf(x);
    }

    /** بيرجّع حالة التشغيل بتاعنا: {"status","conclusion"} أو null لو لسه ما ظهرش. */
    static JSONObject findRun(String job) throws Exception {
        String[] x = Gh.http("GET", Gh.API + "/repos/" + Gh.REPO + "/actions/workflows/pro.yml/runs?per_page=30", null);
        checkWf(x);
        JSONArray a = new JSONObject(x[1]).getJSONArray("workflow_runs");
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.getJSONObject(i);
            if (("pro " + job).equals(o.optString("display_title"))) return o;
        }
        return null;
    }

    /** إصدار النتيجة (pro-JOB) أو null لو لسه ما اتعملش. */
    static JSONObject release(String job) throws Exception {
        String[] x = Gh.http("GET", Gh.API + "/repos/" + Gh.REPO + "/releases/tags/pro-" + job, null);
        if ("404".equals(x[0])) return null;
        Gh.check(x);
        return new JSONObject(x[1]);
    }

    /** ملاحظات الإصدار = JSON حالة السيرفر (pct/msg/files/warn/stage). */
    static JSONObject status(JSONObject rel) {
        String b = rel.optString("body", "").trim();
        return ProKit.parseObj(b);
    }

    static JSONObject latestApk() throws Exception {
        String[] x = Gh.http("GET", Gh.API + "/repos/" + Gh.REPO + "/releases/tags/apk-latest", null);
        if ("404".equals(x[0])) return null;
        Gh.check(x);
        return new JSONObject(x[1]);
    }

    static JSONObject repoInfo() throws Exception {
        String[] x = Gh.http("GET", Gh.API + "/repos/" + Gh.REPO, null);
        Gh.check(x);
        return new JSONObject(x[1]);
    }

    /** تنزيل أصل إصدار (asset) مع استكمال لو النت قطع. dest = الملف النهائي. */
    static void download(String assetApiUrl, File dest, long size, Prog p) throws IOException {
        File part = new File(dest.getPath() + ".part");
        IOException last = null;
        for (int attempt = 0; attempt < 6; attempt++) {
            try {
                long have = part.exists() ? part.length() : 0;
                if (size > 0 && have > size) {
                    part.delete();
                    have = 0;
                }
                if (size > 0 && have == size) break;
                HttpURLConnection c = Gh.open("GET", assetApiUrl, true);
                c.setRequestProperty("Accept", "application/octet-stream");
                c.setInstanceFollowRedirects(false);
                int code = c.getResponseCode();
                HttpURLConnection d;
                boolean append = have > 0;
                if (code >= 300 && code < 400 && c.getHeaderField("Location") != null) {
                    String loc = c.getHeaderField("Location");
                    c.disconnect();
                    d = (HttpURLConnection) new URL(loc).openConnection();
                    d.setConnectTimeout(20000);
                    d.setReadTimeout(40000);
                    d.setRequestProperty("User-Agent", "ReelsMaker-App");
                    if (have > 0) d.setRequestProperty("Range", "bytes=" + have + "-");
                    int dc = d.getResponseCode();
                    if (dc == 200 && have > 0) {      // السيرفر تجاهل Range: نبدأ من الأول
                        append = false;
                    } else if (dc != 200 && dc != 206) {
                        d.disconnect();
                        throw new IOException("download http " + dc);
                    }
                } else if (code == 200) {
                    d = c;
                    append = false;
                } else {
                    c.disconnect();
                    throw new IOException("download http " + code);
                }
                long done = append ? have : 0;
                try (InputStream in = d.getInputStream();
                     FileOutputStream out = new FileOutputStream(part, append)) {
                    byte[] buf = new byte[32768];
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        out.write(buf, 0, n);
                        done += n;
                        if (p != null) p.on(done, size);
                    }
                } finally {
                    d.disconnect();
                }
                last = null;
                break;
            } catch (IOException e) {
                last = e;
                try {
                    Thread.sleep(2000L * (attempt + 1));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IOException("interrupted");
                }
            }
        }
        if (last != null) throw last;
        if (size > 0 && part.length() != size) throw new IOException("size mismatch");
        if (dest.exists()) dest.delete();
        if (!part.renameTo(dest)) throw new IOException("rename failed");
    }

    /** إلغاء التشغيل على السيرفر (لو لسه شغّال). */
    static void cancel(String job) throws Exception {
        JSONObject run = findRun(job);
        if (run == null) return;
        String[] x = Gh.http("POST", Gh.API + "/repos/" + Gh.REPO + "/actions/runs/" + run.optLong("id") + "/cancel", "{}");
        if (!"202".equals(x[0]) && !"409".equals(x[0])) Gh.check(x);
    }
}
