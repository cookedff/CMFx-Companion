package com.cmfx;

import android.util.Log;

/**
 * Static rendezvous between the patched Nothing X smali and the CMFx code.
 * Every method here is called from smali that we inject; keep signatures stable.
 */
public final class Bridge {

    public static final String TAG = "CMFx";

    /** Live com.nothing.nt_ble.plugin.WatchPluginImpl */
    public static volatile Object plugin;
    /** Live com.nothing.nt_ble.NtBlePlugin */
    public static volatile Object ntPlugin;

    /** Receives every raw incoming frame (uuid, index, bytes). */
    public interface RawListener {
        void onRaw(String uuid, int index, byte[] data);
    }

    /** Receives every outbound Flutter write (plugin, device, key, priority, hex payload, timings). */
    public interface OutListener {
        void onOut(Object plugin, Object device, Object key, Object priority, String hex, long a, long b);
    }

    public static volatile RawListener rawListener;
    public static volatile OutListener outListener;

    private Bridge() {}

    // ---- smali hooks -------------------------------------------------------

    public static void setPlugin(Object p) {
        plugin = p;
        Log.i(TAG, "WatchPluginImpl captured: " + (p == null ? "null" : p.getClass().getName()));
    }

    public static void setNtPlugin(Object p) {
        ntPlugin = p;
        Log.i(TAG, "NtBlePlugin captured: " + (p == null ? "null" : p.getClass().getName()));
    }

    /** Hooked at the head of WatchCommandParser.getReceiveCommand(String, int, byte[]). */
    public static void onRaw(String uuid, int index, byte[] data) {
        RawListener l = rawListener;
        if (l != null) {
            try {
                l.onRaw(uuid, index, data);
            } catch (Throwable t) {
                Log.e(TAG, "onRaw failed", t);
            }
        }
    }

    /** Hooked at the head of WatchPluginImpl.writeValue(...). */
    public static void onOut(Object plugin, Object device, Object key, Object priority,
                             String value, long a, long b) {
        OutListener l = outListener;
        if (l != null) {
            try {
                l.onOut(plugin, device, key, priority, value, a, b);
            } catch (Throwable t) {
                Log.e(TAG, "onOut failed", t);
            }
        }
    }
}
