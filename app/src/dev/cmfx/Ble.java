package dev.cmfx;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothStatusCodes;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.os.Build;
import android.util.Log;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Thin BLE transport for the CMF dial protocol.
 *
 * Writes are serialised because Android's GATT stack allows one outstanding operation at a time.
 * Incoming notifications are decoded into complete protocol frames and queued; the protocol
 * worker consumes them with a timeout.
 */
final class Ble {

    static final UUID CMD_WRITE   = UUID.fromString("0000fff2-0000-1000-8000-00805f9b34fb");
    static final UUID CMD_READ    = UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb");
    static final UUID DATA_WRITE  = UUID.fromString("02f00000-0000-0000-0000-00000000ffe1");
    static final UUID DATA_READ   = UUID.fromString("02f00000-0000-0000-0000-00000000ffe2");
    static final UUID SHELL_WRITE = UUID.fromString("77d4ff01-2fe2-2334-0d35-9ccd078f529c");
    static final UUID SHELL_READ  = UUID.fromString("77d4ff02-2fe2-2334-0d35-9ccd078f529c");
    static final UUID CCC         = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    interface Log { void log(String line); }
    interface FrameListener { void onFrame(Proto.Frame f); }

    private final Log log;
    private volatile FrameListener frameListener;
    private final Object writeLock = new Object();
    private final Semaphore writeDone = new Semaphore(0);
    private final Semaphore descDone = new Semaphore(0);
    private volatile int writeStatus = -1;
    private volatile int descStatus = -1;

    private final LinkedBlockingQueue<Proto.Frame> frames = new LinkedBlockingQueue<>();
    private final Map<Integer, byte[][]> pending = new HashMap<>();
    private final Map<Integer, Integer> pendingIndex = new HashMap<>();

    private final StringBuilder shellBuf = new StringBuilder();
    private final Object shellLock = new Object();

    private volatile BluetoothGatt gatt;
    private BluetoothGattCharacteristic cmdWrite, dataWrite, shellWrite;
    private BluetoothGattCharacteristic cmdRead, dataRead, shellRead;
    private final CountDownLatch ready = new CountDownLatch(1);
    private volatile String readyError;
    private volatile boolean connected;
    private volatile boolean dead;
    private volatile int mtu = 23;
    private volatile String name = "watch";

    Ble(Log log) {
        this.log = log;
    }

    int mtu() { return mtu; }
    String name() { return name; }
    /** True only while a live GATT session exists; a dead session counts as disconnected. */
    boolean isConnected() { return connected && !dead; }
    /** True once the session was marked dead (e.g. after a DeadObjectException) and must be re-created. */
    boolean isDead() { return dead; }

    // ---- connection ---------------------------------------------------

    boolean connect(Context ctx, BluetoothDevice dev, long timeoutMs) throws Exception {
        name = dev.getName() == null ? dev.getAddress() : dev.getName();
        log.log("Connecting to " + name + " (" + dev.getAddress() + ")…");
        if (Build.VERSION.SDK_INT >= 23) {
            gatt = dev.connectGatt(ctx, false, callback, BluetoothDevice.TRANSPORT_LE);
        } else {
            gatt = dev.connectGatt(ctx, false, callback);
        }
        if (gatt == null) {
            log.log("connectGatt returned null");
            return false;
        }
        if (!ready.await(timeoutMs, TimeUnit.MILLISECONDS)) {
            log.log("Timed out setting up GATT (watch connected to another app?)");
            close();
            return false;
        }
        if (readyError != null) {
            log.log("GATT setup failed: " + readyError);
            close();
            return false;
        }
        if (!enableNotifications()) {
            close();
            return false;
        }
        log.log("Ready. MTU=" + mtu + "  notifications on fff1/ffe2/77d4ff02");
        return true;
    }

    /**
     * Enables notifications from the caller's thread. Doing this inside
     * onServicesDiscovered would block the callback thread while waiting for
     * onDescriptorWrite, which can deadlock when both share a looper.
     */
    private boolean enableNotifications() throws InterruptedException {
        boolean ok = true;
        if (!subscribe(cmdRead)) {
            log.log("Could not enable command notifications (fff1)");
            ok = false;
        }
        if (!subscribe(dataRead)) {
            log.log("Could not enable data notifications (ffe2)");
            ok = false;
        }
        if (shellRead != null) {
            if (!subscribe(shellRead)) log.log("Shell notifications unavailable — pairing may fail");
        } else {
            log.log("Shell notification characteristic not found — pairing may fail");
        }
        return ok;
    }

    void close() {
        connected = false;
        dead = true;
        BluetoothGatt g = gatt;
        gatt = null;
        if (g != null) {
            try { g.disconnect(); } catch (Throwable ignored) {}
            try { g.close(); } catch (Throwable ignored) {}
        }
    }

    private final BluetoothGattCallback callback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt g, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                log.log("BLE connected (status " + status + "), negotiating MTU…");
                try { g.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH); } catch (Throwable ignored) {}
                if (!g.requestMtu(247)) {
                    log.log("MTU request not accepted, continuing with default");
                    g.discoverServices();
                }
            } else {
                connected = false;
                log.log("BLE disconnected");
                ready.countDown();
            }
        }

        @Override
        public void onMtuChanged(BluetoothGatt g, int newMtu, int status) {
            mtu = newMtu;
            if (status == BluetoothGatt.GATT_SUCCESS) {
                log.log("MTU negotiated: " + newMtu);
            } else {
                log.log("MTU request failed (status " + status + "), using " + newMtu);
            }
            g.discoverServices();
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt g, int status) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                readyError = "service discovery status " + status;
                ready.countDown();
                return;
            }
            try {
                cmdWrite = charOf(g, CMD_WRITE);
                dataWrite = charOf(g, DATA_WRITE);
                shellWrite = charOf(g, SHELL_WRITE);
                cmdRead = charOf(g, CMD_READ);
                dataRead = charOf(g, DATA_READ);
                shellRead = charOf(g, SHELL_READ);

                if (cmdRead == null || dataRead == null) {
                    readyError = "required characteristics missing (fff1=" + (cmdRead != null)
                            + ", ffe2=" + (dataRead != null) + ")";
                    ready.countDown();
                    return;
                }
                if (cmdWrite == null || dataWrite == null) {
                    readyError = "required write characteristics missing (fff2="
                            + (cmdWrite != null) + ", ffe1=" + (dataWrite != null) + ")";
                    ready.countDown();
                    return;
                }

                connected = true;
                ready.countDown();
            } catch (Throwable t) {
                readyError = String.valueOf(t);
                ready.countDown();
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt g, BluetoothGattCharacteristic c, byte[] value) {
            onNotify(c.getUuid(), value);
        }

        @SuppressWarnings("deprecation")
        @Override
        public void onCharacteristicChanged(BluetoothGatt g, BluetoothGattCharacteristic c) {
            onNotify(c.getUuid(), c.getValue());
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt g, BluetoothGattCharacteristic c, int status) {
            writeStatus = status;
            writeDone.release();
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt g, BluetoothGattDescriptor d, int status) {
            descStatus = status;
            descDone.release();
        }
    };

    private static BluetoothGattCharacteristic charOf(BluetoothGatt g, UUID uuid) {
        for (android.bluetooth.BluetoothGattService s : g.getServices()) {
            BluetoothGattCharacteristic c = s.getCharacteristic(uuid);
            if (c != null) return c;
        }
        return null;
    }

    /** Enables notifications/indications; returns false when the CCC write fails. */
    private boolean subscribe(BluetoothGattCharacteristic c) throws InterruptedException {
        if (c == null) return false;
        BluetoothGatt g = gatt;
        if (g == null) return false;
        try {
            g.setCharacteristicNotification(c, true);
            BluetoothGattDescriptor d = c.getDescriptor(CCC);
            if (d == null) return false;
            boolean indicate = (c.getProperties() & BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0;
            d.setValue(indicate ? BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                    : BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            descDone.drainPermits();
            if (!g.writeDescriptor(d)) {
                log.log("writeDescriptor rejected on " + c.getUuid());
                return false;
            }
            long deadline = System.currentTimeMillis() + 3000;
            while (!descDone.tryAcquire(250, TimeUnit.MILLISECONDS)) {
                if (System.currentTimeMillis() > deadline) {
                    log.log("Notification enable timed out on " + c.getUuid());
                    return false;
                }
            }
            if (descStatus != BluetoothGatt.GATT_SUCCESS) {
                log.log("Notification enable status " + descStatus + " on " + c.getUuid());
                return false;
            }
            return true;
        } catch (Throwable t) {
            log.log("Subscribe failed on " + c.getUuid() + ": " + t);
            return false;
        }
    }

    // ---- inbound ------------------------------------------------------

    private void onNotify(UUID uuid, byte[] value) {
        if (value == null) return;
        if (SHELL_READ.equals(uuid)) {
            synchronized (shellLock) {
                shellBuf.append(new String(value, java.nio.charset.Charset.forName("UTF-8")));
                shellLock.notifyAll();
            }
            return;
        }
        Proto.Frame f = Proto.parse(value);
        if (f == null) return;

        if (f.count > 1) {
            byte[][] slot = pending.get(f.cmd);
            if (slot == null || f.index == 1) {
                slot = new byte[f.count][];
                pending.put(f.cmd, slot);
            }
            if (f.index < 1 || f.index > f.count) return;
            slot[f.index - 1] = f.body;
            for (byte[] part : slot) if (part == null) return;
            pending.remove(f.cmd);
            int total = 0;
            for (byte[] part : slot) total += part.length;
            byte[] joined = new byte[total];
            int o = 0;
            for (byte[] part : slot) {
                System.arraycopy(part, 0, joined, o, part.length);
                o += part.length;
            }
            f.body = joined;
        }
        frames.add(f);
        if (f.cmd == Proto.BATTERY) log.log("Battery notification received");
        FrameListener l = frameListener;
        if (l != null) {
            try { l.onFrame(f); } catch (Throwable ignored) {}
        }
    }

    Proto.Frame pollFrame(long timeoutMs) throws InterruptedException {
        return frames.poll(timeoutMs, TimeUnit.MILLISECONDS);
    }

    void clearFrames() {
        frames.clear();
        pending.clear();
    }

    void setFrameListener(FrameListener listener) { frameListener = listener; }

    /** Sends AT GETSECRET and returns the raw shell reply, e.g. "GETSECRET:…,OK". */
    String shellCommand(String cmd, long timeoutMs) throws IOException {
        if (shellWrite == null) throw new IOException("shell characteristic missing");
        synchronized (shellLock) {
            shellBuf.setLength(0);
        }
        write(shellWrite, cmd.getBytes(java.nio.charset.Charset.forName("US-ASCII")),
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT, 3000);
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            synchronized (shellLock) {
                String s = shellBuf.toString();
                if (s.contains(",OK") || s.contains(",ERR") || s.contains("ERROR")) return s.trim();
                long wait = Math.max(50, deadline - System.currentTimeMillis());
                try {
                    shellLock.wait(Math.min(wait, 250));
                } catch (InterruptedException e) {
                    throw new IOException("interrupted");
                }
            }
        }
        synchronized (shellLock) {
            return shellBuf.toString().trim();
        }
    }

    // ---- outbound -----------------------------------------------------

    /** Writes one characteristic value, serialising with all other GATT writes. */
    boolean write(BluetoothGattCharacteristic c, byte[] data, int writeType, int timeoutMs)
            throws IOException {
        BluetoothGatt g = gatt;
        if (g == null || c == null) throw new IOException("not connected");
        synchronized (writeLock) {
            writeStatus = -1;
            writeDone.drainPermits();
            int rc;
            if (Build.VERSION.SDK_INT >= 33) {
                try {
                    rc = g.writeCharacteristic(c, data, writeType);
                } catch (Throwable t) {
                    // The GATT binder proxy is gone (observed with the CMF watch): the session
                    // can no longer be used, so flag it dead and surface a reconnect-worthy error.
                    dead = true;
                    connected = false;
                    log.log("GATT write failed: " + t + " — marking session dead");
                    throw new IOException("BLE GATT session is dead — reconnect required");
                }
            } else {
                try {
                    c.setWriteType(writeType);
                    c.setValue(data);
                    rc = g.writeCharacteristic(c) ? BluetoothStatusCodes.SUCCESS : -1;
                } catch (Throwable t) {
                    dead = true;
                    connected = false;
                    log.log("GATT write failed: " + t + " — marking session dead");
                    throw new IOException("BLE GATT session is dead — reconnect required");
                }
            }
            if (rc != BluetoothStatusCodes.SUCCESS) {
                throw new IOException("writeCharacteristic rc=" + rc + " (" + data.length + "B)");
            }
            try {
                if (!writeDone.tryAcquire(timeoutMs, TimeUnit.MILLISECONDS)) {
                    // No-response writes are sometimes not acknowledged by the stack.
                    if (writeType == BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE) {
                        Thread.sleep(12);
                        return true;
                    }
                    return false;
                }
            } catch (InterruptedException e) {
                throw new IOException("interrupted");
            }
            if (writeStatus != BluetoothGatt.GATT_SUCCESS) {
                log.log("write status " + writeStatus + " for " + c.getUuid());
                return false;
            }
            return true;
        }
    }

    void writeFrames(boolean dataChannel, int cmd, byte[] payload, byte[] key) throws Exception {
        BluetoothGattCharacteristic c = dataChannel ? dataWrite : cmdWrite;
        if (c == null) throw new IOException("characteristic missing");
        // The reference client always frames for a 180-byte MTU; matching it keeps
        // chunk sizes identical to the known-good client even on wider links.
        int frameMtu = Math.min(mtu, 180);
        for (byte[] frame : Proto.encode(cmd, payload, key, frameMtu)) {
            if (!write(c, frame, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE, 1200)) {
                throw new IOException("write failed for " + Proto.name(cmd));
            }
        }
    }

    // ---- discovery ----------------------------------------------------

    interface Found { void onDevice(BluetoothDevice dev); }

    /**
     * Prefers an already-bonded watch (Nothing X bonded it once, and bonding is system-wide),
     * then falls back to a 15 s scan for a device whose name contains "Watch" or "CMF".
     */
    static BluetoothDevice findWatch(Context ctx, Log log, long scanMs) throws Exception {
        BluetoothManager mgr = (BluetoothManager) ctx.getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter adapter = mgr == null ? null : mgr.getAdapter();
        if (adapter == null || !adapter.isEnabled()) throw new IOException("Bluetooth is off");

        for (BluetoothDevice d : adapter.getBondedDevices()) {
            String n = d.getName();
            if (n != null && (n.startsWith("CMF Watch") || n.contains("Watch"))) {
                log.log("Using bonded watch: " + n);
                return d;
            }
        }

        log.log("Scanning for a watch…");
        final BluetoothLeScanner scanner = adapter.getBluetoothLeScanner();
        if (scanner == null) throw new IOException("scanner unavailable");
        final ConcurrentLinkedQueue<BluetoothDevice> found = new ConcurrentLinkedQueue<>();
        final ConcurrentLinkedQueue<String> seen = new ConcurrentLinkedQueue<>();

        ScanCallback cb = new ScanCallback() {
            @Override public void onScanResult(int type, ScanResult result) {
                BluetoothDevice d = result.getDevice();
                String n = d.getName();
                if (n == null) {
                    ScanRecord r = result.getScanRecord();
                    if (r != null) n = r.getDeviceName();
                }
                if (n == null) return;
                if (!seen.contains(n)) {
                    seen.add(n);
                    log.log("  saw " + n + " (" + d.getAddress() + ")");
                }
                if (n.contains("Watch") || n.startsWith("CMF")) found.add(d);
            }
            @Override public void onScanFailed(int errorCode) {
                log.log("Scan failed: " + errorCode);
            }
        };

        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();
        scanner.startScan(null, settings, cb);
        try {
            long deadline = System.currentTimeMillis() + scanMs;
            while (System.currentTimeMillis() < deadline && found.isEmpty()) {
                Thread.sleep(200);
            }
        } finally {
            try { scanner.stopScan(cb); } catch (Throwable ignored) {}
        }
        BluetoothDevice d = found.poll();
        if (d == null) {
            throw new IOException("No watch found. Turn Bluetooth on, wake the watch, and make sure "
                    + "Nothing X is not connected to it.");
        }
        log.log("Selected " + d.getName() + " (" + d.getAddress() + ")");
        return d;
    }
}
