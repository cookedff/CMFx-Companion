package com.cmfx;

import android.content.Context;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Drives a CMF watch-face (.bin) install over the live Nothing X BLE session.
 *
 * The watch drives the transfer: init1 -> init2 -> repeated chunk requests -> finish.
 * All frames go through NtBlePlugin.writeValue(..., NtSendKey.DIAL, ...), which writes
 * them on the app's existing authenticated dial channel.
 */
public final class DialUploader {

    public interface Log {
        void log(String line);
    }

    public interface Progress {
        void progress(int percent, String message);
    }

    private static final int MTU_DEFAULT = 180;
    private static final long INIT_TIMEOUT_MS = 12000;
    private static final long CHUNK_TIMEOUT_MS = 45000;

    private final Context ctx;
    private final Log log;
    private final Progress progress;

    private final BlockingQueue<FrameCodec.Frame> rx = new ArrayBlockingQueue<>(256);

    private Object nt;
    private Object device;
    private Object dialKey;
    private Object blePriority;
    private byte[] k1;
    private int mtu = MTU_DEFAULT;
    /** Operate interval in seconds (the SDK multiplies by 1000); 0 = no delay. */
    private long intervalSeconds = 0;
    /** Per-write ACK duration in ms; <= 0 lets the SDK use its default of 10 ms. */
    private long writeDurationMillis = 0;

    private volatile boolean capturing;
    private boolean captureMode;
    private boolean plaintextMode;
    private List<byte[]> captureKeys = new ArrayList<>();
    private boolean captureKeyFound;
    private int captureDecoded;

    public DialUploader(Context ctx, Log log, Progress progress) {
        this.ctx = ctx.getApplicationContext();
        this.log = log;
        this.progress = progress;
    }

    /** Logs and captures what the stock app sends, without transmitting anything ourselves. */
    public void setCaptureMode(boolean on) {
        captureMode = on;
        if (on) {
            installHooks();
            captureKeys = candidateKeys(false);
            captureKeyFound = false;
            captureDecoded = 0;
            log.log("Capture keys: " + captureKeys.size());
            Bridge.outListener = (plugin, dev, key, priority, hex, a, b) -> {
                String line = "OUT " + brief(key) + " prio=" + brief(priority)
                        + " iv=" + a + " dur=" + b;
                try {
                    byte[] raw = Util.unhex(hex);
                    FrameCodec.Frame f = FrameCodec.parse(raw, 0);
                    if (f == null) {
                        line += " raw=" + Util.shortHex(raw, 40);
                    } else {
                        line += " " + FrameCodec.name(f.cmd) + " " + f.index + "/" + f.count
                                + " len=" + f.body.length;
                        line += describePayload(f);
                    }
                } catch (Throwable t) {
                    line += " (" + t + ")";
                }
                log.log(line);
            };
            log.log("Capture ON — now install any watch face from the normal Nothing X UI.");
        } else {
            Bridge.outListener = null;
            Bridge.rawListener = null;
            log.log("Capture OFF");
        }
    }

    /**
     * Tries the stored key candidates on a captured frame and, when the trailing CRC32
     * matches, reports the key and the plaintext. This is how we confirm K1 without
     * ever having to see the Dart side.
     */
    private String describePayload(FrameCodec.Frame f) {
        try {
            if (FrameCodec.isPlaintext(f.cmd)) {
                return FrameCodec.crcOk(f.body)
                        ? " plain=" + Util.hex(FrameCodec.stripCrc(f.body))
                        : " (crc bad) plain=" + Util.shortHex(f.body, 40);
            }
            if (captureKeyFound || captureDecoded > 40) return "";
            captureDecoded++;
            for (byte[] k : captureKeys) {
                byte[] raw = FrameCodec.decryptQuiet(f.body, k);
                if (raw != null && FrameCodec.crcOk(raw)) {
                    captureKeyFound = true;
                    return " KEY=" + Util.hex(k) + " plain=" + Util.hex(FrameCodec.stripCrc(raw));
                }
            }
        } catch (Throwable ignored) {
        }
        return "";
    }

    private static String brief(Object o) {
        if (o == null) return "null";
        String s = String.valueOf(o);
        int i = s.lastIndexOf('.');
        return i >= 0 ? s.substring(i + 1) : s;
    }

    private static String abbreviate(String hex) {
        if (hex == null) return "null";
        return hex.length() <= 160 ? hex : hex.substring(0, 160) + "… (" + (hex.length() / 2) + "B)";
    }

    private void installHooks() {
        Bridge.rawListener = (uuid, index, data) -> {
            FrameCodec.Frame f = FrameCodec.parse(data, index);
            if (f == null) {
                log.log("RX raw " + uuid + " idx=" + index + " " + Util.shortHex(data, 64));
                return;
            }
            String name = FrameCodec.name(f.cmd);
            log.log("RX " + name + " chunk " + f.index + "/" + f.count + " " + Util.shortHex(f.body, 48));
            if (!captureMode && isDialReply(f.cmd)) {
                rx.offer(f);
            }
        };
    }

    private static boolean isDialReply(int cmd) {
        return cmd == FrameCodec.WF_INIT1_REP
                || cmd == FrameCodec.WF_INIT2_REP
                || cmd == FrameCodec.WF_CHUNK_REQ
                || cmd == FrameCodec.WF_FINISH_1;
    }

    /** Runs the whole install. Blocking; call from a worker thread. */
    public boolean upload(final byte[] file, final String fileName) {
        installHooks();
        try {
            nt = Bridge.ntPlugin;
            if (nt == null) {
                log.log("NtBlePlugin is not captured yet — is Nothing X fully started?");
                return false;
            }

            log.log("Looking for the connected watch…");
            device = pickWatch(listConnectedDevices());
            if (device == null) {
                log.log("No connected watch found. Connect the watch in Nothing X first.");
                return false;
            }
            log.log("Watch: " + describe(device));

            readMtu();
            log.log("MTU used for framing: " + mtu);
            resolveEnums();

            List<byte[]> keys = keys();
            if (keys.isEmpty()) {
                log.log("No 16-byte K1 candidate in app storage — trying plaintext dial protocol.");
            } else {
                log.log("K1 candidates: " + keys.size() + " (" + keys.get(0).length + " bytes)");
            }

            log.log("Opening dial channel…");
            if (!startDial()) {
                log.log("startDial failed.");
                return false;
            }
            log.log("Dial channel open.");

            boolean ok = false;
            for (byte[] key : keys) {
                k1 = key;
                plaintextMode = false;
                log.log("Trying K1 " + Util.hex(key));
                OkResult r = attempt(file, fileName);
                if (r.ok) {
                    ok = true;
                    log.log(r.message);
                    break;
                }
                log.log("Attempt failed: " + r.message);
                if (!r.retryAnotherKey) break;
            }
            if (!ok) {
                k1 = null;
                plaintextMode = true;
                log.log("Trying plaintext dial frames (dialProtocol 1 style)…");
                OkResult r = attempt(file, fileName);
                if (r.ok) {
                    ok = true;
                    log.log(r.message);
                } else {
                    log.log("Plaintext attempt failed: " + r.message);
                }
            }

            progress.progress(ok ? 100 : 0, ok ? "Installed" : "Failed");
            try {
                method(nt, "stopDial", 2).invoke(nt, device, new Cb("stopDial", new Cb.Sink() {
                    public void ok(Object value) { log.log("Dial channel closed."); }
                    public void fail(Throwable t) { log.log("stopDial failed: " + t); }
                }));
            } catch (Throwable t) {
                android.util.Log.w(Bridge.TAG, "stopDial", t);
            }
            return ok;
        } catch (Throwable t) {
            android.util.Log.e(Bridge.TAG, "upload failed", t);
            log.log("Upload error: " + t);
            return false;
        } finally {
            if (!captureMode) Bridge.rawListener = null;
        }
    }

    private static final class OkResult {
        final boolean ok;
        final boolean retryAnotherKey;
        final String message;
        OkResult(boolean ok, boolean retry, String message) {
            this.ok = ok;
            this.retryAnotherKey = retry;
            this.message = message;
        }
    }

    private OkResult attempt(byte[] file, String name) {
        try {
            rx.clear();
            int id = dialId(name);

            progress.progress(1, "init");
            byte[] init1 = new byte[]{(byte) 0xa5};
            byte[] r1 = roundTrip(FrameCodec.WF_INIT1_REQ, init1, FrameCodec.WF_INIT1_REP, INIT_TIMEOUT_MS);
            if (r1 == null || r1.length == 0 || r1[0] != 1) {
                return new OkResult(false, true,
                        "INIT_1 rejected: " + Util.hex(r1) + " (wrong key or watch refused)");
            }

            boolean init2ok = false;
            String last = "";
            for (int variant = 0; variant < 2 && !init2ok; variant++) {
                if (variant == 1) {
                    rx.clear();
                    byte[] again = roundTrip(FrameCodec.WF_INIT1_REQ, init1,
                            FrameCodec.WF_INIT1_REP, INIT_TIMEOUT_MS);
                    if (again == null || again.length == 0 || again[0] != 1) {
                        last = "re-INIT_1 failed";
                        continue;
                    }
                }
                byte[] init2 = variant == 0 ? init2Be(file.length, id) : init2Le(file.length, id);
                byte[] rep = roundTrip(FrameCodec.WF_INIT2_REQ, init2, FrameCodec.WF_INIT2_REP, INIT_TIMEOUT_MS);
                if (rep != null && rep.length > 0 && rep[0] == 1) {
                    init2ok = true;
                    log.log("INIT_2 accepted (variant " + (variant == 0 ? "9B BE" : "13B LE") + ")");
                } else {
                    last = "INIT_2 rejected: " + Util.hex(rep);
                    log.log(last);
                }
            }
            if (!init2ok) return new OkResult(false, false, last);

            int lastPct = -1;
            long deadline = System.currentTimeMillis() + CHUNK_TIMEOUT_MS;
            while (System.currentTimeMillis() < deadline) {
                FrameCodec.Frame f = rx.poll(500, TimeUnit.MILLISECONDS);
                if (f == null) continue;

                if (f.cmd == FrameCodec.WF_CHUNK_REQ) {
                    byte[] p = FrameCodec.payload(f, k1, plaintextMode);
                    if (p == null || p.length < 9) {
                        return new OkResult(false, false, "undecodable chunk request");
                    }
                    int off = FrameCodec.u32be(p, 0);
                    int len = FrameCodec.u32be(p, 4);
                    int pct = p[8] & 0xff;
                    if (off < 0 || len < 0 || off + len > file.length) {
                        return new OkResult(false, false, "chunk out of bounds: " + off + "+" + len);
                    }
                    if (pct != lastPct) {
                        lastPct = pct;
                        progress.progress(pct, "sending " + pct + "%");
                    }
                    byte[] slice = new byte[len];
                    System.arraycopy(file, off, slice, 0, len);
                    send(FrameCodec.WF_CHUNK_WRITE, slice);
                    deadline = System.currentTimeMillis() + CHUNK_TIMEOUT_MS;
                } else if (f.cmd == FrameCodec.WF_FINISH_1) {
                    byte[] p = FrameCodec.payload(f, k1, plaintextMode);
                    send(FrameCodec.WF_FINISH_2, new byte[]{(byte) 0xa5});
                    if (p != null && p.length > 0 && p[0] == 1) {
                        return new OkResult(true, false, "Watch face installed.");
                    }
                    if (p != null && p.length > 0 && p[0] == 0x0a) {
                        return new OkResult(true, false,
                                "Transfer complete, watch stored but did not activate it (0x0a).");
                    }
                    return new OkResult(false, false, "finish rejected: " + Util.hex(p));
                }
            }
            return new OkResult(false, false, "transfer stalled");
        } catch (Throwable t) {
            android.util.Log.e(Bridge.TAG, "attempt", t);
            return new OkResult(false, false, "error: " + t);
        }
    }

    private byte[] roundTrip(int req, byte[] payload, int expected, long timeoutMs) {
        try {
            send(req, payload);
            long deadline = System.currentTimeMillis() + timeoutMs;
            while (System.currentTimeMillis() < deadline) {
                FrameCodec.Frame f = rx.poll(200, TimeUnit.MILLISECONDS);
                if (f == null) continue;
                if (f.cmd != expected) {
                    log.log("ignoring " + FrameCodec.name(f.cmd));
                    continue;
                }
                return FrameCodec.payload(f, k1, plaintextMode);
            }
        } catch (Throwable t) {
            android.util.Log.w(Bridge.TAG, "roundTrip", t);
        }
        return null;
    }

    private static int dialId(String name) {
        int h = 0x811c9dc5;
        String s = name == null || name.isEmpty() ? "CMFx" : name;
        for (int i = 0; i < s.length(); i++) {
            h ^= s.charAt(i);
            h *= 0x01000193;
        }
        long v = h & 0xffffffffL;
        return (int) (10000 + (v % (0x7fffffffL - 10000)));
    }

    private static byte[] init2Le(long size, int id) {
        byte[] b = new byte[13];
        b[0] = 3;
        FrameCodec.putU32le(b, 1, 0);      // old face id: 0 = append
        FrameCodec.putU32le(b, 5, id);
        FrameCodec.putU32le(b, 9, size);
        return b;
    }

    private static byte[] init2Be(long size, int id) {
        byte[] b = new byte[9];
        b[0] = (byte) 0xa5;
        FrameCodec.putU32be(b, 1, size);
        FrameCodec.putU32be(b, 5, id);
        return b;
    }

    // ---- BLE plumbing ------------------------------------------------------

    private void resolveEnums() throws Exception {
        Class<?> sendKey = Class.forName("com.nothing.generate.NtSendKey");
        dialKey = sendKey.getField("DIAL").get(null);
        Class<?> prio = Class.forName("com.nothing.generate.NtChannelPriority");
        blePriority = prio.getField("BLE").get(null);
    }

    private void send(int cmd, byte[] payload) throws Exception {
        List<byte[]> frames = FrameCodec.encode(cmd, payload, k1, mtu, plaintextMode);
        if (cmd != FrameCodec.WF_CHUNK_WRITE) {
            log.log("TX " + FrameCodec.name(cmd) + " " + frames.size() + " frame(s) "
                    + Util.shortHex(frames.get(0), 96)
                    + (plaintextMode ? " [plain]" : " [aes]"));
        }
        Method wv = method(nt, "writeValue", 6);
        for (byte[] frame : frames) {
            wv.invoke(nt, device, dialKey, blePriority, Util.hex(frame),
                    Long.valueOf(intervalSeconds), Long.valueOf(writeDurationMillis));
        }
    }

    private boolean startDial() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<Throwable> err = new AtomicReference<>();
        method(nt, "startDial", 2).invoke(nt, device, new Cb("startDial", new Cb.Sink() {
            public void ok(Object value) { latch.countDown(); }
            public void fail(Throwable t) { err.set(t); latch.countDown(); }
        }));
        if (!latch.await(15, TimeUnit.SECONDS)) {
            log.log("startDial timed out");
            return false;
        }
        if (err.get() != null) {
            log.log("startDial error: " + err.get());
            return false;
        }
        return true;
    }

    private List<Object> listConnectedDevices() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<Object> got = new AtomicReference<>();
        final AtomicReference<Throwable> err = new AtomicReference<>();
        method(nt, "getBtConnectedDevices", 1).invoke(nt, new Cb("devices", new Cb.Sink() {
            public void ok(Object value) { got.set(value); latch.countDown(); }
            public void fail(Throwable t) { err.set(t); latch.countDown(); }
        }));
        latch.await(8, TimeUnit.SECONDS);
        if (err.get() != null) log.log("device list error: " + err.get());
        List<Object> out = new ArrayList<>();
        Object v = got.get();
        if (v instanceof Iterable) {
            for (Object o : (Iterable<?>) v) out.add(o);
        }
        return out;
    }

    private void readMtu() {
        try {
            final CountDownLatch latch = new CountDownLatch(1);
            final AtomicReference<Object> got = new AtomicReference<>();
            method(nt, "mtuSize", 2).invoke(nt, device, new Cb("mtu", new Cb.Sink() {
                public void ok(Object value) { got.set(value); latch.countDown(); }
                public void fail(Throwable t) { latch.countDown(); }
            }));
            latch.await(3, TimeUnit.SECONDS);
            Object v = got.get();
            if (v instanceof Number) {
                int m = ((Number) v).intValue();
                if (m >= 23) mtu = Math.min(m, 247);
            }
        } catch (Throwable t) {
            android.util.Log.w(Bridge.TAG, "mtuSize", t);
        }
    }

    private List<byte[]> keys() {
        return candidateKeys(true);
    }

    private List<byte[]> candidateKeys(boolean verbose) {
        List<StorageDump.Entry> entries = StorageDump.dumpAll(ctx);
        if (verbose) {
            for (StorageDump.Entry e : entries) {
                log.log("store " + e.source + " " + e.key + " = " + trim(e.value, 90));
            }
        }
        return StorageDump.keyCandidates(entries);
    }

    private static String trim(String s, int n) {
        if (s == null) return "null";
        return s.length() <= n ? s : s.substring(0, n) + "…";
    }

    private Object pickWatch(List<Object> devices) {
        Object best = null;
        int bestScore = -1;
        for (Object d : devices) {
            String name = String.valueOf(Util.call(d, "getName"));
            String real = String.valueOf(Util.call(d, "getRealMac"));
            String type = String.valueOf(Util.call(d, "getDeviceType"));
            log.log("device: name=" + name + " mac=" + real + " type=" + type);
            int score = 0;
            if (type.toUpperCase().contains("WATCH")) score += 5;
            if (name.toLowerCase().contains("watch")) score += 3;
            if (name.toLowerCase().contains("cmf")) score += 2;
            if (score > bestScore) {
                bestScore = score;
                best = d;
            }
        }
        return best;
    }

    private static String describe(Object d) {
        return Util.call(d, "getName") + " / " + Util.call(d, "getRealMac")
                + " / " + Util.call(d, "getDeviceType");
    }

    private static Method method(Object target, String name, int args) throws NoSuchMethodException {
        Class<?> k = target.getClass();
        while (k != null) {
            for (Method m : k.getDeclaredMethods()) {
                if (m.getName().equals(name) && m.getParameterTypes().length == args) {
                    m.setAccessible(true);
                    return m;
                }
            }
            k = k.getSuperclass();
        }
        throw new NoSuchMethodException(name + "/" + args + " on " + target.getClass());
    }
}
