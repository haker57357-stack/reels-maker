package com.reelsmaker.app;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

/** تذكيرات النشر المجدولة: وقت ما يحل الموعد بيطلع إشعار بيفتح مركز النشر على الريل ده. بتتجدد بعد إعادة تشغيل الجهاز. */
public class ProRemind extends BroadcastReceiver {

    private static PendingIntent pi(Context c, JSONObject s) {
        Intent i = new Intent(c, ProRemind.class);
        i.putExtra("id", s.optString("id"));
        i.putExtra("job", s.optString("job"));
        i.putExtra("file", s.optString("file"));
        return PendingIntent.getBroadcast(c, s.optString("id").hashCode(), i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static void schedule(Context c, JSONObject s) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, s.optLong("t"), pi(c, s));
    }

    static void cancel(Context c, JSONObject s) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am != null) am.cancel(pi(c, s));
    }

    @Override public void onReceive(Context c, Intent in) {
        if (in == null) return;
        if (Intent.ACTION_BOOT_COMPLETED.equals(in.getAction())) {
            JSONArray a = ProKit.arr(c, "sched");
            long now = System.currentTimeMillis();
            for (int i = 0; i < a.length(); i++) {
                JSONObject s = a.optJSONObject(i);
                if (s != null && s.optLong("t") > now) schedule(c, s);
            }
            return;
        }
        String id = in.getStringExtra("id"), job = in.getStringExtra("job"), file = in.getStringExtra("file");
        if (id == null || job == null || file == null) return;
        ProService.channels(c);
        // شيل الموعد من القايمة
        JSONArray a = ProKit.arr(c, "sched");
        JSONArray n = new JSONArray();
        for (int i = 0; i < a.length(); i++) {
            JSONObject s = a.optJSONObject(i);
            if (s != null && !id.equals(s.optString("id"))) n.put(s);
        }
        ProKit.saveArr(c, "sched", n);
        Intent open = new Intent(c, ProShareActivity.class);
        open.putExtra("job", job);
        open.putExtra("file", file);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent p = PendingIntent.getActivity(c, id.hashCode(), open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, ProService.CH_DONE) : new Notification.Builder(c);
        b.setSmallIcon(android.R.drawable.ic_menu_upload).setContentTitle("⏰ وقت النشر")
                .setContentText("حان موعد نشر " + file + " — اضغط لفتح مركز النشر").setAutoCancel(true).setContentIntent(p);
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(id.hashCode(), b.build());
    }
}
