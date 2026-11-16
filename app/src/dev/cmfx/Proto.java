package dev.cmfx;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * CMF dial protocol primitives, matching the reference client (FMC ble.ts):
 *
 *   frame  = [0xF5][u16be bodyLen][u16be cmd1][u16be count][u16be index][u16be cmd2][body]
 *   body   = AES-128-CBC-PKCS7(payload || crc32le(payload))     (encrypted commands)
 *   body   = payload || crc32le(payload)                        (plaintext commands)
 *
 * Plaintext commands are exactly: AUTH_PAIR_REQ, AUTH_PAIR_REP, WF_CHUNK_WRITE,
 * AUTH_FAILED. Everything else is encrypted with the current session key.
 */
final class Proto {

    static final byte[] IV = new byte[]{
            0x50, 0x51, 0x52, 0x53, 0x54, 0x55, 0x56, 0x57,
            0x60, 0x61, 0x62, 0x63, 0x64, 0x65, 0x66, 0x5a
    };

    static final int AUTH_PAIR_REQ    = (0xffff << 16) | 0x8047;
    static final int AUTH_PAIR_REP    = (0xffff << 16) | 0x0048;
    static final int AUTH_NAME        = (0xffff << 16) | 0x8049;
    static final int AUTH_MAC         = (0xffff << 16) | 0x0049;
    static final int AUTH_NONCE_REQ   = (0xffff << 16) | 0x804b;
    static final int AUTH_NONCE_REP   = (0xffff << 16) | 0x004c;
    static final int AUTH_CONFIRM_REQ = (0xffff << 16) | 0x804d;
    static final int AUTH_CONFIRM_REP = (0xffff << 16) | 0x0004;
    static final int AUTH_FAILED      = (0xffff << 16) | 0xa051;
    static final int TIME_GET = (0xffff << 16) | 0x8004;
    static final int TIME_RET = (0xffff << 16) | 0x0004;
    static final int BATTERY = (0x005c << 16) | 0x0001;
    static final int BATTERY_GET = (0x005c << 16) | 0x0002;
    static final int WORKOUT_SUMMARY = (0x0057 << 16) | 0x0001;
    static final int WORKOUT_GPS = (0xffff << 16) | 0xa05a;
    static final int ACTIVITY_DATA = (0x0056 << 16) | 0x0001;
    static final int HEART_RATE_MANUAL_AUTO = (0x0053 << 16) | 0x0001;
    static final int HEART_RATE_WORKOUT = (0x00e0 << 16) | 0x0001;
    static final int HEART_RATE_RESTING = (0x00da << 16) | 0x0001;
    static final int SLEEP_DATA = (0x0058 << 16) | 0x0001;
    static final int STRESS = (0x009d << 16) | 0x0001;
    static final int SPO2 = (0x0055 << 16) | 0x0001;

    static final int WF_INIT1_REQ  = (0xffff << 16) | 0x8052;
    static final int WF_INIT1_REP  = (0xffff << 16) | 0x0052;
    static final int WF_INIT2_REQ  = (0xffff << 16) | 0x9075;
    static final int WF_INIT2_REP  = (0xffff << 16) | 0xa075;
    static final int WF_CHUNK_REQ  = (0xffff << 16) | 0xa064;
    static final int WF_CHUNK_WRITE = (0xffff << 16) | 0x9064;
    static final int WF_FINISH_1   = (0xffff << 16) | 0xa065;
    static final int WF_FINISH_2   = (0xffff << 16) | 0x9065;
    /** Installed-dial list pushed by the watch after auth (FFFF/A055). */
    static final int DIAL_LIST     = (0xffff << 16) | 0xa055;
    /** Dial list/select/delete request (FFFF/9055). Type 0 = query. */
    static final int DIAL_COMMAND  = (0xffff << 16) | 0x9055;
    static final int WF_INSTALLED  = (0xffff << 16) | 0xa045;
    static final int ACTIVITY_FETCH_1 = (0xffff << 16) | 0x8005;
    static final int ACTIVITY_FETCH_2 = (0xffff << 16) | 0x9057;
    static final int ACTIVITY_FETCH_ACK_1 = (0xffff << 16) | 0x0005;
    static final int ACTIVITY_FETCH_ACK_2 = (0xffff << 16) | 0xa057;

    static final int SERIAL_GET = (0x00de << 16) | 0x0002;
    static final int SERIAL_RET = (0x00de << 16) | 0x0001;
    static final int FIRMWARE_GET = (0xffff << 16) | 0x8006;
    static final int FIRMWARE_RET = (0xffff << 16) | 0x0006;
    static final int APP_NOTIFICATION = (0x0065 << 16) | 0x0001;
    static final int MUSIC_INFO_SET = (0xffff << 16) | 0x905c;
    static final int MUSIC_BUTTON = (0xffff << 16) | 0xa05d;
    static final int CALL_REMINDER = (0xffff << 16) | 0x9066;
    static final int FIND_WATCH = (0x005d << 16) | 0x0001;
    static final int FIND_PHONE = (0x005b << 16) | 0x0001;
    static final int DO_NOT_DISTURB = (0x0099 << 16) | 0x0001;

    static final int ALARMS_SET = (0x0063 << 16) | 0x0001;
    static final int ALARMS_GET = (0x0063 << 16) | 0x0002;
    static final int CONTACTS_SET = (0x00d5 << 16) | 0x0001;
    static final int CONTACTS_GET = (0x00d5 << 16) | 0x0002;
    static final int WEATHER_SET_1 = (0xffff << 16) | 0x906b;
    static final int WEATHER_SET_2 = (0x0066 << 16) | 0x0001;
    static final int WATCHFACE = (0x009f << 16) | 0x0001;

    static int key(int c1, int c2) {
        return (c1 << 16) | c2;
    }

    static boolean isPlaintext(int cmd) {
        return cmd == AUTH_PAIR_REQ || cmd == AUTH_PAIR_REP
                || cmd == WF_CHUNK_WRITE || cmd == AUTH_FAILED;
    }

    static String name(int cmd) {
        int c1 = (cmd >>> 16) & 0xffff;
        int c2 = cmd & 0xffff;
        if (c1 == 0xffff && c2 == 0x8047) return "AUTH_PAIR_REQ";
        if (c1 == 0xffff && c2 == 0x0048) return "AUTH_PAIR_REP";
        if (c1 == 0xffff && c2 == 0x8049) return "AUTH_NAME";
        if (c1 == 0xffff && c2 == 0x0049) return "AUTH_MAC";
        if (c1 == 0xffff && c2 == 0x804b) return "AUTH_NONCE_REQ";
        if (c1 == 0xffff && c2 == 0x004c) return "AUTH_NONCE_REP";
        if (c1 == 0xffff && c2 == 0x804d) return "AUTH_CONFIRM_REQ";
        if (c1 == 0xffff && c2 == 0x0004) return "AUTH_CONFIRM_REP";
        if (c1 == 0xffff && c2 == 0xa051) return "AUTH_FAILED";
        if (c1 == 0xffff && c2 == 0x8052) return "WF_INIT1_REQ";
        if (c1 == 0xffff && c2 == 0x0052) return "WF_INIT1_REP";
        if (c1 == 0xffff && c2 == 0x9075) return "WF_INIT2_REQ";
        if (c1 == 0xffff && c2 == 0xa075) return "WF_INIT2_REP";
        if (c1 == 0xffff && c2 == 0xa064) return "WF_CHUNK_REQ";
        if (c1 == 0xffff && c2 == 0x9064) return "WF_CHUNK_WRITE";
        if (c1 == 0xffff && c2 == 0xa065) return "WF_FINISH_1";
        if (c1 == 0xffff && c2 == 0x9065) return "WF_FINISH_2";
        if (c1 == 0xffff && c2 == 0xa055) return "DIAL_LIST";
        if (c1 == 0xffff && c2 == 0x9055) return "DIAL_COMMAND";
        if (c1 == 0xffff && c2 == 0xa045) return "WF_INSTALLED";
        if (c1 == 0xffff && c2 == 0x8005) return "ACTIVITY_FETCH_1";
        if (c1 == 0xffff && c2 == 0x9057) return "ACTIVITY_FETCH_2";
        if (c1 == 0xffff && c2 == 0x0005) return "ACTIVITY_FETCH_ACK_1";
        if (c1 == 0xffff && c2 == 0xa057) return "ACTIVITY_FETCH_ACK_2";
        if (c1 == 0x0065 && c2 == 0x0001) return "APP_NOTIFICATION";
        if (c1 == 0xffff && c2 == 0x8004) return "TIME_GET";
        if (c1 == 0xffff && c2 == 0x0004) return "TIME_RET";
        if (c1 == 0x005c && c2 == 0x0001) return "BATTERY";
        if (c1 == 0x005c && c2 == 0x0002) return "BATTERY_GET";
        if (c1 == 0xffff && c2 == 0x8006) return "FIRMWARE_GET";
        if (c1 == 0xffff && c2 == 0x0006) return "FIRMWARE_RET";
        if (c1 == 0x0057 && c2 == 0x0001) return "WORKOUT_SUMMARY";
        if (c1 == 0xffff && c2 == 0xa05a) return "WORKOUT_GPS";
        if (c1 == 0x0056 && c2 == 0x0001) return "ACTIVITY_DATA";
        if (c1 == 0x0053 && c2 == 0x0001) return "HEART_RATE_MANUAL_AUTO";
        if (c1 == 0x00e0 && c2 == 0x0001) return "HEART_RATE_WORKOUT";
        if (c1 == 0x00da && c2 == 0x0001) return "HEART_RATE_RESTING";
        if (c1 == 0x0058 && c2 == 0x0001) return "SLEEP_DATA";
        if (c1 == 0x009d && c2 == 0x0001) return "STRESS";
        if (c1 == 0x0055 && c2 == 0x0001) return "SPO2";
        if (c1 == 0xffff && c2 == 0x905c) return "MUSIC_INFO_SET";
        if (c1 == 0xffff && c2 == 0xa05d) return "MUSIC_BUTTON";
        if (c1 == 0xffff && c2 == 0x9066) return "CALL_REMINDER";
        if (c1 == 0x005d && c2 == 0x0001) return "FIND_WATCH";
        if (c1 == 0x005b && c2 == 0x0001) return "FIND_PHONE";
        if (c1 == 0x0099 && c2 == 0x0001) return "DO_NOT_DISTURB";
        if (c1 == 0x0063 && c2 == 0x0001) return "ALARMS_SET";
        if (c1 == 0x0063 && c2 == 0x0002) return "ALARMS_GET";
        if (c1 == 0x00d5 && c2 == 0x0001) return "CONTACTS_SET";
        if (c1 == 0x00d5 && c2 == 0x0002) return "CONTACTS_GET";
        if (c1 == 0xffff && c2 == 0x906b) return "WEATHER_SET_1";
        if (c1 == 0x0066 && c2 == 0x0001) return "WEATHER_SET_2";
        if (c1 == 0x009f && c2 == 0x0001) return "WATCHFACE";
        if (c1 == 0x009f && c2 == 0x0002) return "WATCHFACE_LIST";
        return String.format("0x%04x/0x%04x", c1, c2);
    }

    // ---- crypto -------------------------------------------------------

    static byte[] sha256(byte[]... parts) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            for (byte[] p : parts) md.update(p);
            return md.digest();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static byte[] random16() {
        byte[] b = new byte[16];
        new SecureRandom().nextBytes(b);
        return b;
    }

    static byte[] encrypt(byte[] plain, byte[] key16) throws Exception {
        Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding");
        c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key16, "AES"), new IvParameterSpec(IV));
        return c.doFinal(plain);
    }

    static byte[] decrypt(byte[] cipherText, byte[] key16) throws Exception {
        Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding");
        c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key16, "AES"), new IvParameterSpec(IV));
        return c.doFinal(cipherText);
    }

    static byte[] crc32le(byte[] data, int off, int len) {
        CRC32 crc = new CRC32();
        crc.update(data, off, len);
        long v = crc.getValue();
        return new byte[]{(byte) v, (byte) (v >>> 8), (byte) (v >>> 16), (byte) (v >>> 24)};
    }

    // ---- framing ------------------------------------------------------

    static List<byte[]> encode(int cmd, byte[] payload, byte[] key16, int mtu) throws Exception {
        List<byte[]> bodies = new ArrayList<>();
        boolean plain = isPlaintext(cmd);

        if (plain) {
            int size = Math.max(1, mtu - 20);
            if (payload.length == 0) {
                bodies.add(new byte[0]);
            } else {
                for (int i = 0; i < payload.length; i += size) {
                    int n = Math.min(size, payload.length - i);
                    bodies.add(concat(slice(payload, i, i + n), crc32le(payload, i, n)));
                }
            }
        } else if (payload.length == 0) {
            bodies.add(new byte[0]);
        } else {
            int maxEnc = ((mtu - 11) / 16) * 16;
            int maxPay = Math.max(16, maxEnc - 5);
            for (int i = 0; i < payload.length; i += maxPay) {
                int n = Math.min(maxPay, payload.length - i);
                bodies.add(encrypt(concat(slice(payload, i, i + n), crc32le(payload, i, n)), key16));
            }
        }

        int c1 = (cmd >>> 16) & 0xffff;
        int c2 = cmd & 0xffff;
        List<byte[]> frames = new ArrayList<>(bodies.size());
        for (int i = 0; i < bodies.size(); i++) {
            byte[] body = bodies.get(i);
            byte[] f = new byte[11 + body.length];
            f[0] = (byte) 0xf5;
            putU16(f, 1, body.length);
            putU16(f, 3, c1);
            putU16(f, 5, bodies.size());
            putU16(f, 7, i + 1);
            putU16(f, 9, c2);
            System.arraycopy(body, 0, f, 11, body.length);
            frames.add(f);
        }
        return frames;
    }

    static final class Frame {
        int cmd;
        int count;
        int index;
        byte[] body;
    }

    static Frame parse(byte[] data) {
        if (data == null || data.length < 11) return null;
        if ((data[0] & 0xff) != 0xf5) return null;
        Frame f = new Frame();
        int len = u16(data, 1);
        f.count = u16(data, 5);
        f.index = u16(data, 7);
        f.cmd = key(u16(data, 3), u16(data, 9));
        if (11 + len > data.length) len = data.length - 11;
        f.body = slice(data, 11, 11 + len);
        return f;
    }

    /**
     * Decrypts and strips the trailing CRC from encrypted frames. Plaintext
     * frames are returned verbatim — the reference client keeps the CRC there.
     */
    static byte[] payload(Frame f, byte[] key16) throws Exception {
        if (isPlaintext(f.cmd)) return f.body;
        byte[] raw = decrypt(f.body, key16);
        if (raw.length < 4) return raw;
        return slice(raw, 0, raw.length - 4);
    }

    // ---- byte helpers -------------------------------------------------

    static byte[] concat(byte[]... parts) {
        int n = 0;
        for (byte[] p : parts) n += p.length;
        byte[] out = new byte[n];
        int o = 0;
        for (byte[] p : parts) {
            System.arraycopy(p, 0, out, o, p.length);
            o += p.length;
        }
        return out;
    }

    static byte[] slice(byte[] b, int from, int to) {
        int a = Math.max(0, from);
        int z = Math.min(b.length, to);
        if (z <= a) return new byte[0];
        byte[] out = new byte[z - a];
        System.arraycopy(b, a, out, 0, z - a);
        return out;
    }

    static void putU16(byte[] b, int off, int v) {
        b[off] = (byte) (v >>> 8);
        b[off + 1] = (byte) v;
    }

    static void putU32le(byte[] b, int off, long v) {
        b[off] = (byte) v;
        b[off + 1] = (byte) (v >>> 8);
        b[off + 2] = (byte) (v >>> 16);
        b[off + 3] = (byte) (v >>> 24);
    }

    static void putU32be(byte[] b, int off, long v) {
        b[off] = (byte) (v >>> 24);
        b[off + 1] = (byte) (v >>> 16);
        b[off + 2] = (byte) (v >>> 8);
        b[off + 3] = (byte) v;
    }

    static int u16(byte[] b, int off) {
        return ((b[off] & 0xff) << 8) | (b[off + 1] & 0xff);
    }

    static long u32le(byte[] b, int off) {
        return ((long) (b[off] & 0xff)) | ((long) (b[off + 1] & 0xff) << 8)
                | ((long) (b[off + 2] & 0xff) << 16) | ((long) (b[off + 3] & 0xff) << 24);
    }

    static long u32be(byte[] b, int off) {
        return ((long) (b[off] & 0xff) << 24) | ((long) (b[off + 1] & 0xff) << 16)
                | ((long) (b[off + 2] & 0xff) << 8) | ((long) (b[off + 3] & 0xff));
    }

    static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte x : b) sb.append(String.format("%02x", x & 0xff));
        return sb.toString();
    }

    static byte[] unhex(String s) {
        int n = s.length() / 2;
        byte[] out = new byte[n];
        for (int i = 0; i < n; i++) {
            out[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }

    /** NUL-terminated ASCII string starting at off, or null. */
    static String cstring(byte[] b, int off) {
        int end = off;
        while (end < b.length && b[end] != 0) end++;
        if (end >= b.length) return null;
        try {
            return new String(b, off, end - off, "UTF-8");
        } catch (Exception e) {
            return null;
        }
    }

    static int dialId(String name) {
        int h = 0x811c9dc5;
        byte[] bytes;
        try {
            bytes = name.getBytes("UTF-8");
        } catch (Exception e) {
            bytes = name.getBytes();
        }
        for (byte c : bytes) {
            h ^= (c & 0xff);
            h *= 0x01000193;
        }
        long unsigned = h & 0xffffffffL;
        return (int) (10000 + (unsigned % 2147473647L));
    }
}
