package com.cmfx;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.method.ScrollingMovementMethod;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class MainActivity extends Activity implements DialUploader.Log, DialUploader.Progress {

    private static final int PICK_BIN = 1001;

    private TextView logView;
    private TextView statusView;
    private Button captureButton;
    private DialUploader uploader;
    private volatile boolean busy;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final StringBuilder logBuffer = new StringBuilder();
    private final SimpleDateFormat time = new SimpleDateFormat("HH:mm:ss", Locale.US);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        uploader = new DialUploader(this, this, this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#101010"));
        int pad = dp(14);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("CMFx");
        title.setTextColor(Color.WHITE);
        title.setTextSize(24);
        title.setGravity(Gravity.START);
        root.addView(title);

        statusView = new TextView(this);
        statusView.setText("Idle. Nothing X must be open and the watch connected.");
        statusView.setTextColor(Color.parseColor("#9ad0a0"));
        statusView.setTextSize(13);
        statusView.setPadding(0, dp(4), 0, dp(10));
        root.addView(statusView);

        root.addView(button("Install .bin from storage", v -> pickFile()));
        captureButton = button("Capture stock upload (debug)", v -> toggleCapture());
        root.addView(captureButton);
        root.addView(button("Dump stored keys", v -> dumpKeys()));
        root.addView(button("Clear log", v -> {
            logBuffer.setLength(0);
            logView.setText("");
        }));

        logView = new TextView(this);
        logView.setTextColor(Color.parseColor("#d8d8d8"));
        logView.setTextSize(11);
        logView.setTypeface(android.graphics.Typeface.MONOSPACE);
        logView.setMovementMethod(new ScrollingMovementMethod());
        logView.setTextIsSelectable(true);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(logView, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        lp.topMargin = dp(10);
        root.addView(scroll, lp);

        setContentView(root);
        log("Ready. Build " + BuildConfigish.VERSION);
    }

    private Button button(String text, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(6);
        b.setLayoutParams(lp);
        return b;
    }

    private int dp(int v) {
        return Math.round(getResources().getDisplayMetrics().density * v);
    }

    private void pickFile() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        try {
            startActivityForResult(i, PICK_BIN);
        } catch (Throwable t) {
            log("No file picker: " + t);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_BIN || resultCode != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null) return;
        if (busy) {
            log("Busy — wait for the current transfer.");
            return;
        }
        new Thread(() -> {
            try {
                byte[] file = readAll(uri);
                String name = nameOf(uri);
                log("Picked " + name + " (" + file.length + " bytes)");
                if (file.length < 40) {
                    log("That does not look like a watch-face .bin");
                    return;
                }
                busy = true;
                setStatus("Uploading " + name);
                boolean ok = uploader.upload(file, stripExt(name));
                setStatus(ok ? "Done. Check the watch." : "Failed — see log.");
            } catch (Throwable t) {
                log("Read/upload failed: " + t);
                setStatus("Failed");
            } finally {
                busy = false;
            }
        }, "CMFx-upload").start();
    }

    private byte[] readAll(Uri uri) throws Exception {
        InputStream in = getContentResolver().openInputStream(uri);
        if (in == null) throw new IllegalStateException("cannot open " + uri);
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toByteArray();
        } finally {
            try { in.close(); } catch (Throwable ignored) {}
        }
    }

    private static String nameOf(Uri uri) {
        String p = uri.getLastPathSegment();
        return p == null ? "face.bin" : p;
    }

    private static String stripExt(String n) {
        int i = n.lastIndexOf('.');
        return i > 0 ? n.substring(0, i) : n;
    }

    private void toggleCapture() {
        captureButton.setTag(captureButton.getTag() == null ? Boolean.TRUE : null);
        boolean enable = captureButton.getTag() != null;
        uploader.setCaptureMode(enable);
        captureButton.setText(enable ? "Capture ON (tap to stop)" : "Capture stock upload (debug)");
        setStatus(enable ? "Capture ON — install a face from the stock UI, then check the log."
                : "Capture OFF");
    }

    private void dumpKeys() {
        new Thread(() -> {
            try {
                log("--- data files ---");
                for (String f : StorageDump.listFiles(this)) log(f);
                java.util.List<StorageDump.Entry> all = StorageDump.sorted(StorageDump.dumpAll(this));
                log("--- storage entries: " + all.size() + " ---");
                for (StorageDump.Entry e : all) {
                    log(e.source + " | " + e.key + " = " + e.value);
                }
                java.util.List<byte[]> keys = StorageDump.keyCandidates(all);
                log("--- K1 candidates: " + keys.size() + " ---");
                for (byte[] k : keys) log("K1 " + Util.hex(k));
            } catch (Throwable t) {
                log("dump failed: " + t);
            }
        }, "CMFx-dump").start();
    }

    // ---- callbacks ---------------------------------------------------------

    @Override
    public void log(String line) {
        android.util.Log.i(Bridge.TAG, line);
        ui.post(() -> {
            logBuffer.append(time.format(new Date())).append("  ").append(line).append('\n');
            if (logBuffer.length() > 200_000) {
                logBuffer.delete(0, logBuffer.length() - 150_000);
            }
            logView.setText(logBuffer);
        });
    }

    @Override
    public void progress(int percent, String message) {
        setStatus(percent + "% — " + message);
    }

    private void setStatus(String s) {
        ui.post(() -> statusView.setText(s));
    }

    /** Tiny placeholder so the activity does not depend on generated BuildConfig. */
    static final class BuildConfigish {
        static final String VERSION = "CMFx-m1";
    }
}
