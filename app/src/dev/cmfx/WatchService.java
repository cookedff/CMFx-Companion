package dev.cmfx;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import org.json.JSONArray;
import org.json.JSONObject;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Persistent companion BLE session used by notification forwarding. */
public final class WatchService extends Service {
    private static final String CHANNEL = "companion";
    private static volatile WatchService instance;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private volatile Watch watch;
    private volatile Ble ble;
    private volatile boolean stopped = false;
    private volatile boolean connecting = false;
    private final Handler weatherHandler = new Handler(Looper.getMainLooper());
    private final Runnable weatherSyncRunnable = new Runnable() {
        public void run() {
            worker.execute(WatchService.this::doWeatherSync);
            weatherHandler.postDelayed(this, 3600000L);
        }
    };
    private Ringtone ringtone;
    private final java.util.Set<Integer> loggedUnknown =
            java.util.Collections.synchronizedSet(new java.util.HashSet<>());

    static void forward(int icon, String sender, String body) {
        WatchService s = instance;
        if (s != null) s.send(icon, sender, body);
    }

    static Ble sessionBle() { WatchService s = instance; return s == null ? null : s.ble; }
    static Watch sessionWatch() { WatchService s = instance; return s == null ? null : s.watch; }
    static boolean sessionConnected() { Ble b = instance == null ? null : instance.ble; return b != null && b.isConnected(); }

    /** Waits for the single service-owned session to become usable. */
    static boolean awaitConnected(long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (sessionConnected()) return true;
            try { Thread.sleep(250); } catch (InterruptedException e) { return false; }
        }
        return sessionConnected();
    }

    @Override public void onCreate() {
        super.onCreate();
        instance = this;
        Watch.init(this);
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(
                new NotificationChannel(CHANNEL, "CMFx companion", NotificationManager.IMPORTANCE_LOW));
        Notification n = new Notification.Builder(this, CHANNEL)
                .setContentTitle("CMFx companion active")
                .setContentText("Forwarding watch notifications")
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .setOngoing(true).build();
        startForeground(42, n);
        worker.execute(this::connect);
        weatherHandler.postDelayed(weatherSyncRunnable, 30000);
    }

    private static void log(String line) { android.util.Log.i("CMFx", line); }

    private void connect() {
        if (stopped || connecting) return;
        Ble existing = ble;
        if (existing != null && existing.isConnected() && watch != null) return;
        connecting = true;
        try {
            BluetoothDevice d = Ble.findWatch(this, WatchService::log, 10000);
            Ble b = new Ble(WatchService::log);
            if (!b.connect(this, d, 20000)) throw new IOException("BLE connection failed");
            Watch w = new Watch(b, WatchService::log, p -> {});
            b.setFrameListener(f -> onWatchFrame(w, f));
            w.handshake();
            ble = b; watch = w;
        } catch (Throwable t) {
            android.util.Log.i("CMFx", "Companion connect deferred: " + t);
            Handler h = new Handler(Looper.getMainLooper());
            h.postDelayed(() -> { if (!stopped) worker.execute(WatchService.this::connect); }, 10000);
        } finally {
            connecting = false;
        }
    }

    /** Periodic weather sync: fetches weather for the saved location and pushes it to the watch. */
    private void doWeatherSync() {
        if (stopped) return;
        Watch w = watch; Ble b = ble;
        if (w == null || b == null || !b.isConnected()) return;
        SharedPreferences prefs = getSharedPreferences("cmfx_weather", Context.MODE_PRIVATE);
        String location = prefs.getString("location", "");
        if (location == null || location.trim().isEmpty()) return;
        location = location.trim();
        try {
            String url = "https://wttr.in/" + URLEncoder.encode(location, "UTF-8") + "?format=j1";
            String text = httpText(url);
            JSONObject root = new JSONObject(text);
            // location + lat/lon
            String locName = location;
            double la = 0, lo = 0;
            JSONArray areas = root.optJSONArray("nearest_area");
            if (areas != null && areas.length() > 0) {
                JSONObject a = areas.getJSONObject(0);
                JSONArray an = a.optJSONArray("areaName");
                if (an != null && an.length() > 0) locName = an.getJSONObject(0).optString("value", location);
                JSONArray cn = a.optJSONArray("country");
                if (cn != null && cn.length() > 0) locName = locName + ", " + cn.getJSONObject(0).optString("value", "");
                try { la = Double.parseDouble(a.optString("latitude", "0")); } catch (Throwable ignored) {}
                try { lo = Double.parseDouble(a.optString("longitude", "0")); } catch (Throwable ignored) {}
            }
            // current
            int temp = 0, hum = 0;
            String condition = "";
            JSONArray cur = root.optJSONArray("current_condition");
            if (cur != null && cur.length() > 0) {
                JSONObject c = cur.getJSONObject(0);
                try { temp = Integer.parseInt(c.optString("temp_C", "0")); } catch (Throwable ignored) {}
                JSONArray desc = c.optJSONArray("weatherDesc");
                if (desc != null && desc.length() > 0) condition = desc.getJSONObject(0).optString("value", "");
                try { hum = Integer.parseInt(c.optString("humidity", "0")); } catch (Throwable ignored) {}
            }
            // daily min/max
            int mn = temp, mx = temp;
            JSONArray weather = root.optJSONArray("weather");
            if (weather != null && weather.length() > 0) {
                JSONObject w0 = weather.getJSONObject(0);
                try { mx = Integer.parseInt(w0.optString("maxtempC", String.valueOf(temp))); } catch (Throwable ignored) {}
                try { mn = Integer.parseInt(w0.optString("mintempC", String.valueOf(temp))); } catch (Throwable ignored) {}
            }
            int code = cmfConditionCode(condition, isNightTime());
            w.sendWeather(code, temp, mn, mx, hum, locName, la, lo);
            log("Weather auto-synced: " + condition + " " + temp + "C (" + mn + "…" + mx + "), " + hum + "%, \"" + locName + "\"");
        } catch (Throwable t) {
            log("Weather auto-sync failed: " + t);
        }
    }

    private static String httpText(String address) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(address).openConnection();
        c.setRequestMethod("GET");
        c.setConnectTimeout(15000);
        c.setReadTimeout(15000);
        c.setRequestProperty("User-Agent", "curl/8.0");
        c.setRequestProperty("Accept", "application/json");
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return new String(out.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
        } finally {
            c.disconnect();
        }
    }

    private static boolean isNightTime() {
        int h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        return h < 6 || h >= 18;
    }

    private static int cmfConditionCode(String condition, boolean night) {
        String c = condition == null ? "" : condition.toLowerCase(java.util.Locale.US);
        boolean thunder = c.contains("thunder") || c.contains("storm");
        boolean snow = c.contains("snow") || c.contains("blizzard") || c.contains("ice");
        boolean sleet = c.contains("sleet");
        boolean rain = c.contains("rain") || c.contains("drizzle") || c.contains("shower");
        boolean fog = c.contains("fog");
        boolean mist = c.contains("mist") || c.contains("haze");
        boolean overcast = c.contains("overcast");
        boolean cloudy = c.contains("cloud") || overcast;
        boolean clear = c.contains("clear") || c.contains("sunny");
        int idx;
        if (thunder) idx = 6;
        else if (snow) idx = 4;
        else if (sleet) idx = 7;
        else if (rain) idx = 3;
        else if (fog) idx = 5;
        else if (mist) idx = 13;
        else if (overcast) idx = 2;
        else if (cloudy && night) idx = 11;
        else if (cloudy) idx = 1;
        else if (clear && night) idx = 12;
        else if (clear) idx = 0;
        else if (c.contains("hot")) idx = 8;
        else if (c.contains("cold")) idx = 9;
        else if (c.contains("wind")) idx = 10;
        else if (c.contains("partly")) idx = 14;
        else idx = clear ? (night ? 12 : 0) : (night ? 11 : 1);
        int[] codes = {1, 2, 3, 4, 5, 6, 9, 14, 19, 20, 21, 22, 23, 25, 26};
        return codes[idx];
    }

    /** Push the currently playing media session to the watch. */
    static void pushMusicNow(Context ctx) {
        WatchService s = instance;
        if (s == null) return;
        s.worker.execute(() -> {
            try {
                Watch w = s.watch;
                Ble b = s.ble;
                if (w == null || b == null || !b.isConnected()) {
                    log("Music: not connected");
                    return;
                }
                MediaSessionManager msm =
                        (MediaSessionManager) ctx.getSystemService(Context.MEDIA_SESSION_SERVICE);
                ComponentName cn = new ComponentName(ctx, NotificationBridge.class);
                List<MediaController> sessions = msm.getActiveSessions(cn);
                if (sessions.isEmpty()) {
                    log("Music: no active media session");
                    return;
                }
                MediaController mc = sessions.get(0);
                PlaybackState ps = mc.getPlaybackState();
                int state = 0;
                if (ps != null) {
                    if (ps.getState() == PlaybackState.STATE_PLAYING) state = 2;
                    else if (ps.getState() == PlaybackState.STATE_PAUSED
                            || ps.getState() == PlaybackState.STATE_BUFFERING) state = 1;
                }
                String title = "", artist = "";
                if (mc.getMetadata() != null && mc.getMetadata().getDescription() != null) {
                    title = str(mc.getMetadata().getDescription().getTitle());
                    artist = str(mc.getMetadata().getDescription().getSubtitle());
                }
                AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
                int vol = am.getStreamVolume(AudioManager.STREAM_MUSIC);
                int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                w.sendMusicState(state, vol, max, title, artist);
            } catch (Throwable t) {
                log("Music push failed: " + t);
            }
        });
    }

    private static String str(CharSequence cs) { return cs == null ? "" : cs.toString(); }

    /** Ring the phone from the watch (companion control). */
    static void findWatchNow() {
        WatchService s = instance;
        if (s == null) return;
        s.worker.execute(() -> {
            try {
                Watch w = s.watch;
                if (w != null && s.ble != null && s.ble.isConnected()) w.sendFindWatch();
            } catch (Throwable t) { log("Find watch failed: " + t); }
        });
    }

    /** Enable/disable the watch's do-not-disturb mode. */
    static void setDnd(boolean on) {
        WatchService s = instance;
        if (s == null) return;
        s.worker.execute(() -> {
            try {
                Watch w = s.watch;
                if (w != null && s.ble != null && s.ble.isConnected()) w.sendDoNotDisturb(on);
            } catch (Throwable t) { log("DND failed: " + t); }
        });
    }

    /** Handles watch→phone events as they arrive, without disturbing sync consumers. */
    private void onWatchFrame(Watch w, Proto.Frame f) {
        try {
            if (f.cmd == Proto.BATTERY) {
                w.noteBattery(f);
                return;
            }
            if (f.cmd == Proto.DIAL_LIST) {
                w.noteDialList(w.payloadOf(f));
                return;
            }
            if (f.cmd == Proto.FIND_PHONE) {
                byte[] p = w.payloadOf(f);
                ring(p.length > 0 && p[0] == 1);
                return;
            }
            if (f.cmd == Proto.MUSIC_BUTTON) {
                byte[] p = w.payloadOf(f);
                if (p.length >= 2) musicButton((p[0] & 0xff) | ((p[1] & 0xff) << 8));
                return;
            }
            if (isKnownPassive(f.cmd)) return;
            if (loggedUnknown.add(f.cmd)) {
                byte[] p = w.payloadOf(f);
                log("Watch frame " + Proto.name(f.cmd) + " "
                        + Proto.hex(Proto.slice(p, 0, Math.min(96, p.length))));
            }
        } catch (Throwable t) {
            log("Watch event failed: " + t);
        }
    }

    /** Frames already understood or handled by sync consumers. */
    private static boolean isKnownPassive(int cmd) {
        return cmd == Proto.ACTIVITY_DATA
                || cmd == Proto.DIAL_LIST
                || cmd == Proto.AUTH_PAIR_REP
                || cmd == Proto.AUTH_MAC
                || cmd == Proto.AUTH_NONCE_REP
                || cmd == Proto.AUTH_CONFIRM_REP
                || cmd == Proto.HEART_RATE_MANUAL_AUTO
                || cmd == Proto.HEART_RATE_WORKOUT
                || cmd == Proto.HEART_RATE_RESTING
                || cmd == Proto.SPO2
                || cmd == Proto.STRESS
                || cmd == Proto.SLEEP_DATA
                || cmd == Proto.WORKOUT_SUMMARY
                || cmd == Proto.WORKOUT_GPS
                || cmd == Proto.ACTIVITY_FETCH_ACK_1
                || cmd == Proto.ACTIVITY_FETCH_ACK_2
                || cmd == Proto.MUSIC_BUTTON
                || cmd == Proto.FIND_PHONE;
    }

    private MediaController activeController() {
        MediaSessionManager msm =
                (MediaSessionManager) getSystemService(MEDIA_SESSION_SERVICE);
        List<MediaController> list =
                msm.getActiveSessions(new ComponentName(this, NotificationBridge.class));
        return list.isEmpty() ? null : list.get(0);
    }

    /** Watch "find phone": play/stop the default ringtone. */
    private void ring(boolean start) {
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                if (start) {
                    if (ringtone == null) {
                        Uri uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
                        ringtone = RingtoneManager.getRingtone(this, uri);
                    }
                    if (ringtone != null && !ringtone.isPlaying()) {
                        ringtone.play();
                        log("Find phone: ringing");
                    }
                } else if (ringtone != null && ringtone.isPlaying()) {
                    ringtone.stop();
                    log("Find phone: stopped");
                }
            } catch (Throwable t) {
                log("Find phone failed: " + t);
            }
        });
    }

    /** Watch media buttons: pause/play/next/previous and volume. */
    private void musicButton(int code) {
        try {
            MediaController mc = activeController();
            if (mc == null) {
                log("Music button 0x" + Integer.toHexString(code) + ": no active session");
                return;
            }
            switch (code) {
                case 0x0003:
                    adjustVolume(-1);
                    break;
                case 0x0103:
                    adjustVolume(1);
                    break;
                case 0x0001:
                    mc.getTransportControls().pause();
                    break;
                case 0x0101:
                    mc.getTransportControls().play();
                    break;
                case 0x0102:
                    mc.getTransportControls().skipToNext();
                    break;
                case 0x0002:
                    mc.getTransportControls().skipToPrevious();
                    break;
                default:
                    log(String.format("Unknown music button 0x%04x", code));
                    return;
            }
            log(String.format("Music button 0x%04x handled", code));
        } catch (Throwable t) {
            log("Music button failed: " + t);
        }
    }

    private void adjustVolume(int direction) {
        AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
        am.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                direction > 0 ? AudioManager.ADJUST_RAISE : AudioManager.ADJUST_LOWER,
                AudioManager.FLAG_SHOW_UI);
    }

    private void send(int icon, String sender, String body) {
        worker.execute(() -> {
            try {
                Watch w = watch;
                if (w != null && ble != null && ble.isConnected()) {
                    w.sendNotification(icon, sender, body);
                    log("Forwarded notification to watch: " + sender);
                } else {
                    log("Notification dropped (watch not connected): " + sender);
                }
            } catch (Throwable t) { log("Notification forward failed: " + t); }
        });
    }

    @Override public int onStartCommand(Intent intent, int flags, int id) { return START_STICKY; }
    @Override public IBinder onBind(Intent intent) { return null; }

    /** Drops the current BLE session so the next operation starts from a fresh connect+auth. */
    static void resetSession() {
        WatchService s = instance;
        if (s == null) return;
        Watch w = s.watch; Ble b = s.ble;
        s.watch = null; s.ble = null;
        s.connecting = false;
        if (b != null) try { b.close(); } catch (Throwable ignored) {}
        if (s.worker != null) s.worker.execute(s::connect);
    }

    /** Resets the session and waits for it to become usable again. */
    static boolean resetAndWait(long timeoutMs) {
        WatchService s = instance;
        if (s == null) return false;
        resetSession();
        return awaitConnected(timeoutMs);
    }

    /** Lets the activity adopt an already-running companion session. */
    void setSession(Ble b, Watch w) { ble = b; watch = w; }

    @Override public void onDestroy() {
        stopped = true;
        instance = null;
        weatherHandler.removeCallbacks(weatherSyncRunnable);
        Ble b = ble; if (b != null) b.close();
        worker.shutdownNow();
        super.onDestroy();
    }
}
