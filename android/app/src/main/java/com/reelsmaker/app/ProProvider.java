package com.reelsmaker.app;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

/** بيدّي تطبيقات النظام صلاحية قراءة ملف واحد محدد (تحديث التطبيق أو ريل للمشاركة) من مجلد مؤقت جوه التطبيق. */
public class ProProvider extends ContentProvider {
    static final String AUTH = "com.reelsmaker.app.pro";

    static File shareDir(Context c) {
        File d = new File(c.getCacheDir(), "proshare");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    static Uri uriFor(File f) {
        return Uri.parse("content://" + AUTH + "/" + f.getName());
    }

    private File fileOf(Uri u) throws FileNotFoundException {
        String n = u.getLastPathSegment();
        if (n == null || n.contains("/") || n.contains("..") || getContext() == null) throw new FileNotFoundException();
        File f = new File(shareDir(getContext()), n);
        if (!f.isFile()) throw new FileNotFoundException();
        return f;
    }

    @Override public boolean onCreate() { return true; }

    @Override public ParcelFileDescriptor openFile(Uri u, String mode) throws FileNotFoundException {
        return ParcelFileDescriptor.open(fileOf(u), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override public Cursor query(Uri u, String[] p, String s, String[] a, String o) {
        try {
            File f = fileOf(u);
            MatrixCursor c = new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
            c.addRow(new Object[]{f.getName(), f.length()});
            return c;
        } catch (FileNotFoundException e) {
            return null;
        }
    }

    @Override public String getType(Uri u) {
        String n = String.valueOf(u.getLastPathSegment()).toLowerCase();
        if (n.endsWith(".apk")) return "application/vnd.android.package-archive";
        if (n.endsWith(".mp4")) return "video/mp4";
        if (n.endsWith(".jpg")) return "image/jpeg";
        return "application/octet-stream";
    }

    @Override public Uri insert(Uri u, ContentValues v) { return null; }
    @Override public int delete(Uri u, String s, String[] a) { return 0; }
    @Override public int update(Uri u, ContentValues v, String s, String[] a) { return 0; }
}
