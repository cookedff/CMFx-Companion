package com.cmfx;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * 0xF5 wire codec used by the CMF watch family, matching the official app /
 * FMC "cmf-protocol" reference:
 *
 *   [0xF5][u16be bodyLen][u16be cmd1][u16be chunkCount][u16be chunkIndex][u16be cmd2][body]
 *
 * body = AES-128-CBC-PKCS7(payloadSlice || crc32le(payloadSlice)) for encrypted
 * commands, or plain payloadSlice || crc32le(payloadSlice) for the plaintext set.
 */
public final class FrameCodec {

    public static final byte[] AES_IV = new byte[]{
            0x50, 0x51, 0x52, 0x53, 0x54, 0x55, 0x56, 0x57,
            0x60, 0x61, 0x62, 0x63, 0x64, 0x65, 0x66, 0x5a
    };

    public static final int WF_INIT1_REQ  = (0xffff << 16) | 0x8052;
    public static final int WF_INIT1_REP  = (0xffff << 16) | 0x0052;
    public static final int WF_INIT2_REQ  = (0xffff << 16) | 0x9075;
    public static final int WF_INIT2_REP  = (0xffff << 16) | 0xa075;
    public static final int WF_CHUNK_REQ  = (0xffff << 16) | 0xa064;
    public static final int WF_CHUNK_WRITE= (0xffff << 16) | 0x9064;
    public static final int WF_FINISH_1   = (0xffff << 16) | 0xa065;
    public static final int WF_FINISH_2   = (0xffff << 16) | 0x9065;

    public static final int AUTH_PAIR_REQ = (0xffff << 16) | 0x8047;
    public static final int AUTH_PAIR_REP = (0xffff << 16) | 0x0048;

    public static int key(int c1, int c2) {
        return (c1 << 16) | c2;
    }

    public static boolean isPlaintext(int cmd) {
        return cmd == AUTH_PAIR_REQ || cmd == AUTH_PAIR_REP || cmd == WF_CHUNK_WRITE;
    }

    public static byte[] crc32le(byte[] data, int off, int len) {
        CRC32 crc = new CRC32();
        crc.update(data, off, len);
        long v = crc.getValue();
        return new byte[]{(byte) v, (byte) (v >>> 8), (byte) (v >>> 16), (byte) (v >>> 24)};
    }

    public static byte[] encrypt(byte[] plain, byte[] key16) throws Exception {
        Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding");
        c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key16, "AES"), new IvParameterSpec(AES_IV));
        return c.doFinal(plain);
    }

    public static byte[] decrypt(byte[] cipherText, byte[] key16) throws Exception {
        Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding");
        c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key16, "AES"), new IvParameterSpec(AES_IV));
        return c.doFinal(cipherText);
    }

    /** Like decrypt, but returns null instead of throwing (wrong key / bad padding). */
    public static byte[] decryptQuiet(byte[] cipherText, byte[] key16) {
        try {
            return decrypt(cipherText, key16);
        } catch (Throwable t) {
            return null;
        }
    }

    /** True when raw ends with the little-endian CRC32 of everything before it. */
    public static boolean crcOk(byte[] raw) {
        if (raw == null || raw.length < 4) return false;
        int bodyLen = raw.length - 4;
        CRC32 crc = new CRC32();
        crc.update(raw, 0, bodyLen);
        long v = crc.getValue();
        return (raw[bodyLen] & 0xff) == (v & 0xff)
                && (raw[bodyLen + 1] & 0xff) == ((v >>> 8) & 0xff)
                && (raw[bodyLen + 2] & 0xff) == ((v >>> 16) & 0xff)
                && (raw[bodyLen + 3] & 0xff) == ((v >>> 24) & 0xff);
    }

    public static byte[] stripCrc(byte[] raw) {
        if (raw == null || raw.length < 4) return raw;
        return java.util.Arrays.copyOf(raw, raw.length - 4);
    }

    /** Splits a logical command payload into complete 0xF5 frames for the given MTU. */
    public static List<byte[]> encode(int cmd, byte[] payload, byte[] key16, int mtu) throws Exception {
        return encode(cmd, payload, key16, mtu, false);
    }

    /**
     * @param forcePlain when true every command is sent unencrypted. Some watch generations
     *                   (dialProtocol 1) do not use a session key at all.
     */
    public static List<byte[]> encode(int cmd, byte[] payload, byte[] key16, int mtu,
                                      boolean forcePlain) throws Exception {
        List<byte[]> bodies = new ArrayList<>();
        boolean plain = forcePlain || isPlaintext(cmd);

        if (plain) {
            int size = Math.max(1, mtu - 20);
            if (payload.length == 0) {
                bodies.add(new byte[0]);
            } else {
                for (int i = 0; i < payload.length; i += size) {
                    int n = Math.min(size, payload.length - i);
                    ByteArrayOutputStream b = new ByteArrayOutputStream(n + 4);
                    b.write(payload, i, n);
                    b.write(crc32le(payload, i, n));
                    bodies.add(b.toByteArray());
                }
            }
        } else if (payload.length == 0) {
            bodies.add(new byte[0]);
        } else {
            int maxEnc = ((mtu - 11) / 16) * 16;
            int maxPay = Math.max(16, maxEnc - 5);
            for (int i = 0; i < payload.length; i += maxPay) {
                int n = Math.min(maxPay, payload.length - i);
                ByteArrayOutputStream b = new ByteArrayOutputStream(n + 4);
                b.write(payload, i, n);
                b.write(crc32le(payload, i, n));
                bodies.add(encrypt(b.toByteArray(), key16));
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

    public static void putU16(byte[] b, int off, int v) {
        b[off] = (byte) (v >>> 8);
        b[off + 1] = (byte) v;
    }

    public static int u16(byte[] b, int off) {
        return ((b[off] & 0xff) << 8) | (b[off + 1] & 0xff);
    }

    public static int u32be(byte[] b, int off) {
        return ((b[off] & 0xff) << 24) | ((b[off + 1] & 0xff) << 16)
                | ((b[off + 2] & 0xff) << 8) | (b[off + 3] & 0xff);
    }

    public static void putU32be(byte[] b, int off, long v) {
        b[off] = (byte) (v >>> 24);
        b[off + 1] = (byte) (v >>> 16);
        b[off + 2] = (byte) (v >>> 8);
        b[off + 3] = (byte) v;
    }

    public static void putU32le(byte[] b, int off, long v) {
        b[off] = (byte) v;
        b[off + 1] = (byte) (v >>> 8);
        b[off + 2] = (byte) (v >>> 16);
        b[off + 3] = (byte) (v >>> 24);
    }

    /** A parsed single frame. */
    public static final class Frame {
        public int cmd;
        public int count;
        public int index;
        public byte[] body;
    }

    public static Frame parse(byte[] data, int offset) {
        if (data == null || data.length - offset < 11) return null;
        if ((data[offset] & 0xff) != 0xf5) return null;
        Frame f = new Frame();
        int len = u16(data, offset + 1);
        int c1 = u16(data, offset + 3);
        f.count = u16(data, offset + 5);
        f.index = u16(data, offset + 7);
        int c2 = u16(data, offset + 9);
        f.cmd = key(c1, c2);
        if (offset + 11 + len > data.length) len = data.length - offset - 11;
        f.body = new byte[Math.max(0, len)];
        if (len > 0) System.arraycopy(data, offset + 11, f.body, 0, len);
        return f;
    }

    /** Decrypts/strips CRC. Returns null if the frame cannot be decoded. */
    public static byte[] payload(Frame f, byte[] key16) {
        return payload(f, key16, false);
    }

    public static byte[] payload(Frame f, byte[] key16, boolean forcePlain) {
        try {
            byte[] raw;
            if (forcePlain || isPlaintext(f.cmd)) {
                raw = f.body;
            } else {
                raw = decrypt(f.body, key16);
            }
            if (raw.length < 4) return raw;
            return java.util.Arrays.copyOf(raw, raw.length - 4);
        } catch (Throwable t) {
            return null;
        }
    }

    public static String name(int cmd) {
        switch (cmd) {
            case WF_INIT1_REQ: return "WF_INIT1_REQ";
            case WF_INIT1_REP: return "WF_INIT1_REP";
            case WF_INIT2_REQ: return "WF_INIT2_REQ";
            case WF_INIT2_REP: return "WF_INIT2_REP";
            case WF_CHUNK_REQ: return "WF_CHUNK_REQ";
            case WF_CHUNK_WRITE: return "WF_CHUNK_WRITE";
            case WF_FINISH_1: return "WF_FINISH_ACK_1";
            case WF_FINISH_2: return "WF_FINISH_ACK_2";
            default: return String.format("0x%04x/0x%04x", (cmd >>> 16) & 0xffff, cmd & 0xffff);
        }
    }
}
