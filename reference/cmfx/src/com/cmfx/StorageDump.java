package com.cmfx;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import android.util.Log;

import java.io.File;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Dumps the app's own persisted state so we can locate the 16-byte pairing key (K1)
 * that Nothing X uses to encrypt the watch protocol. Falls back to regex-scraping
 * every stored string, because the exact storage key is Dart-internal.
 */
public final class StorageDump {

    private static final Pattern HEX32 = Pattern.compile("(?<![0-9a-fA-F])[0-9a-fA-F]{32}(?![0-9a-fA-F])");

    public static final class Entry {
        public final String source;
        public final String key;
        public final String value;
        Entry(String source, String key, String value) {
            this.source = source;
            this.key = key;
            this.value = value;
        }
        public String toString() {
            return source + " | " + key + " = " + value;
        }
    }

    private StorageDump() {}

    public static List<Entry> dumpAll(Context ctx) {
        List<Entry> out = new ArrayList<>();

        // 1. Plain shared preferences (Flutter + native).
        try {
            File dir = new File(ctx.getApplicationInfo().dataDir, "shared_prefs");
            File[] files = dir.listFiles();
            if (files != null) {
                for (File f : files) {
                    String name = f.getName();
                    if (name.endsWith(".xml")) name = name.substring(0, name.length() - 4);
                    try {
                        SharedPreferences sp = ctx.getSharedPreferences(name, Context.MODE_PRIVATE);
                        for (Map.Entry<String, ?> e : sp.getAll().entrySet()) {
                            out.add(new Entry("prefs:" + name, e.getKey(), String.valueOf(e.getValue())));
                        }
                    } catch (Throwable t) {
                        Log.w(Bridge.TAG, "prefs " + name + " failed: " + t);
                    }
                }
            }
        } catch (Throwable t) {
            Log.w(Bridge.TAG, "shared_prefs scan failed", t);
        }

        // 2. flutter_secure_storage, default and known custom names.
        String[] names = new String[]{null, "FlutterSecureStorage", "flutter_secure_storage_service", "SecureStorageAndroid"};
        for (String name : names) {
            try {
                Class<?> cls = Class.forName("com.it_nomads.fluttersecurestorage.FlutterSecureStorage");
                Constructor<?> ctor = cls.getConstructor(Context.class, Map.class);
                Map<String, Object> opts = new HashMap<>();
                if (name != null) opts.put("sharedPreferencesName", name);
                Object store = ctor.newInstance(ctx, opts);
                Object all = cls.getMethod("readAll").invoke(store);
                if (all instanceof Map) {
                    for (Map.Entry<?, ?> e : ((Map<?, ?>) all).entrySet()) {
                        out.add(new Entry("secure:" + (name == null ? "default" : name),
                                String.valueOf(e.getKey()), String.valueOf(e.getValue())));
                    }
                }
            } catch (Throwable t) {
                Log.w(Bridge.TAG, "secure storage " + name + " failed: " + t);
            }
        }
        return out;
    }

    /** Returns candidate 16-byte K1 values, most-likely first, deduplicated. */
    public static List<byte[]> keyCandidates(List<Entry> entries) {
        LinkedHashMap<String, byte[]> found = new LinkedHashMap<>();

        // Direct hits: a value that is exactly 32 hex chars or 24 base64 chars.
        for (Entry e : entries) {
            String v = e.value == null ? "" : e.value.trim();
            byte[] k = asKey(v);
            if (k != null) found.put(hexKey(k), k);
        }
        // Nested hits: a 32-hex substring inside JSON / delimited storage values.
        for (Entry e : entries) {
            String v = e.value == null ? "" : e.value;
            Matcher m = HEX32.matcher(v);
            while (m.find()) {
                byte[] k = Util.unhex(m.group());
                if (k.length == 16) found.put(hexKey(k), k);
            }
        }
        // Keys themselves sometimes carry the key material.
        for (Entry e : entries) {
            Matcher m = HEX32.matcher(e.key == null ? "" : e.key);
            while (m.find()) {
                byte[] k = Util.unhex(m.group());
                if (k.length == 16) found.put(hexKey(k), k);
            }
        }
        return new ArrayList<>(found.values());
    }

    private static String hexKey(byte[] k) {
        return Util.hex(k);
    }

    private static byte[] asKey(String s) {
        if (s == null) return null;
        if (s.matches("[0-9a-fA-F]{32}")) return Util.unhex(s);
        if (s.length() == 24 || s.length() == 22) {
            try {
                byte[] b = Base64.decode(s, Base64.NO_WRAP);
                if (b.length == 16) return b;
            } catch (Throwable ignored) {}
        }
        return null;
    }

    /** Relative paths of app-private files, so we can see where Dart keeps its state. */
    public static List<String> listFiles(Context ctx) {
        List<String> out = new ArrayList<>();
        String root = ctx.getApplicationInfo().dataDir;
        String[] dirs = new String[]{"shared_prefs", "files", "databases", "app_flutter", "no_backup"};
        for (String dir : dirs) {
            collect(new File(root, dir), dir, out, 0);
        }
        Collections.sort(out);
        return out;
    }

    private static void collect(File f, String rel, List<String> out, int depth) {
        if (f == null || depth > 3) return;
        File[] kids = f.listFiles();
        if (kids == null) {
            if (f.isFile()) out.add(rel + " (" + f.length() + "B)");
            return;
        }
        if (kids.length == 0 && f.isFile()) {
            out.add(rel + " (" + f.length() + "B)");
            return;
        }
        for (File k : kids) {
            String r = rel + "/" + k.getName();
            if (k.isDirectory()) {
                if (depth < 2) collect(k, r, out, depth + 1);
            } else {
                out.add(r + " (" + k.length() + "B)");
            }
        }
    }

    public static List<Entry> sorted(List<Entry> in) {
        List<Entry> copy = new ArrayList<>(in);
        Collections.sort(copy, (a, b) -> {
            int c = a.source.compareTo(b.source);
            return c != 0 ? c : a.key.compareTo(b.key);
        });
        return copy;
    }
}
