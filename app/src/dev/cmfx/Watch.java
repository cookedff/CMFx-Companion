package dev.cmfx;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Pairing, authentication and watch-face upload, following the reference client:
 *
 *   pair   : shell "AT GETSECRET" -> 16-byte secret
 *            r = random16
 *            send AUTH_PAIR_REQ  = r || SHA256(r || secret)
 *            recv AUTH_PAIR_REP  = o || SHA256(o || secret)
 *            key = SHA256(r || o || secret)[0..16]
 *   auth   : send AUTH_NAME (0xA5 || client name)
 *            recv AUTH_MAC
 *            send AUTH_NONCE_REQ, recv AUTH_NONCE_REP = t
 *            key = SHA256(t || key)[0..16]
 *            send AUTH_CONFIRM_REQ, recv AUTH_CONFIRM_REP
 *   upload : INIT_1 -> INIT_2 (13-byte LE) -> reply to every WF_CHUNK_REQ -> FINISH
 */
final class Watch {

    interface Log { void log(String line); }
    interface Progress { void progress(int pct); }

    private final Ble ble;
    private final Log log;
    private volatile Progress progress;

    /** Face slots reported by the watch (FFFF/A055); empty until the first notification. */
    private volatile int[] dialIds = new int[0];
    private volatile int dialCapacity = 6;
    private volatile int activeDial = -1;

    /** Raised when every face slot is taken and the caller must name one to overwrite. */
    static final class NeedSlotException extends IOException {
        final int[] installed;
        final int capacity;

        NeedSlotException(String message, int[] installed, int capacity) {
            super(message);
            this.installed = installed == null ? new int[0] : installed;
            this.capacity = capacity;
        }
    }

    /** The old dial id is not installed any more; the upload can be retried as an append. */
    private static final class StaleSlotException extends IOException {
        StaleSlotException(String message) { super(message); }
    }

    /**
     * The face being installed is already on the watch. The watch answers such an
     * upload with {@code finish=0x0a} and drops the most recently added dial, so it is
     * refused here instead of being sent.
     */
    static final class AlreadyInstalledException extends IOException {
        final int dialId;

        AlreadyInstalledException(int dialId) {
            super("this face is already on the watch (id " + dialId
                    + ") — remove it on the watch first to reinstall it");
            this.dialId = dialId;
        }
    }

    private byte[] key;
    private volatile boolean authUsedStoredKey;
    private volatile boolean authFreshPairing;
    private final Health health = Health.shared();
    private volatile String lastWorkout = "";
    private volatile boolean lastFetchOk = false;

    Watch(Ble ble, Log log, Progress progress) {
        this.ble = ble;
        this.log = log;
        this.progress = progress;
    }

    /** Lets the activity drive the progress bar for a session-owned watch. */
    void setProgressListener(Progress listener) { this.progress = listener; }

    private void report(int pct) {
        Progress p = progress;
        if (p != null) p.progress(pct);
    }

    /** Installed dial ids as reported by the watch, or an empty list when unknown. */
    int[] installedDialIds() { return dialIds.clone(); }

    int dialCapacity() { return dialCapacity; }

    /** Parses the FFFF/A055 installed-dial notification. */
    void noteDialList(byte[] p) {
        if (p == null || p.length < 4) return;
        int total = p.length >= 3 ? (p[2] & 0xff) : 0;
        int capacity = p.length >= 4 ? (p[3] & 0xff) : 0;
        if (capacity > 0 && capacity < 64) dialCapacity = capacity;
        activeDial = p.length >= 2 ? (p[1] & 0xff) : -1;
        java.util.ArrayList<Integer> ids = new java.util.ArrayList<>();
        for (int off = 4; off + 4 <= p.length; off += 4) {
            int id = (int) Proto.u32le(p, off);
            if (id != 0xffffffff && id != 0) ids.add(id);
        }
        int[] next = new int[ids.size()];
        for (int i = 0; i < next.length; i++) next[i] = ids.get(i);
        dialIds = next;
        log.log("Dials on watch: " + ids.size() + "/"
                + (capacity > 0 ? capacity : dialCapacity)
                + (activeDial >= 0 ? "  active index " + activeDial : "")
                + "  ids " + ids);
    }

    // ---- auth-key persistence (Gadgetbridge-compatible) ----------------

    private static android.content.Context appContext;

    /** Called once by the service so the pairing key can survive restarts. */
    static void init(android.content.Context c) {
        if (c != null) appContext = c.getApplicationContext();
    }

    /** The stored pairing key K1, or null when this watch has not been paired yet. */
    static String storedAuthKeyHex() {
        if (appContext == null) return null;
        String s = appContext.getSharedPreferences("cmfx", 0).getString("authkey", null);
        return (s != null && s.length() == 32) ? s : null;
    }

    private static void storeAuthKeyHex(byte[] k) {
        if (appContext == null || k == null || k.length != 16) return;
        appContext.getSharedPreferences("cmfx", 0).edit()
                .putString("authkey", Proto.hex(k)).apply();
    }

    /** Latest aggregated health snapshot. */
    Health health() { return health; }

    /** Latest watch battery level, or -1 when the watch has not reported one yet. */
    int lastBattery() { return health.battery; }

    /** True while the watch reports it is charging. */
    boolean lastCharging() { return health.charging; }

    /** Raw last workout-summary payload. */
    String lastWorkout() { return lastWorkout; }

    /** True when the last activity fetch sequence completed. */
    boolean lastFetchOk() { return lastFetchOk; }

    /** True when the current session authenticated with the stored key (no re-pair). */
    boolean authKeyVerified() { return authUsedStoredKey; }

    /** True when this session had to derive a new key, which rotates the watch's K1. */
    boolean authKeyFreshPairing() { return authFreshPairing; }

    // ---- session ------------------------------------------------------

    void handshake() throws Exception {
        authUsedStoredKey = false;
        authFreshPairing = false;
        String saved = storedAuthKeyHex();
        if (saved != null) {
            // Same fast path Gadgetbridge uses: the watch already holds K1, so we
            // can skip AT GETSECRET and the pair exchange. This keeps K1 stable,
            // which is what makes it usable as Gadgetbridge's "auth key".
            try {
                key = Proto.unhex(saved);
                log.log("Using stored auth key " + saved + " (Gadgetbridge-compatible).");
                authenticate("fmc");
                authUsedStoredKey = true;
            } catch (Exception e) {
                log.log("Stored auth key rejected (" + msg(e) + "); re-pairing…");
                key = null;
                pair();
                authenticate("fmc");
                authFreshPairing = true;
            }
        } else {
            pair();
            try {
                authenticate("fmc");
            } catch (Exception first) {
                log.log("Auth as 'fmc' failed (" + msg(first) + "), re-pairing as 'cmfx'…");
                pair();
                authenticate("cmfx");
            }
            authFreshPairing = true;
        }
        log.log("Authenticated — session key " + Proto.hex(key).substring(0, 8) + "…");
        syncTime();
    }

    /**
     * Post-auth initialization, mirroring the reference client's phase 2: send the
     * current time and timezone. Without this the watch stays quiet about state
     * such as battery level.
     */
    private void syncTime() throws Exception {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        ByteBuffer buf = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN);
        buf.putInt((int) (cal.getTimeInMillis() / 1000L));
        buf.putInt(java.util.TimeZone.getDefault().getOffset(cal.getTimeInMillis()));
        clearFramesKeepingBattery();
        // The reference client writes each command to both the command and data
        // characteristics; mirror that during initialization.
        ble.writeFrames(false, Proto.TIME_GET, buf.array(), key);
        ble.writeFrames(true, Proto.TIME_GET, buf.array(), key);
        try {
            byte[] r = await(Proto.TIME_RET, 1500);
            log.log("Time synced (" + Proto.hex(r) + ")");
        } catch (Exception e) {
            log.log("Time sync: no reply (continuing)");
        }
        initQuery(Proto.FIRMWARE_GET, Proto.FIRMWARE_RET, "firmware");
        initQuery(Proto.SERIAL_GET, Proto.SERIAL_RET, "serial");
    }

    /** Best-effort post-auth query used to complete session initialization. */
    private void initQuery(int request, int reply, String label) {
        try {
            ble.writeFrames(false, request, new byte[0], key);
            ble.writeFrames(true, request, new byte[0], key);
            byte[] r = await(reply, 2500);
            log.log("Watch " + label + ": " + Proto.hex(r));
        } catch (Exception e) {
            log.log("Watch " + label + ": no reply (" + msg(e) + ")");
        }
    }

    private void pair() throws Exception {
        log.log("Pairing: asking watch for its secret (AT GETSECRET)… confirm on the watch if prompted.");
        ble.clearFrames();
        String reply = ble.shellCommand("AT GETSECRET", 5000);
        if (!reply.endsWith(",OK") || reply.length() < 42) {
            throw new IOException("watch refused AT GETSECRET: \"" + reply + "\"");
        }
        String secretHex = reply.substring(10, 42);
        byte[] secret = Proto.unhex(secretHex);
        if (secret.length != 16) throw new IOException("bad secret length: " + secret.length);
        log.log("AUTH_SECRET_HEX=" + secretHex);

        byte[] r = Proto.random16();
        ble.writeFrames(false, Proto.AUTH_PAIR_REQ,
                Proto.concat(r, Proto.sha256(r, secret)), null);

        byte[] rep = await(Proto.AUTH_PAIR_REP, 8000);
        if (rep.length < 48) throw new IOException("short AUTH_PAIR_REP: " + Proto.hex(rep));
        byte[] o = Proto.slice(rep, 0, 16);
        byte[] proof = Proto.sha256(o, secret);
        if (!java.util.Arrays.equals(Proto.slice(rep, 16, 48), proof)) {
            throw new IOException("watch signature mismatch — wrong secret or unexpected reply");
        }
        key = Proto.slice(Proto.sha256(r, o, secret), 0, 16);
        storeAuthKeyHex(key);
        log.log("AUTH_KEY_HEX=" + Proto.hex(key));
        log.log("Watch proved the secret; pairing key K1 stored (this is Gadgetbridge's auth key).");
    }

    private void authenticate(String clientName) throws Exception {
        log.log("Authenticating as \"" + clientName + "\"…");
        ble.clearFrames();

        ble.writeFrames(false, Proto.AUTH_NAME,
                Proto.concat(new byte[]{(byte) 0xA5}, clientName.getBytes("UTF-8")), key);
        await(Proto.AUTH_MAC, 6000);

        ble.writeFrames(false, Proto.AUTH_NONCE_REQ, new byte[]{(byte) 0xA5}, key);
        byte[] nonce = await(Proto.AUTH_NONCE_REP, 6000);
        key = Proto.slice(Proto.sha256(nonce, key), 0, 16);

        ble.writeFrames(false, Proto.AUTH_CONFIRM_REQ, new byte[]{(byte) 0xA5}, key);
        await(Proto.AUTH_CONFIRM_REP, 6000);
    }

    // ---- upload -------------------------------------------------------

    void upload(byte[] file) throws Exception {
        upload(file, -1, null);
    }

    /** Back-compat for the manual replace-ID field: 0 appends, >0 targets that dial. */
    void upload(byte[] file, int oldId) throws Exception {
        upload(file, oldId, null);
    }

    /**
     * Uploads a face.
     *
     * <p>{@code oldId} selects the slot the watch should overwrite: {@code -1} decides
     * automatically from the installed-dial list (reinstall into its own slot, append while
     * there is room, otherwise ask the caller for a slot), {@code 0} forces an append and a
     * positive value targets that dial.
     *
     * <p>{@code seed} derives the face id. The FMC catalog record id is used where one exists,
     * because two different faces can share the same name embedded in the .bin.
     */
    void upload(byte[] file, int oldId, String seed) throws Exception {
        if (key == null) throw new IOException("not authenticated");
        String dialName = validate(file);
        int newId = Proto.dialId(seed == null || seed.isEmpty() ? dialName : seed);
        if (oldId > 0 && oldId == newId) throw new AlreadyInstalledException(newId);

        if (dialIds.length == 0) {
            try {
                log.log("Dial list not reported yet — querying before upload…");
                if (!requestDialList(2500)) {
                    log.log("Dial list query timed out; trying an append first.");
                }
            } catch (Exception e) {
                log.log("Dial list query failed: " + msg(e));
            }
        }

        int chosen = oldId;
        if (chosen < 0) {
            int[] installed = dialIds;
            if (contains(installed, newId)) {
                // The watch refuses an in-place re-flash of the same id with finish=0x0a and
                // rolls back its dial list, so never send this upload at all.
                throw new AlreadyInstalledException(newId);
            } else if (installed.length >= dialCapacity) {
                throw new NeedSlotException("the watch already holds "
                        + installed.length + " of " + dialCapacity + " faces",
                        installed, dialCapacity);
            } else {
                chosen = 0;
            }
        }

        log.log("Face \"" + dialName + "\"  id=" + newId + "  " + file.length + " bytes"
                + (chosen != 0 ? "  replacing id=" + chosen : "  (append)"));
        log.log("Upload context: authFresh=" + authFreshPairing + " authStored=" + authUsedStoredKey
                + " installed=" + java.util.Arrays.toString(dialIds) + "/" + dialCapacity
                + " active=" + activeDial);
        try {
            attempt(file, newId, chosen);
        } catch (StaleSlotException e) {
            if (chosen != 0 && !contains(dialIds, chosen)) {
                // The dial disappeared since it was reported: fall back to an append.
                log.log("Dial " + chosen + " is no longer installed — retrying as an append.");
                attempt(file, newId, 0);
            } else {
                // The watch refused the dial it reported itself, so let the caller pick another.
                throw new NeedSlotException(e.getMessage(), dialIds, dialCapacity);
            }
        }
    }

    private static boolean contains(int[] ids, int id) {
        for (int candidate : ids) if (candidate == id) return true;
        return false;
    }

    /** One INIT_1 → INIT_2 → chunk loop → FINISH exchange. */
    private void attempt(byte[] file, int newId, int oldId) throws Exception {
        ble.clearFrames();
        report(0);
        log.log("Sending WF_INIT1_REQ (payload A5)…");
        ble.writeFrames(true, Proto.WF_INIT1_REQ, new byte[]{(byte) 0xA5}, key);
        byte[] r1 = await(Proto.WF_INIT1_REP, 8000);
        log.log("WF_INIT1_REP: " + Proto.hex(r1) + (r1.length > 0 ? "  (status 0x" + Integer.toHexString(r1[0] & 0xff) + ")" : "  (empty)"));
        if (r1.length == 0 || r1[0] != 1) {
            throw new IOException("watch rejected INIT_1: " + Proto.hex(r1));
        }

        // §9.5e: 13 bytes, little-endian, no marker:
        // ctr_type(3) ‖ old_wf_id ‖ new_wf_id ‖ size. old_wf_id 0 appends, otherwise the
        // watch overwrites that dial and must already have it installed.
        byte[] init2 = new byte[13];
        init2[0] = 3;
        Proto.putU32le(init2, 1, oldId & 0xffffffffL);
        Proto.putU32le(init2, 5, newId);
        Proto.putU32le(init2, 9, file.length);
        ble.writeFrames(true, Proto.WF_INIT2_REQ, init2, key);
        byte[] r2 = await(Proto.WF_INIT2_REP, 8000);
        log.log("WF_INIT2_REP: " + Proto.hex(r2) + (r2.length > 0 ? "  (status 0x" + Integer.toHexString(r2[0] & 0xff) + ")" : "  (empty)"));
        if (r2.length == 0 || r2[0] != 1) {
            if (oldId != 0) {
                throw new StaleSlotException("the watch would not open dial " + oldId
                        + " for replacement (INIT_2 " + Proto.hex(r2) + ")");
            }
            throw new NeedSlotException("the watch refused an append (INIT_2 "
                    + Proto.hex(r2) + ") — every face slot may be taken",
                    dialIds, dialCapacity);
        }

        long started = System.currentTimeMillis();
        long sent = 0;
        int lastPct = -1;
        while (true) {
            Proto.Frame f = ble.pollFrame(30000);
            if (f == null) {
                throw new IOException("upload stalled — no chunk request for 30 s (sent "
                        + sent + "/" + file.length + " bytes)");
            }
            if (f.cmd == Proto.AUTH_FAILED) throw new IOException("watch dropped the session");

            if (f.cmd == Proto.WF_CHUNK_REQ) {
                byte[] p = Proto.payload(f, key);
                if (p.length < 9) throw new IOException("short chunk request: " + Proto.hex(p));
                long off = Proto.u32be(p, 0);
                long len = Proto.u32be(p, 4);
                int pct = p[8] & 0xff;
                if (off + len > file.length) {
                    throw new IOException("chunk out of range: " + off + "+" + len);
                }
                if (pct != lastPct) {
                    lastPct = pct;
                    report(pct);
                    if (pct % 10 == 0 || pct >= 99) log.log("  " + pct + "%");
                }
                byte[] part = Proto.slice(file, (int) off, (int) (off + len));
                ble.writeFrames(true, Proto.WF_CHUNK_WRITE, part, key);
                sent += part.length;
                continue;
            }

            if (f.cmd == Proto.WF_FINISH_1) {
                byte[] p = Proto.payload(f, key);
                int code = p.length > 0 ? (p[0] & 0xff) : -1;
                try { ble.writeFrames(true, Proto.WF_FINISH_2, new byte[]{(byte) 0xA5}, key); }
                catch (Exception ignored) {}
                long secs = Math.max(1, (System.currentTimeMillis() - started) / 1000);
                if (code == 1) {
                    report(100);
                    // The watch does not re-announce the list after an upload; keep it current.
                    if (oldId != 0) {
                        int[] installed = dialIds;
                        for (int i = 0; i < installed.length; i++) {
                            if (installed[i] == oldId) { installed[i] = newId; break; }
                        }
                    } else if (!contains(dialIds, newId)) {
                        int[] grown = java.util.Arrays.copyOf(dialIds, dialIds.length + 1);
                        grown[grown.length - 1] = newId;
                        dialIds = grown;
                    }
                    log.log("Dials now " + dialIds.length + " of " + dialCapacity + ".");
                    log.log("Done in " + secs + " s (" + (sent / 1024) + " KB)."
                            + " The face is installed — long-press the watch face to pick it.");
                    return;
                }
                if (code == 0x0a) {
                    // 0x0a = the file arrived but the watch did not accept it as a dial.
                    if (oldId != 0) {
                        throw new StaleSlotException("the watch will not overwrite dial " + oldId
                                + " in place (finish=0x0a)");
                    }
                    throw new NeedSlotException(
                            "the watch stored the file but refused to activate it"
                                    + " (finish=0x0a) — all " + dialCapacity + " face slots are taken",
                            dialIds, dialCapacity);
                }
                throw new IOException("watch refused the file (finish=0x" + Integer.toHexString(code)
                        + ", " + (file.length / 1024) + " KB)");
            }

            log.log("  (ignoring " + Proto.name(f.cmd) + " during upload)");
        }
    }

    /** Forward one Android notification using the CMF companion payload format. */
    void sendNotification(int icon, String sender, String body) throws Exception {
        if (key == null) throw new IOException("not authenticated");
        byte[] title = truncateUtf8(sender == null ? "" : sender, 20);
        byte[] text = truncateUtf8(body == null ? "" : body, 128);
        byte[] p = new byte[1 + 1 + 4 + 1 + title.length + text.length];
        p[0] = (byte) icon;
        p[1] = 0;
        long seconds = System.currentTimeMillis() / 1000L;
        p[2] = (byte) (seconds >>> 24); p[3] = (byte) (seconds >>> 16);
        p[4] = (byte) (seconds >>> 8); p[5] = (byte) seconds;
        p[6] = (byte) title.length;
        System.arraycopy(title, 0, p, 7, title.length);
        System.arraycopy(text, 0, p, 7 + title.length, text.length);
        ble.writeFrames(false, Proto.APP_NOTIFICATION, p, key);
    }

    private static byte[] truncateUtf8(String value, int max) throws Exception {
        byte[] all = value.getBytes("UTF-8");
        if (all.length <= max) return all;
        int n = max;
        while (n > 0 && (all[n] & 0xc0) == 0x80) n--;
        return Proto.slice(all, 0, n);
    }

    /**
     * Read the watch battery level.
     *
     * BATTERY (0x005c/0x0001) is a notification, not a request/response command: the
     * watch reports it on its own (and occasionally after TRIGGER_SYNC). A missing
     * reply is normal and must not abort a health sync.
     */
    void getBattery() throws Exception {
        if (key == null) throw new IOException("not authenticated");
        // One battery report per session is enough; the watch also pushes updates
        // whenever its level or charging state changes.
        if (health.battery >= 0) {
            log.log("Battery (already reported): " + health.battery
                    + (health.charging ? "% charging" : "%"));
            return;
        }
        ble.writeFrames(false, Proto.BATTERY_GET, new byte[]{(byte) 0xA5}, key);
        long deadline = System.currentTimeMillis() + 5000;
        while (true) {
            long left = deadline - System.currentTimeMillis();
            if (left <= 0) break;
            Proto.Frame f = ble.pollFrame(left);
            if (f == null) break;
            if (f.cmd == Proto.BATTERY) {
                health.setBattery(safePayload(f));
                log.log("Battery response: " + health.battery + "%"
                        + (health.charging ? " charging" : ""));
                return;
            }
            log.log("  (battery wait: skipping " + Proto.name(f.cmd) + ")");
        }
        log.log("Battery not reported yet (passive notification only)");
    }

    /**
     * Run the recorded-data sync and collect the notifications the watch pushes in
     * response: activity samples, heart rate, SpO2, stress, sleep and workouts.
     *
     * Sequence: ACTIVITY_FETCH_1 -> ACK_1(0x01) -> ACTIVITY_FETCH_2 -> ACK_2
     *           -> ACTIVITY_DATA/... -> ACK_1(0x02 = finished).
     */
    void fetchActivityData() throws Exception {
        if (key == null) throw new IOException("not authenticated");
        health.beginSync();
        lastFetchOk = false;
        clearFramesKeepingBattery();
        ble.writeFrames(false, Proto.ACTIVITY_FETCH_1, new byte[]{(byte) 0xA5}, key);
        byte[] ack1 = await(Proto.ACTIVITY_FETCH_ACK_1, 15000);
        if (ack1.length == 0) {
            log.log("Activity fetch: empty ack1");
            return;
        }
        int code = ack1[0] & 0xff;
        if (code == 0x02) {
            log.log("Activity fetch: watch reports nothing new");
            lastFetchOk = true;
            return;
        }
        if (code != 0x01) {
            log.log("Activity fetch rejected (ack1=" + Proto.hex(ack1) + ")");
            return;
        }

        ble.writeFrames(false, Proto.ACTIVITY_FETCH_2, new byte[]{(byte) 0xA5}, key);
        byte[] ack2 = await(Proto.ACTIVITY_FETCH_ACK_2, 30000);
        log.log("Activity fetch ack2: " + Proto.hex(ack2));

        // Collect the data burst until the watch sends the finish ack (ACK_1 = 0x02).
        // Heavy sessions push ~170-210 frames over ~160 s, so the hard deadline must
        // comfortably exceed the observed burst window.
        long idleDeadline = System.currentTimeMillis() + 10_000;
        long hardDeadline = System.currentTimeMillis() + 180_000;
        boolean finished = false;
        while (System.currentTimeMillis() < hardDeadline) {
            long left = Math.min(idleDeadline, hardDeadline) - System.currentTimeMillis();
            if (left <= 0) break;
            Proto.Frame f = ble.pollFrame(left);
            if (f == null) break;
            idleDeadline = System.currentTimeMillis() + 10_000;
            if (consume(f)) {
                finished = true;
                break;
            }
        }
        boolean gotData = health.activitySamples > 0 || health.hrSamples > 0
                || health.spo2Samples > 0 || health.stressSamples > 0
                || health.sleepSamples > 0 || health.workouts > 0;
        lastFetchOk = finished || gotData;
        // Replace the dashboard totals with this sync's own records instead of adding to
        // them: the watch resends the day, so adding would inflate the numbers on every pull.
        if (health.activitySamples > 0) health.commitSync();
        log.log("Activity sync collected: " + health.activitySamples + " activity, "
                + health.hrSamples + " HR, " + health.spo2Samples + " SpO2, "
                + health.stressSamples + " stress, " + health.sleepSamples + " sleep"
                + (finished ? " (finish ack)" : " (idle timeout)"));
        log.log("Totals from watch: " + health.steps + " steps, " + health.distanceM
                + " m, " + health.calories + " calorie-units");
    }

    /** Handles one incoming frame during a sync; returns true on the finish ack. */
    private boolean consume(Proto.Frame f) throws Exception {
        if (f.cmd == Proto.BATTERY) {
            byte[] bp = safePayload(f);
            if (bp != null) health.setBattery(bp);
            return false;
        }
        byte[] p = safePayload(f);
        if (p == null) return false; // passive/undecryptable frame — skip
        switch (f.cmd) {
            case Proto.ACTIVITY_DATA:
                health.addActivity(p);
                return false;
            case Proto.HEART_RATE_MANUAL_AUTO:
            case Proto.HEART_RATE_WORKOUT:
                health.addHeartRate(p);
                return false;
            case Proto.HEART_RATE_RESTING:
                health.addRestingHeartRate(p);
                return false;
            case Proto.SPO2:
                health.addSpo2(p);
                return false;
            case Proto.STRESS:
                health.addStress(p);
                return false;
            case Proto.SLEEP_DATA:
                health.addSleep(p);
                return false;
            case Proto.WORKOUT_SUMMARY:
                lastWorkout = Proto.hex(p);
                health.addWorkouts(p);
                return false;
            case Proto.ACTIVITY_FETCH_ACK_1:
                if (p.length > 0 && p[0] == 0x02) return true;
                return false;
            default:
                log.log("  (sync: skipping " + Proto.name(f.cmd) + " "
                        + Proto.hex(Proto.slice(p, 0, Math.min(48, p.length))) + ")");
                return false;
        }
    }

    /** True for notification frames that carry dashboard data. */
    private static boolean isHealthFrame(int cmd) {
        return cmd == Proto.ACTIVITY_DATA
                || cmd == Proto.HEART_RATE_MANUAL_AUTO
                || cmd == Proto.HEART_RATE_WORKOUT
                || cmd == Proto.HEART_RATE_RESTING
                || cmd == Proto.SPO2
                || cmd == Proto.STRESS
                || cmd == Proto.SLEEP_DATA
                || cmd == Proto.WORKOUT_SUMMARY;
    }

    /** Send a raw command from the health dashboard. */
    void sendRaw(int cmd1, int cmd2, byte[] payload) throws Exception {
        if (key == null) throw new IOException("not authenticated");
        int cmd = (cmd1 << 16) | cmd2;
        ble.writeFrames(false, cmd, payload, key);
        log.log("Sent " + Proto.name(cmd));
    }

    /** Decrypts an incoming frame for the companion event listener. */
    byte[] payloadOf(Proto.Frame f) throws Exception {
        return Proto.payload(f, key);
    }

    /** Records a battery notification seen outside a sync. */
    void noteBattery(Proto.Frame f) throws Exception {
        health.setBattery(safePayload(f));
    }

    /** Push the current phone media state to the watch (MUSIC_INFO_SET). */
    void sendMusicState(int state, int volume, int volumeMax, String track, String artist) throws Exception {
        if (key == null) throw new IOException("not authenticated");
        byte[] trackBytes = track == null ? new byte[0] : track.getBytes("UTF-8");
        byte[] artistBytes = artist == null ? new byte[0] : artist.getBytes("UTF-8");
        byte[] t = new byte[64], a = new byte[64];
        System.arraycopy(trackBytes, 0, t, 0, Math.min(trackBytes.length, 64));
        System.arraycopy(artistBytes, 0, a, 0, Math.min(artistBytes.length, 64));
        ByteBuffer buf = ByteBuffer.allocate(131).order(ByteOrder.BIG_ENDIAN);
        buf.put((byte) state);
        buf.put((byte) volume);
        buf.put((byte) volumeMax);
        buf.put(t);
        buf.put(new byte[64 - t.length]);
        buf.put(a);
        buf.put(new byte[64 - a.length]);
        byte[] payload = buf.array();
        ble.writeFrames(false, Proto.MUSIC_INFO_SET, payload, key);
        log.log("Music info sent: state=" + state + " \"" + track + "\" / \"" + artist + "\"");
    }

    /** Ring the watch (matches the reference's payload-less FIND_WATCH). */
    void sendFindWatch() throws Exception {
        sendRaw(Proto.FIND_WATCH >> 16, Proto.FIND_WATCH & 0xffff, new byte[0]);
    }

    /** Toggle the watch's do-not-disturb mode. */
    void sendDoNotDisturb(boolean enabled) throws Exception {
        sendRaw(Proto.DO_NOT_DISTURB >> 16, Proto.DO_NOT_DISTURB & 0xffff, new byte[]{(byte) (enabled ? 0x01 : 0x00)});
    }

    // ---- dial slots ---------------------------------------------------

    /**
     * Asks the watch for its installed-dial list with the documented type-0
     * {@code DIAL_COMMAND} query and returns a readable summary.
     */
    String probeWatchFaceList() throws Exception {
        if (key == null) throw new IOException("not authenticated");

        log.log("Dial list: querying DIAL_COMMAND type 0…");
        if (!requestDialList(3000)) {
            log.log("Dial list: no FFFF/A055 reply to the query");
        }

        if (dialIds.length == 0) {
            log.log("Dial list: the watch has not reported any installed faces yet");
            return "";
        }
        StringBuilder out = new StringBuilder();
        out.append("Installed: ").append(dialIds.length)
                .append(" of ").append(dialCapacity).append('\n');
        if (activeDial >= 0) out.append("Active index: ").append(activeDial).append('\n');
        for (int i = 0; i < dialIds.length; i++) {
            out.append(i == activeDial ? "▶ " : "  ")
                    .append("slot ").append(i + 1)
                    .append("  id ").append(dialIds[i]).append('\n');
        }
        String text = out.toString().trim();
        log.log("Dial list:\n" + text);
        return text;
    }

    private boolean requestDialList(long timeoutMs) throws Exception {
        ble.writeFrames(false, Proto.DIAL_COMMAND, new byte[]{0}, key);
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            Proto.Frame f = ble.pollFrame(Math.max(1, deadline - System.currentTimeMillis()));
            if (f == null) break;
            if (f.cmd == Proto.DIAL_LIST) {
                noteDialList(Proto.payload(f, key));
                return true;
            }
            if (f.cmd == Proto.BATTERY) {
                health.setBattery(safePayload(f));
            }
        }
        return false;
    }

    // ---- alarms -------------------------------------------------------

    /** One 40-byte alarm record in the watch's payload order. */
    static final class AlarmEntry {
        int hour, minute;
        boolean enabled;
        int repetition;
        String label = "";

        AlarmEntry(int hour, int minute, boolean enabled, int repetition, String label) {
            this.hour = hour;
            this.minute = minute;
            this.enabled = enabled;
            this.repetition = repetition;
            this.label = label == null ? "" : label;
        }

        int secondsOfDay() { return hour * 3600 + minute * 60; }

        String describe() {
            return String.format("%02d:%02d", hour, minute)
                    + (enabled ? "" : " (off)")
                    + " " + repeatName(repetition)
                    + (label.isEmpty() ? "" : " \"" + label + "\"");
        }
    }

    static String repeatName(int rep) {
        if (rep == 0) return "once";
        if ((rep & 0x7f) == 0x7f) return "daily";
        String[] names = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 7; i++) {
            if ((rep & (1 << i)) != 0) {
                if (sb.length() > 0) sb.append(',');
                sb.append(names[i]);
            }
        }
        return sb.length() == 0 ? "once" : sb.toString();
    }

    /** Reads the watch's current alarm list, or an empty list when it does not answer. */
    java.util.List<AlarmEntry> readAlarms() throws Exception {
        if (key == null) throw new IOException("not authenticated");
        byte[] p = requestPayload(Proto.ALARMS_GET, 3000);
        java.util.List<AlarmEntry> out = new java.util.ArrayList<>();
        if (p == null) {
            log.log("Alarms: watch did not report its list");
            return out;
        }
        if (p.length % 40 != 0) {
            log.log("Alarms raw (unparsed, " + p.length + " B): " + Proto.hex(p));
            return out;
        }
        log.log("Alarms raw (" + p.length + " B): " + Proto.hex(p));
        for (int off = 0; off + 40 <= p.length; off += 40) {
            int sec = (int) Proto.u32be(p, off);
            boolean enabled = p[off + 5] == 0x01;
            int rep = p[off + 6] & 0xff;
            String label = Proto.cstring(Proto.slice(p, off + 32, off + 40), 0);
            log.log("  alarm@" + off + " sec=" + sec + " idx=" + (p[off + 4] & 0xff)
                    + " on=" + enabled + " rep=" + rep + " b7=" + (p[off + 7] & 0xff));
            out.add(new AlarmEntry(sec / 3600, (sec % 3600) / 60, enabled, rep, label == null ? "" : label.trim()));
        }
        log.log("Alarms: watch reports " + out.size() + " alarm(s)");
        return out;
    }

    /**
     * Sends an arbitrary ALARMS_SET payload and polls the read-back so the
     * asynchronous apply behaviour can be observed. Experimental.
     */
    String sendRawAlarm(byte[] payload) throws Exception {
        if (key == null) throw new IOException("not authenticated");
        ble.writeFrames(false, Proto.ALARMS_SET, payload, key);
        String last = "";
        for (int i = 0; i < 8; i++) {
            Thread.sleep(900);
            byte[] back = requestFamily(Proto.ALARMS_GET, new byte[]{(byte) 0xA5}, 1500);
            String hex = back == null ? "" : Proto.hex(back);
            log.log("  +" + ((i + 1) * 900) + "ms → " + (hex.isEmpty() ? "(none)" : hex));
            if (!hex.isEmpty()) last = hex;
        }
        log.log("Raw alarm write " + payload.length + " B → last read-back "
                + (last.isEmpty() ? "nothing" : last.length() / 2 + " B: " + last));
        return last;
    }

    /**
     * Writes the complete alarm list in one command, mirroring the reference
     * client: big-endian seconds-of-day, 0xff at byte 7, 8-byte right-aligned
     * label. Batched records were previously avoided only because Gadgetbridge
     * was concurrently overwriting the list.
     */
    void sendAlarms(java.util.List<AlarmEntry> alarms) throws Exception {
        if (key == null) throw new IOException("not authenticated");
        if (alarms.size() > 5) throw new IOException("the watch only holds 5 alarms");
        byte[] p = new byte[40 * alarms.size()];
        for (int index = 0; index < alarms.size(); index++) {
            AlarmEntry a = alarms.get(index);
            int off = index * 40;
            Proto.putU32be(p, off, a.secondsOfDay());
            p[off + 4] = (byte) index;
            p[off + 5] = (byte) (a.enabled ? 0x01 : 0x00);
            p[off + 6] = (byte) a.repetition;
            p[off + 7] = (byte) 0xff;
            // bytes 8..31 reserved
            byte[] label = truncateUtf8(a.label, 8);
            System.arraycopy(label, 0, p, off + 32 + (8 - label.length), label.length);
            log.log("  alarm slot " + index + ": " + a.describe());
        }
        ble.writeFrames(false, Proto.ALARMS_SET, p, key);
        log.log("Alarms sent: " + alarms.size() + " slot(s), " + p.length + " B");
    }

    // ---- contacts -----------------------------------------------------

    static final class ContactEntry {
        String name, number;
        ContactEntry(String name, String number) {
            this.name = name == null ? "" : name;
            this.number = number == null ? "" : number;
        }
    }

    java.util.List<ContactEntry> readContacts() throws Exception {
        if (key == null) throw new IOException("not authenticated");
        byte[] p = requestPayload(Proto.CONTACTS_GET, 3000);
        java.util.List<ContactEntry> out = new java.util.ArrayList<>();
        if (p == null) {
            log.log("Contacts: watch did not report its list");
            return out;
        }
        if (p.length % 57 != 0) {
            log.log("Contacts raw (unparsed, " + p.length + " B): " + Proto.hex(p));
            return out;
        }
        for (int off = 0; off + 57 <= p.length; off += 57) {
            String name = Proto.cstring(Proto.slice(p, off, off + 32), 0);
            String number = Proto.cstring(Proto.slice(p, off + 32, off + 57), 0);
            out.add(new ContactEntry(name == null ? "" : name, number == null ? "" : number));
        }
        log.log("Contacts: watch reports " + out.size() + " contact(s)");
        return out;
    }

    void sendContacts(java.util.List<ContactEntry> contacts) throws Exception {
        if (key == null) throw new IOException("not authenticated");
        if (contacts.size() > 20) throw new IOException("the watch holds at most 20 contacts");
        byte[] p = new byte[57 * contacts.size()];
        for (int i = 0; i < contacts.size(); i++) {
            ContactEntry c = contacts.get(i);
            int off = i * 57;
            byte[] name = truncateUtf8(c.name, 32);
            byte[] number = truncateUtf8(c.number, 25);
            System.arraycopy(name, 0, p, off, name.length);
            System.arraycopy(number, 0, p, off + 32, number.length);
        }
        ble.writeFrames(false, Proto.CONTACTS_SET, p, key);
        log.log("Contacts sent: " + contacts.size());
    }

    // ---- weather ------------------------------------------------------

    /** Back-compat single-value entry: repeats the same day for all 7 slots. */
    void sendWeather(int condition, int tempC, int minC, int maxC, int humidity,
                     String location, double latitude, double longitude) throws Exception {
        int[] c = new int[7], t = new int[7], mn = new int[7], mx = new int[7], h = new int[7];
        for (int i = 0; i < 7; i++) { c[i] = condition; t[i] = tempC; mn[i] = minC; mx[i] = maxC; h[i] = humidity; }
        sendWeather(c, t, mn, mx, h, location, latitude, longitude);
    }

    /**
     * Sends a 7-day weather snapshot in the CMF Watch Pro 2 layout:
     * 7 day entries (9 B each), 24 hourly (2 B each),
     * a 32-byte location, then 7 little-endian sunrise/sunset pairs.
     */
    void sendWeather(int[] condition, int[] tempC, int[] minC, int[] maxC, int[] humidity,
                     String location, double latitude, double longitude) throws Exception {
        if (key == null) throw new IOException("not authenticated");
        if (condition == null || condition.length < 7) throw new IOException("weather: need 7 days");
        byte[] p = new byte[199];
        int o = 0;
        // 7 day entries.
        for (int day = 0; day < 7; day++) {
            int c = condition[day] & 0xff;
            int t = (tempC[day] + 100) & 0xff;
            int hi = (maxC[day] + 100) & 0xff;
            int lo = (minC[day] + 100) & 0xff;
            int hu = humidity[day] & 0xff;
            p[o] = (byte) c;
            p[o + 1] = (byte) t;
            p[o + 2] = (byte) hi;
            p[o + 3] = (byte) lo;
            p[o + 4] = (byte) hu;
            p[o + 5] = 0; p[o + 6] = 0;          // AQI
            p[o + 7] = 3;                         // UV index
            p[o + 8] = 0;                         // wind speed
            o += 9;
        }
        // Hourly: use today's temperature/condition for all 24 hours.
        int todayT = (tempC[0] + 100) & 0xff;
        int todayC = condition[0] & 0xff;
        for (int h = 0; h < 24; h++) {
            p[o++] = (byte) todayT;
            p[o++] = (byte) todayC;
        }
        // 30-byte location padded to 32.
        byte[] loc = truncateUtf8(location == null ? "" : location, 30);
        System.arraycopy(loc, 0, p, o, loc.length);
        o += 32;

        // Sunrise/sunset for today and the next six days, little-endian epoch seconds.
        long now = System.currentTimeMillis();
        java.util.Calendar cal = java.util.Calendar.getInstance();
        for (int day = 0; day < 7; day++) {
            cal.setTimeInMillis(now);
            cal.add(java.util.Calendar.DAY_OF_MONTH, day);
            long[] riseSet = sunTimes(latitude, longitude, cal.getTimeInMillis());
            long rise = riseSet == null ? 0 : riseSet[0];
            long set = riseSet == null ? 0 : riseSet[1];
            putU32le(p, o, rise);
            putU32le(p, o + 4, set);
            o += 8;
        }
        ble.writeFrames(false, Proto.WEATHER_SET_1, p, key);
        log.log("Weather sent: " + (condition[0] & 0xff) + " " + tempC[0] + "C (" + minC[0] + "…" + maxC[0] + "), "
                + humidity[0] + "%, \"" + (location == null ? "" : location) + "\"");
    }

    /** Approximate sunrise/sunset (UTC epoch seconds) for a date and location. */
    private static long[] sunTimes(double lat, double lon, long dayMillis) {
        double rad = Math.PI / 180.0;
        double n = Math.floor(dayMillis / 86400000.0 - 10957.5 + 0.0008);
        double jStar = n - lon / 360.0;
        double m = mod360(357.5291 + 0.98560028 * jStar);
        double c = 1.9148 * Math.sin(m * rad) + 0.0200 * Math.sin(2 * m * rad)
                + 0.0003 * Math.sin(3 * m * rad);
        double lambda = mod360(m + c + 180 + 102.9372);
        double jTransit = 2451545.0 + jStar + 0.0053 * Math.sin(m * rad)
                - 0.0069 * Math.sin(2 * lambda * rad);
        double sinDecl = Math.sin(lambda * rad) * Math.sin(23.4397 * rad);
        double cosDecl = Math.cos(Math.asin(sinDecl));
        double cosH = (Math.sin(-0.833 * rad) - Math.sin(lat * rad) * sinDecl)
                / (Math.cos(lat * rad) * cosDecl);
        if (cosH > 1 || cosH < -1) return null;
        double h = Math.acos(cosH) / rad;
        double jRise = jTransit - h / 360.0;
        double jSet = jTransit + h / 360.0;
        return new long[]{
                Math.round((jRise - 2440587.5) * 86400.0),
                Math.round((jSet - 2440587.5) * 86400.0)
        };
    }

    private static double mod360(double v) {
        double r = v % 360.0;
        return r < 0 ? r + 360.0 : r;
    }

    private static void putU32le(byte[] b, int off, long v) {
        b[off] = (byte) v;
        b[off + 1] = (byte) (v >>> 8);
        b[off + 2] = (byte) (v >>> 16);
        b[off + 3] = (byte) (v >>> 24);
    }

    /**
     * Sends a request and returns the first reply payload from the same command
     * family, or null. Replies may use a different second word (e.g. GET/SET).
     * Both the 0xA5 query payload and an empty payload are tried, since the
     * getters are not consistently documented.
     */
    private byte[] requestPayload(int request, long timeoutMs) throws Exception {
        byte[] p = requestFamily(request, new byte[]{(byte) 0xA5}, timeoutMs);
        if (p == null) p = requestFamily(request, new byte[0], timeoutMs);
        return p;
    }

    private byte[] requestFamily(int request, byte[] payload, long timeoutMs) throws Exception {
        int family = request >>> 16;
        ble.writeFrames(false, request, payload, key);
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (true) {
            long left = deadline - System.currentTimeMillis();
            if (left <= 0) return null;
            Proto.Frame f = ble.pollFrame(left);
            if (f == null) return null;
            if (f.cmd == Proto.AUTH_FAILED) throw new IOException("watch dropped the session");
            if (f.cmd == Proto.BATTERY) {
                health.setBattery(safePayload(f));
                continue;
            }
            if ((f.cmd >>> 16) == family) return Proto.payload(f, key);
            log.log("  (waiting: skipping " + Proto.name(f.cmd) + ")");
        }
    }

    // ---- helpers ------------------------------------------------------

    /** Drops stale frames but keeps any passive battery notification already queued. */
    private void clearFramesKeepingBattery() throws Exception {
        Proto.Frame f;
        while ((f = ble.pollFrame(0)) != null) {
            if (f.cmd == Proto.BATTERY) health.setBattery(safePayload(f));
        }
        ble.clearFrames();
    }

    /** Decrypts a frame; returns null for passive/undecryptable frames so a stray
     *  notification (e.g. the plaintext 0xa06b language reply) cannot abort a sync. */
    private byte[] safePayload(Proto.Frame f) {
        try { return Proto.payload(f, key); }
        catch (Throwable t) { return null; }
    }

    private byte[] await(int cmd, long timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (true) {
            long left = deadline - System.currentTimeMillis();
            if (left <= 0) throw new IOException("timeout waiting for " + Proto.name(cmd));
            Proto.Frame f = ble.pollFrame(left);
            if (f == null) throw new IOException("timeout waiting for " + Proto.name(cmd));
            if (f.cmd == Proto.AUTH_FAILED) {
                throw new IOException("watch rejected the auth key (AUTH_FAILED)");
            }
            if (f.cmd == Proto.BATTERY) {
                // Passive battery notification: keep it even while waiting for another reply.
                byte[] bp = safePayload(f);
                if (bp != null) health.setBattery(bp);
                continue;
            }
            if (f.cmd != cmd) {
                if (isHealthFrame(f.cmd)) {
                    consume(f);
                } else {
                    byte[] p = safePayload(f);
                    if (p != null) log.log("  (skipping " + Proto.name(f.cmd) + " "
                            + Proto.hex(Proto.slice(p, 0, Math.min(64, p.length))) + ")");
                }
                continue;
            }
            return Proto.payload(f, key);
        }
    }

    /** Basic .bin sanity checks, mirroring the reference client. */
    private static String validate(byte[] f) throws IOException {
        if (f.length < 36) throw new IOException("file too small: " + f.length + " bytes");
        if (f[4] != 1 || f[5] != 0 || f[6] != 0 || (f[7] != 0 && f[7] != 2)) {
            throw new IOException("not a watch face: missing 01 00 00 0x magic at offset 4");
        }
        if (f.length > (32 << 20)) throw new IOException("file larger than 32 MB");
        String head = Proto.cstring(f, 8);
        String tail = Proto.cstring(f, f.length - 28);
        if (head == null || head.isEmpty()) throw new IOException("no dial name at offset 8");
        if (!head.equals(tail)) throw new IOException("trailer name mismatch — corrupted file?");
        return head;
    }

    private static String msg(Throwable t) {
        return t.getMessage() == null ? t.toString() : t.getMessage();
    }
}
