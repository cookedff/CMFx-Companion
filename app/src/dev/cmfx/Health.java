package dev.cmfx;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Calendar;

/**
 * Aggregated health / dashboard snapshot built from the watch's sync notifications.
 *
 * The watch pushes activity, heart-rate, SpO2, stress, sleep and workout records as
 * notification commands while the phone runs the activity-fetch sequence. This class
 * parses those payloads and keeps the most recent day's totals for the dashboard.
 */
final class Health {

    /** One watch per phone: keep the dashboard alive across reconnect/activity restarts. */
    private static final Health SHARED = new Health();

    static Health shared() { return SHARED; }

    long steps;
    long distanceM;
    long calories;
    int heartRate = -1;
    int restingHeartRate = -1;
    int spo2 = -1;
    int stress = -1;
    int sleepMinutes = -1;
    int sleepDeepMinutes = -1;
    int sleepLightMinutes = -1;
    int sleepRemMinutes = -1;
    int sleepAwakeMinutes = -1;
    int workouts;
    int battery = -1;
    boolean charging;
    long batteryAt;
    long updatedAt;

    int activitySamples;
    int hrSamples;
    int spo2Samples;
    int stressSamples;
    int sleepSamples;

    /**
     * Totals gathered during the current sync. The watch re-sends the day's records on
     * every pull, so these start at zero and are committed in one go by
     * {@link #commitSync()} — never added on top of the numbers already on screen.
     */
    private long syncDay = Long.MIN_VALUE;
    private long syncSteps, syncDistanceM, syncCalories;
    private boolean haveActivity;
    private final java.util.HashSet<Integer> seenActivity = new java.util.HashSet<>();

    /**
     * Starts a fresh sync. The dashboard keeps its previous values until
     * {@link #commitSync()} replaces them, and per-sync sample counters restart, so a
     * second pull can never inflate the totals the user is looking at.
     */
    void beginSync() {
        sleepMinutes = -1;
        sleepDeepMinutes = -1;
        sleepLightMinutes = -1;
        sleepRemMinutes = -1;
        sleepAwakeMinutes = -1;
        activitySamples = hrSamples = spo2Samples = stressSamples = sleepSamples = 0;
        syncDay = Long.MIN_VALUE;
        syncSteps = syncDistanceM = syncCalories = 0;
        seenActivity.clear();
    }

    /**
     * Publishes the totals from the sync that just finished. Steps, distance and
     * calories are taken straight from the watch's records, so an unchanged day shows
     * the same numbers and a day without records clears them to "no data".
     */
    void commitSync() {
        steps = syncSteps;
        distanceM = syncDistanceM;
        calories = syncCalories;
        haveActivity = true;
    }

    /**
     * Activity calories as kcal. The watch reports gram-calories on some firmware
     * builds and plain kcal on others, so an implausibly large day is scaled down.
     */
    double kcal() {
        return calories >= 20000 ? calories / 1000.0 : calories;
    }

    /**
     * True once a sync has actually committed activity records. The per-sync counter is
     * reset at the start of every pull, so it must not decide whether the dashboard
     * shows numbers: a pull that returns nothing new still leaves the last committed
     * totals visible.
     */
    boolean hasActivity() { return haveActivity; }

    /** Stores a battery notification payload: level, charging flag. */
    void setBattery(byte[] payload) {
        if (payload == null || payload.length == 0) return;
        battery = payload[0] & 0xff;
        charging = payload.length > 1 && payload[1] == 0x01;
        // Battery is pushed on connect; it says nothing about health data freshness, so
        // it must not move the dashboard's LAST UPDATE stamp.
        batteryAt = System.currentTimeMillis();
    }

    /**
     * ACTIVITY_DATA (0x0056/0x0001): 32-byte little-endian samples of
     * {timestamp(s), steps, distance(m), calories, 16 unknown bytes}. The calorie
     * field is gram-calories on some firmware builds and kcal on this one, so
     * {@link #kcal()} normalises it for display.
     */
    void addActivity(byte[] p) {
        if (p.length == 0 || p.length % 32 != 0) return;
        ByteBuffer b = ByteBuffer.wrap(p).order(ByteOrder.LITTLE_ENDIAN);
        while (b.remaining() >= 32) {
            int ts = b.getInt();
            int s = b.getInt();
            int d = b.getInt();
            int c = b.getInt();
            b.position(b.position() + 16);
            if (s < 0 || d < 0 || c < 0) continue;
            // The watch resends records it has already sent; count each timestamp once.
            if (!seenActivity.add(ts)) continue;
            long day = dayStart(ts);
            if (day < syncDay) continue;
            if (day > syncDay) {
                // A newer day starts: only its records belong in this sync's totals.
                syncDay = day;
                syncSteps = syncDistanceM = syncCalories = 0;
            }
            syncSteps += s;
            syncDistanceM += d;
            syncCalories += c;
            activitySamples++;
        }
        updatedAt = System.currentTimeMillis();
    }

    /** HEART_RATE_MANUAL_AUTO / HEART_RATE_WORKOUT: 8-byte {ts(s), bpm} samples. */
    void addHeartRate(byte[] p) {
        if (p.length == 0 || p.length % 8 != 0) return;
        ByteBuffer b = ByteBuffer.wrap(p).order(ByteOrder.LITTLE_ENDIAN);
        int last = -1;
        while (b.remaining() >= 8) {
            b.getInt();
            int hr = b.getInt();
            if (hr > 0 && hr < 300) {
                last = hr;
                hrSamples++;
            }
        }
        if (last > 0) heartRate = last;
        updatedAt = System.currentTimeMillis();
    }

    /** HEART_RATE_RESTING (0x00da/0x0001): 5-byte {ts(s), bpm(u8)} records. */
    void addRestingHeartRate(byte[] p) {
        if (p.length == 0 || p.length % 5 != 0) return;
        for (int off = 0; off + 5 <= p.length; off += 5) {
            int hr = p[off + 4] & 0xff;
            if (hr > 0 && hr < 300) restingHeartRate = hr;
        }
        updatedAt = System.currentTimeMillis();
    }

    /** SPO2 / STRESS: 8-byte {ts(s), value} samples. */
    void addSpo2(byte[] p) {
        if (p.length == 0 || p.length % 8 != 0) return;
        ByteBuffer b = ByteBuffer.wrap(p).order(ByteOrder.LITTLE_ENDIAN);
        int last = -1;
        while (b.remaining() >= 8) {
            b.getInt();
            int v = b.getInt();
            if (v > 0 && v <= 100) {
                last = v;
                spo2Samples++;
            }
        }
        if (last > 0) spo2 = last;
        updatedAt = System.currentTimeMillis();
    }

    void addStress(byte[] p) {
        if (p.length == 0 || p.length % 8 != 0) return;
        ByteBuffer b = ByteBuffer.wrap(p).order(ByteOrder.LITTLE_ENDIAN);
        int last = -1;
        while (b.remaining() >= 8) {
            b.getInt();
            int v = b.getInt();
            if (v >= 0 && v <= 100) {
                last = v;
                stressSamples++;
            }
        }
        if (last >= 0) stress = last;
        updatedAt = System.currentTimeMillis();
    }

    /**
     * SLEEP_DATA: 18-byte header {sessionTs(s), wakeupTs(s), 10 metadata bytes}
     * followed by 8-byte stage samples {ts(s), duration(u16), stage(u16)}.
     */
    void addSleep(byte[] p) {
        if (p.length < 18) return;
        ByteBuffer b = ByteBuffer.wrap(p).order(ByteOrder.LITTLE_ENDIAN);
        int start = b.getInt();
        int wake = b.getInt();
        int deep = b.getShort() & 0xffff;
        int core = b.getShort() & 0xffff;
        int rem = b.getShort() & 0xffff;
        int awake = b.getShort() & 0xffff;
        b.getShort(); // uncertain
        int total = 0;
        while (b.remaining() >= 8) {
            b.getInt();
            int duration = b.getShort() & 0xffff;
            b.getShort();
            total += duration;
            sleepSamples++;
        }
        long span = (long) wake - (long) start;
        int minutes;
        if (span > 0 && span <= 24 * 3600L) {
            minutes = (int) (span / 60L);
        } else {
            minutes = total;
        }
        if (minutes > 0 && minutes <= 24 * 60) sleepMinutes = minutes;
        // Prefer the header totals when present.
        if (deep > 0 || core > 0 || rem > 0 || awake > 0) {
            sleepDeepMinutes = deep / 60;
            sleepLightMinutes = core / 60;
            sleepRemMinutes = rem / 60;
            sleepAwakeMinutes = awake / 60;
        }
        updatedAt = System.currentTimeMillis();
    }

    /** WORKOUT_SUMMARY: records of 32 or 54 bytes; count them for the dashboard. */
    void addWorkouts(byte[] p) {
        if (p.length == 0) return;
        if (p.length % 32 == 0) workouts += p.length / 32;
        else if (p.length % 54 == 0) workouts += p.length / 54;
        else return;
        updatedAt = System.currentTimeMillis();
    }

    /**
     * Start of the local calendar day for a watch timestamp. The watch reports UTC
     * seconds, so the local offset has to be added before bucketing by day.
     */
    private static long dayStart(int utcSeconds) {
        long localMillis = utcSeconds * 1000L
                + java.util.TimeZone.getDefault().getOffset(utcSeconds * 1000L);
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(localMillis);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    /** Multi-line dashboard summary. */
    String describe() {
        StringBuilder s = new StringBuilder();
        if (hasActivity()) {
            s.append("Steps: ").append(String.format("%,d", steps));
            if (distanceM > 0) s.append(String.format(" · %.1f km", distanceM / 1000.0));
            if (calories > 0) s.append(String.format(" · %,.1f kcal", kcal()));
            s.append('\n');
        }
        if (heartRate > 0) s.append("Heart rate: ").append(heartRate).append(" bpm\n");
        if (spo2 > 0) s.append("SpO2: ").append(spo2).append("%\n");
        if (stress >= 0) s.append("Stress: ").append(stress).append('\n');
        if (sleepMinutes > 0) {
            int h = sleepMinutes / 60, m = sleepMinutes % 60;
            s.append("Sleep: ").append(h).append("h ").append(String.format("%02d", m)).append("m\n");
        }
        if (workouts > 0) s.append("Workouts: ").append(workouts).append('\n');
        if (battery >= 0) {
            s.append("Battery: ").append(battery).append('%');
            if (charging) s.append(" (charging)");
            s.append('\n');
        }
        // Trim the trailing newline for a tidy TextView.
        if (s.length() > 0 && s.charAt(s.length() - 1) == '\n') s.setLength(s.length() - 1);
        return s.length() == 0 ? "no data yet" : s.toString();
    }
}
