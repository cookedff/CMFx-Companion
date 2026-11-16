package com.cmfx;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class Util {

    private Util() {}

    public static String hex(byte[] b) {
        if (b == null) return "null";
        char[] out = new char[b.length * 2];
        final char[] H = "0123456789abcdef".toCharArray();
        for (int i = 0; i < b.length; i++) {
            int v = b[i] & 0xff;
            out[i * 2] = H[v >>> 4];
            out[i * 2 + 1] = H[v & 0xf];
        }
        return new String(out);
    }

    public static String hex(byte[] b, int off, int len) {
        if (b == null) return "null";
        if (off < 0) off = 0;
        if (off > b.length) off = b.length;
        if (len < 0) len = 0;
        if (off + len > b.length) len = b.length - off;
        byte[] s = new byte[len];
        System.arraycopy(b, off, s, 0, len);
        return hex(s);
    }

    public static byte[] unhex(String s) {
        if (s == null) return new byte[0];
        int n = s.length() / 2;
        byte[] out = new byte[n];
        for (int i = 0; i < n; i++) {
            out[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }

    public static String shortHex(byte[] b, int max) {
        String h = hex(b);
        return h.length() <= max ? h : h.substring(0, max) + "…(" + b.length + "B)";
    }

    /** Calls no-arg method, returns null on any failure. */
    public static Object call(Object target, String method) {
        if (target == null) return null;
        try {
            Method m = findMethod(target.getClass(), method);
            if (m == null) return null;
            m.setAccessible(true);
            return m.invoke(target);
        } catch (Throwable t) {
            return null;
        }
    }

    public static Method findMethod(Class<?> c, String name, Class<?>... params) {
        Class<?> k = c;
        while (k != null) {
            try {
                Method m = k.getDeclaredMethod(name, params);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException ignored) {
                k = k.getSuperclass();
            }
        }
        // fall back to a name-only match (obfuscated/renamed signatures)
        k = c;
        while (k != null) {
            for (Method m : k.getDeclaredMethods()) {
                if (m.getName().equals(name)) {
                    m.setAccessible(true);
                    return m;
                }
            }
            k = k.getSuperclass();
        }
        return null;
    }

    public static Field findField(Class<?> c, String name) {
        Class<?> k = c;
        while (k != null) {
            try {
                Field f = k.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) {
                k = k.getSuperclass();
            }
        }
        return null;
    }

    /** Unwraps kotlin.Result into its value, or returns the Throwable wrapped in Result.Failure. */
    public static Object unboxResult(Object result) {
        if (result == null) return null;
        try {
            Class<?> c = result.getClass();
            if (!"kotlin.Result".equals(c.getName())) {
                // some callbacks hand back the plain value (Boolean, Long, List, ...)
                return result;
            }
            Method m = c.getDeclaredMethod("unbox-impl");
            m.setAccessible(true);
            Object v = m.invoke(result);
            String cn = v == null ? "" : v.getClass().getName();
            if (cn.endsWith("Result$Failure")) {
                Field f = v.getClass().getDeclaredField("exception");
                f.setAccessible(true);
                return f.get(v);
            }
            return v;
        } catch (Throwable t) {
            return t;
        }
    }

    public static String str(Object o) {
        if (o == null) return "null";
        if (o instanceof byte[]) return hex((byte[]) o);
        return String.valueOf(o);
    }
}
