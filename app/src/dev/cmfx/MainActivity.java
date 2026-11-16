package dev.cmfx;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.ContentValues;
import android.content.ContentUris;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.content.Intent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.BitmapFactory;
import android.graphics.Insets;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.provider.MediaStore;
import android.net.Uri;
import android.text.SpannableStringBuilder;
import android.text.style.StyleSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;
import android.app.AlertDialog;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.File;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * CMFx — standalone CMF watch-face uploader.
 *
 * Picks a face .bin, connects to the watch over BLE, authenticates with the
 * secret the watch itself hands out, and streams the face over the dial channel.
 */
public class MainActivity extends Activity {

    private static final int REQ_PERMS = 10;
private static final int REQ_CALENDAR = 11;

    private static final String GADGETBRIDGE_WARNING =
            "If using this for Gadget bridge, Before entering the key and attempting to connect, "
            + "you should remove all permissions from the vendor app or uninstall it, since it can "
            + "conflict with Gadgetbridge. Do not unpair the watch from the vendor app, as that will "
            + "invalidate the auth key.";
    private static final int INK = Color.rgb(24, 24, 29);
    private static final int MUTED = Color.rgb(105, 105, 116);
    private static final int CANVAS = Color.rgb(244, 244, 255);
    private static final int CARD = Color.WHITE;
    private static final int ACCENT = Color.rgb(239, 91, 61);
    private static final int DARK_CARD = Color.rgb(35, 35, 42);
    private static final int FMC_BG = Color.rgb(16, 16, 16);
    private static final int FMC_CARD = Color.rgb(31, 31, 31);
    private static final int FMC_MUTED = Color.rgb(145, 145, 145);
    private static final int FMC_TAG = Color.rgb(57, 57, 57);
    private static final int MAX_LOG_CHARS = 60000;

    private static final String DOC_TEXT =
            "Welcome to the CMFx Documentation\n"
            + "\n"
            + "Overview\n"
            + "CMFx is a third-party companion app for the CMF Watch Pro 2, designed to enhance your watch "
            + "experience by providing valuable tools and insights. CMFx offers a range of features to assist "
            + "you in managing watch faces, monitoring your health, and staying connected — all without "
            + "replacing Nothing X.\n"
            + "\n"
            + "Features\n"
            + "\n"
            + "Faces\n"
            + "- Browse the FMC watch-face catalog and download faces.\n"
            + "- Install downloaded faces to the watch, or pick a slot to replace.\n"
            + "- Browse and manage your downloaded faces (with thumbnails).\n"
            + "\n"
            + "Health\n"
            + "- Run a Health Preview to sync activity, heart rate, SpO2, stress and sleep.\n"
            + "- View a health overview card with steps, distance, calories, heart rate, sleep, SpO2.\n"
            + "- Sleep summary card with deep/light/REM/awake breakdown and quality score.\n"
            + "\n"
            + "Weather\n"
            + "- Weather card with current conditions, hourly and 7-day forecast (Open-Meteo).\n"
            + "- Push weather to the watch; auto-sync every hour.\n"
            + "\n"
            + "Companion controls\n"
            + "- Find watch, do-not-disturb, send music, notifications forwarding.\n"
            + "\n"
            + "Debug\n"
            + "- Check auth key (Gadgetbridge), probe installed dial ids, view logs.\n"
            + "\n"
            + "Overlays\n"
            + "CMFx adds an in-app guide (this page) and a status dashboard you can reach from the Debug tab.\n"
            + "\n"
            + "Language compatibility\n"
            + "CMFx uses English for all UI and log output.\n"
            + "\n"
            + "Getting Started\n"
            + "To get started with CMFx, follow the steps in the Installing section below.\n"
            + "\n"
            + "Installing\n"
            + "- CMFx installs CMF Watch Pro 2 watch-face .bin files over Bluetooth LE. Nothing X stays installed and untouched.\n"
            + "\n"
            + "Prerequisites\n"
            + "- Android 8.0 or later, Bluetooth LE, self-signed dev.cmfx APK (~65 KB), allow install unknown apps.\n"
            + "\n"
            + "Step 1: Get the CMFx APK\n"
            + "- Download the latest build from the releases archive, or build with app/build.ps1.\n"
            + "\n"
            + "Step 2: Install CMFx\n"
            + "- Install via adb or file manager; allow install unknown apps.\n"
            + "\n"
            + "Step 3: Launch CMFx\n"
            + "- Launch from the launcher and grant Bluetooth permissions when asked.\n"
            + "\n"
            + "Step 4: Connect to the watch\n"
            + "- Open CMFx, grant Bluetooth permissions, and connect to the watch. Keep Nothing X disconnected while CMFx connects.\n"
            + "\n"
            + "Next steps: features\n"
            + "- Faces: browse FMC watch faces, download, and install to the watch.\n"
            + "- Health Preview: tap Update Health Preview to run an activity sync.\n"
            + "- Companion controls: find watch, do-not-disturb, music, notifications.\n"
            + "\n"
            + "Congratulations: you have successfully installed CMFx.\n"
            + "\n"
            + "Support and Feedback\n"
            + "For any questions, issues, or feedback, check the Debug tab logs or the session logs in Documents/CMFx/Logs.\n"
            + "\n"
            + "Thank you for choosing CMFx to enhance your CMF Watch Pro 2 experience!\n"
            + "\n"
            + "Troubleshooting\n"
            + "- Install fails or signature mismatch: use the self-signed CMFx APK, not a re-signed Nothing X build.\n"
            + "- BLE will not connect: Bluetooth on, watch in range, Nothing X not holding the watch.\n"
            + "- Health Preview fails: check Documents/CMFx/Logs; a DeadObjectException means the BLE session must be re-established.\n";

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    // Faces-tab performance: image pool, thumbnail + catalog caches.
    private final ExecutorService imagePool = Executors.newFixedThreadPool(4);
    private final android.util.LruCache<String, Bitmap> thumbCache =
            new android.util.LruCache<>(8 * 1024 * 1024);
    private ArrayList<Face> cachedFaces;
    private long cachedFacesAt;
    private static final long CATALOG_TTL_MS = 15 * 60 * 1000L;
    // Faces-tab recycling / stale-task protection.
    private volatile int facesGeneration = 0;
    private String selectedDownloadedFace = null;
    private final ArrayList<LinearLayout> fmcRows = new ArrayList<>();
    private final ArrayList<FaceTile> fmcTiles = new ArrayList<>();
    private final AtomicBoolean busy = new AtomicBoolean(false);

    private TextView statusView, fileView, logView;
private ImageView selectedFaceThumb;
private byte[] selectedFacePreviewBytes;
private SharedPreferences weatherPrefs;
private String weatherLocation = "";
private LinearLayout weatherCard;
private TextView weatherLocationView, weatherTempView, weatherCondView, weatherUpdatedView;
private ImageView weatherIconView;
private LinearLayout weatherExpanded;
    private TextView healthStepsView, healthHeartView, healthSleepView,
            healthSpo2View, healthDistanceView, healthCaloriesView,
            healthStressView, healthWorkoutsView, healthUpdatedView, batteryTopView;
    private Button connectBtn, uploadBtn;
    private Button browseBtn, healthBtn, notifyBtn;
    private ProgressBar progress;
    private TextView downloadProgressText;
    private ScrollView logScroll;

    private volatile Ble ble;
    private volatile Watch watch;
    private volatile byte[] fileBytes;
    private volatile String fileName;
    private volatile String faceSeed;
    private volatile Runnable afterPerms;
    private volatile Runnable afterConnect;
    private Uri sessionLogUri;
    private Typeface geistRegular, geistBold, notoSerif, ndot;
    private final String sessionLogName = "session-"
            + new java.text.SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
            .format(new Date()) + ".log";

    private PowerManager.WakeLock wakeLock;
    private boolean dndOn;

    private EditText replaceIdView;
    private String lastWeatherLocation = "";
    private int lastWeatherTemp = 22, lastWeatherMin = 16, lastWeatherMax = 26,
            lastWeatherHumidity = 50, lastWeatherCondition = 1;
    private double lastWeatherLat = 0, lastWeatherLon = 0;

    private static final int[] CONDITION_CODES =
            {1, 2, 3, 4, 5, 6, 9, 14, 19, 20, 21, 22, 23, 25, 26};
    private static final String[] CONDITION_NAMES = {
            "Sunny", "Cloudy", "Overcast", "Showers", "Snow", "Fog", "Thunder",
            "Sleet", "Hot", "Cold", "Windy", "Cloudy night", "Clear night",
            "Haze", "Partly cloudy"};
    private static final int[] REPEAT_VALUES =
            {0, 0x7f, 0x1f, 0x60, 1, 2, 4, 8, 16, 32, 64};
    private static final String[] REPEAT_NAMES = {
            "Once", "Every day", "Weekdays", "Weekend",
            "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};

    private Button compactButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        styleButton(b, false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(dp(2), dp(4), dp(2), 0);
        b.setLayoutParams(lp);
        return b;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Watch.init(getApplicationContext());
        getWindow().setStatusBarColor(CANVAS);
        getWindow().setNavigationBarColor(CANVAS);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        loadFonts();
        weatherPrefs = getSharedPreferences("cmfx_weather", Context.MODE_PRIVATE);
        weatherLocation = weatherPrefs.getString("location", "");
        setContentView(buildUi());
        ensureCmfXStorage();
        logLine("CMFx. Keep this screen open during an upload.");
        logLine("Make sure Nothing X is not connected to the watch.");
        status("Not connected");
        String cached = Health.shared().describe();
        if (!"no data yet".equals(cached)) renderHealthDashboard(Health.shared(), "Last preview");
    }

    @Override
    protected void onResume() {
        super.onResume();
        // The activity may have been recreated while WatchService kept the session alive.
        refreshHeaderFromWatch();
        ui.postDelayed(this::refreshHeaderFromWatch, 2500);
    }

    /** Mirrors the companion session in the header once it is up. */
    private void refreshHeaderFromWatch() {
        if (isFinishing() || !WatchService.sessionConnected()) return;
        Watch w = activeWatch();
        Ble b = ble;
        if (w == null || b == null) return;
        status("Connected to " + b.name());
        uploadBtn.setEnabled(fileBytes != null);
        int battery = Health.shared().battery;
        if (battery >= 0) {
            batteryTopView.setText("BAT " + battery + "%");
        }
    }

    private void loadFonts() {
        try {
            geistRegular = getResources().getFont(getResources().getIdentifier(
                    "geist_regular", "font", getPackageName()));
            geistBold = getResources().getFont(getResources().getIdentifier(
                    "geist_bold", "font", getPackageName()));
            notoSerif = getResources().getFont(getResources().getIdentifier(
                    "noto_serif", "font", getPackageName()));
            ndot = getResources().getFont(getResources().getIdentifier(
                    "ndot_regular", "font", getPackageName()));
        } catch (Throwable ignored) {
            geistRegular = Typeface.create("sans-serif", Typeface.NORMAL);
            geistBold = Typeface.create("sans-serif", Typeface.BOLD);
            notoSerif = Typeface.create("serif", Typeface.NORMAL);
        }
    }

    private Typeface geist() { return geistRegular == null ? Typeface.DEFAULT : geistRegular; }
    private Typeface geistBold() { return geistBold == null ? Typeface.DEFAULT_BOLD : geistBold; }
    private Typeface notoSerif() { return notoSerif == null ? Typeface.SERIF : notoSerif; }
    private Typeface ndot() { return ndot == null ? Typeface.DEFAULT : ndot; }

    @Override
    protected void onDestroy() {
        // The BLE session belongs to WatchService; only drop our references.
        Watch w = watch;
        if (w != null) w.setProgressListener(null);
        ble = null;
        watch = null;
        worker.shutdownNow();
        super.onDestroy();
    }

    // ---- UI -----------------------------------------------------------

    private View buildUi() {
        int pad = dp(18);

        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(CANVAS);
        shell.setPadding(pad, dp(34), pad, 0);

        LinearLayout brandRow = new LinearLayout(this);
        brandRow.setGravity(Gravity.CENTER_VERTICAL);
        ImageView brandIcon = new ImageView(this);
        int iconId = getResources().getIdentifier("cmf_icon", "drawable", getPackageName());
        if (iconId != 0) brandIcon.setImageResource(iconId);
        brandIcon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(38), dp(38));
        iconParams.setMargins(0, 0, dp(8), 0);
        brandRow.addView(brandIcon, iconParams);
        TextView brand = new TextView(this);
        brand.setText("CMFx");
        brand.setTextColor(INK);
        brand.setTypeface(ndot());
        brand.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
        brand.setLetterSpacing(.08f);
        brand.setPadding(dp(4), dp(4), dp(4), dp(10));
        brandRow.addView(brand, new LinearLayout.LayoutParams(0, -2, 1f));
        batteryTopView = new TextView(this);
        batteryTopView.setText("BAT —");
        batteryTopView.setTextColor(INK);
        batteryTopView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        batteryTopView.setTypeface(geistBold());
        batteryTopView.setGravity(Gravity.CENTER);
        batteryTopView.setPadding(dp(12), dp(8), dp(12), dp(8));
        batteryTopView.setBackground(round(Color.rgb(255, 244, 207), 14));
        brandRow.addView(batteryTopView);
        shell.addView(brandRow);

        statusView = new TextView(this);
        statusView.setTextColor(INK);
        statusView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        statusView.setTypeface(geistBold());
        statusView.setPadding(dp(14), dp(12), dp(14), dp(12));
        statusView.setBackground(round(CARD, 18));
        shell.addView(statusView);

        FrameLayout pages = new FrameLayout(this);
        LinearLayout faces = page();
        LinearLayout quick = page();
        LinearLayout debug = page();
        pages.addView(pageScroll(faces), new FrameLayout.LayoutParams(-1, -1));
        pages.addView(pageScroll(quick), new FrameLayout.LayoutParams(-1, -1));
        pages.addView(pageScroll(debug), new FrameLayout.LayoutParams(-1, -1));
        LinearLayout docs = page();
        ScrollView docsScroll = new ScrollView(this);
        TextView docsText = new TextView(this);
        docsText.setText(boldDocHeadings(DOC_TEXT));
        docsText.setTextColor(INK);
        docsText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        docsScroll.addView(docsText);
        docs.addView(docsScroll);
        pages.addView(pageScroll(docs), new FrameLayout.LayoutParams(-1, -1));
        pages.getChildAt(0).setVisibility(View.GONE);
        pages.getChildAt(1).setVisibility(View.VISIBLE);
        pages.getChildAt(2).setVisibility(View.GONE);
        pages.getChildAt(3).setVisibility(View.GONE);
        shell.addView(pages, new LinearLayout.LayoutParams(-1, 0, 1f));

        // ---- Faces tab -------------------------------------------------
        faces.addView(section("FACES"));
        browseBtn = button("Browse FMC watch faces");
        browseBtn.setOnClickListener(v -> browseFmc());
        browseBtn.setOnLongClickListener(v -> {
            cachedFaces = null;
            cachedFacesAt = 0;
            thumbCache.evictAll();
            status("FMC catalog cache cleared");
            return true;
        });
        faces.addView(browseBtn);
        Button browseDownloadedBtn = button("Browse downloaded faces");
        browseDownloadedBtn.setOnClickListener(v -> browseDownloadedFaces());
        faces.addView(browseDownloadedBtn);
        faces.addView(section("SELECTED FACE"));
        fileView = new TextView(this);
        fileView.setText("No file selected");
        fileView.setTextColor(MUTED);
        fileView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        fileView.setPadding(dp(4), dp(6), dp(4), dp(2));
        faces.addView(fileView);
        selectedFaceThumb = new ImageView(this);
        selectedFaceThumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
        selectedFaceThumb.setVisibility(View.GONE);
        LinearLayout.LayoutParams thumbLp = new LinearLayout.LayoutParams(dp(96), dp(96));
        thumbLp.setMargins(dp(4), dp(6), dp(4), dp(6));
        selectedFaceThumb.setLayoutParams(thumbLp);
        selectedFaceThumb.setBackground(round(FMC_CARD, 12));
        faces.addView(selectedFaceThumb);

        LinearLayout replaceRow = new LinearLayout(this);
        replaceRow.setOrientation(LinearLayout.HORIZONTAL);
        replaceRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView replaceLabel = new TextView(this);
        replaceLabel.setText("Replace dial id");
        replaceLabel.setTextColor(MUTED);
        replaceLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        replaceRow.addView(replaceLabel, new LinearLayout.LayoutParams(0, -2, 1f));
        replaceIdView = new EditText(this);
        replaceIdView.setHint("0 = append");
        replaceIdView.setText("0");
        replaceIdView.setSingleLine(true);
        replaceIdView.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        replaceIdView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        replaceIdView.setTextColor(INK);
        replaceIdView.setHintTextColor(MUTED);
        replaceRow.addView(replaceIdView, new LinearLayout.LayoutParams(dp(130), -2));
        faces.addView(replaceRow);
        Button probeBtn = button("Probe installed dial ids");
        probeBtn.setOnClickListener(v -> onDialSlotsClicked());
        faces.addView(probeBtn);
        uploadBtn = button("Upload to watch");
        uploadBtn.setBackground(round(DARK_CARD, 16));
        uploadBtn.setEnabled(false);
        uploadBtn.setOnClickListener(v -> onUploadClicked());
        faces.addView(uploadBtn);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setProgress(0);
        downloadProgressText = new TextView(this);
        downloadProgressText.setText("Ready");
        downloadProgressText.setTextColor(MUTED);
        downloadProgressText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        downloadProgressText.setTypeface(geist());
        downloadProgressText.setPadding(dp(4), dp(8), dp(4), dp(3));
        faces.addView(downloadProgressText);
        faces.addView(progress, new LinearLayout.LayoutParams(-1, dp(6)));

        // ---- Quick controls tab --------------------------------------
        quick.addView(section("CONNECTION"));
        connectBtn = button("Connect to watch");
        connectBtn.setBackground(round(ACCENT, 16));
        connectBtn.setOnClickListener(v -> onConnectClicked());
        quick.addView(connectBtn);
        notifyBtn = button("Enable calls & message notifications");
        notifyBtn.setOnClickListener(v -> {
            try { startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)); }
            catch (Throwable t) { fail("Notification settings unavailable", t); }
        });
        quick.addView(notifyBtn);
        quick.addView(section("QUICK CONTROLS"));
        LinearLayout tools = new LinearLayout(this);
        tools.setOrientation(LinearLayout.HORIZONTAL);
        Button findBtn = compactButton("Find watch");
        findBtn.setOnClickListener(v -> {
            status("Find watch…");
            WatchService.findWatchNow();
        });
        Button dndBtn = compactButton("DND");
        dndBtn.setOnClickListener(v -> {
            dndOn = !dndOn;
            WatchService.setDnd(dndOn);
            status("Do not disturb " + (dndOn ? "on" : "off"));
        });
        Button musicBtn = compactButton("Send music");
        musicBtn.setOnClickListener(v -> {
            status("Sending music info…");
            WatchService.pushMusicNow(this);
        });
        tools.addView(findBtn);
        tools.addView(dndBtn);
        tools.addView(musicBtn);
        quick.addView(tools);
        quick.addView(section("WATCH DATA"));
        quick.addView(createHealthDashboard());
        healthBtn = button("Update Health Preview");
        healthBtn.setOnClickListener(v -> onHealthClicked());
        quick.addView(healthBtn);
        quick.addView(section("WEATHER"));
        quick.addView(createWeatherCard());
        quick.addView(section("SLEEP"));
        quick.addView(createSleepCard());

        // ---- Debug tab ------------------------------------------------
        debug.addView(section("DEBUG MODES"));
        Button authKeyBtn = button("Check auth key (Gadgetbridge)");
        authKeyBtn.setOnClickListener(v -> onCheckAuthKeyClicked());
        debug.addView(authKeyBtn);
        TextView debugHint = new TextView(this);
        debugHint.setText("Use these tools when sharing the watch with Gadgetbridge or diagnosing a BLE session.");
        debugHint.setTextColor(MUTED);
        debugHint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        debugHint.setPadding(dp(4), dp(12), dp(4), dp(8));
        debug.addView(debugHint);

        logScroll = new ScrollView(this);
        logScroll.setBackgroundColor(Color.rgb(8, 8, 10));
        logScroll.setPadding(dp(8), dp(8), dp(8), dp(8));

        logView = new TextView(this);
        logView.setTextColor(Color.rgb(205, 205, 210));
        logView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        logView.setTypeface(Typeface.MONOSPACE);
        logView.setTextIsSelectable(true);
        logScroll.addView(logView);

        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setPadding(0, dp(8), 0, dp(10));
        nav.setBackground(round(CARD, 18));
        Button quickNav = navButton("QuickTools", true);
        Button facesNav = navButton("Faces", false);
        Button debugNav = navButton("Debug", false);
        nav.addView(quickNav); nav.addView(facesNav); nav.addView(debugNav);
        facesNav.setOnClickListener(v -> selectPage(pages, 0, facesNav, quickNav, debugNav));
        quickNav.setOnClickListener(v -> selectPage(pages, 1, facesNav, quickNav, debugNav));
        debugNav.setOnClickListener(v -> selectPage(pages, 2, facesNav, quickNav, debugNav));
        debug.addView(logScroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        debug.addView(section("CMFx DOCS"));
        Button viewDocsBtn = button("View");
        viewDocsBtn.setOnClickListener(v -> showDocPage(pages, facesNav, quickNav, debugNav));
        debug.addView(viewDocsBtn);
        shell.addView(nav, new LinearLayout.LayoutParams(-1, dp(72)));

        shell.setOnApplyWindowInsetsListener((v, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                v.setPadding(pad, dp(28) + bars.top, pad, bars.bottom);
            }
            return insets;
        });
        return shell;
    }

    private LinearLayout page() {
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setPadding(0, dp(4), 0, dp(12));
        return p;
    }

    private ScrollView pageScroll(LinearLayout page) {
        ScrollView s = new ScrollView(this);
        s.setFillViewport(true);
        s.addView(page, new ScrollView.LayoutParams(-1, -2));
        return s;
    }

    private LinearLayout createWeatherCard() {
        weatherCard = new LinearLayout(this);
        weatherCard.setOrientation(LinearLayout.VERTICAL);
        weatherCard.setPadding(dp(16), dp(16), dp(16), dp(16));
        weatherCard.setBackground(round(CARD, 20));

        LinearLayout heading = new LinearLayout(this);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.setOrientation(LinearLayout.HORIZONTAL);

        LinearLayout leftCol = new LinearLayout(this);
        leftCol.setOrientation(LinearLayout.VERTICAL);
        leftCol.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));

        weatherLocationView = new TextView(this);
        weatherLocationView.setText(weatherLocation.isEmpty() ? "Weather" : weatherLocation);
        weatherLocationView.setTextColor(INK);
        weatherLocationView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        weatherLocationView.setTypeface(geistBold());
        leftCol.addView(weatherLocationView);

        weatherUpdatedView = new TextView(this);
        weatherUpdatedView.setText("Tap to set location");
        weatherUpdatedView.setTextColor(MUTED);
        weatherUpdatedView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        weatherUpdatedView.setTypeface(geist());
        leftCol.addView(weatherUpdatedView);

        heading.addView(leftCol);

        ImageButton editLocBtn = new ImageButton(this);
        editLocBtn.setImageResource(android.R.drawable.ic_menu_edit);
        editLocBtn.setContentDescription("Change location");
        editLocBtn.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        editLocBtn.setBackground(round(FMC_CARD, 10));
        editLocBtn.setStateListAnimator(null);
        int es = dp(32);
        LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(es, es);
        elp.setMargins(dp(4), 0, dp(4), 0);
        editLocBtn.setLayoutParams(elp);
        editLocBtn.setPadding(dp(6), dp(6), dp(6), dp(6));
        editLocBtn.setOnClickListener(v -> showWeatherLocationDialog());
        heading.addView(editLocBtn);

        weatherTempView = new TextView(this);
        weatherTempView.setText("—");
        weatherTempView.setTextColor(INK);
        weatherTempView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
        weatherTempView.setTypeface(geistBold());
        weatherTempView.setGravity(Gravity.RIGHT);
        heading.addView(weatherTempView);

        weatherIconView = new ImageView(this);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(48), dp(48));
        iconLp.setMargins(dp(8), 0, 0, 0);
        weatherIconView.setLayoutParams(iconLp);
        weatherIconView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        heading.addView(weatherIconView);

        weatherCard.addView(heading);

        weatherCondView = new TextView(this);
        weatherCondView.setText("");
        weatherCondView.setTextColor(MUTED);
        weatherCondView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        weatherCondView.setPadding(0, dp(4), 0, dp(4));
        weatherCard.addView(weatherCondView);

        // expanded section (hidden by default)
        weatherExpanded = new LinearLayout(this);
        weatherExpanded.setOrientation(LinearLayout.VERTICAL);
        weatherExpanded.setVisibility(View.GONE);
        weatherCard.addView(weatherExpanded);

        weatherCard.setOnClickListener(v -> {
            if (weatherLocation.isEmpty()) {
                showWeatherLocationDialog();
            } else {
                if (weatherExpanded.getVisibility() == View.VISIBLE) {
                    weatherExpanded.setVisibility(View.GONE);
                } else {
                    weatherExpanded.setVisibility(View.VISIBLE);
                    if (weatherExpanded.getChildCount() == 0) {
                        fetchWeather(weatherLocation);
                    }
                }
            }
        });

        // long-press to change location
        weatherCard.setOnLongClickListener(v -> { showWeatherLocationDialog(); return true; });

        if (!weatherLocation.isEmpty()) {
            fetchWeather(weatherLocation);
        }
        return weatherCard;
    }

    private void showWeatherLocationDialog() {
        final EditText input = new EditText(this);
        input.setText(weatherLocation);
        input.setHint("City name (e.g. London)");
        input.setSingleLine(true);
        input.setTextColor(INK);
        input.setHintTextColor(MUTED);
        new AlertDialog.Builder(this)
                .setTitle("Weather location")
                .setView(input)
                .setPositiveButton("Save", (d, w) -> {
                    String loc = input.getText().toString().trim();
                    if (loc.isEmpty()) return;
                    weatherLocation = loc;
                    if (weatherPrefs != null) weatherPrefs.edit().putString("location", loc).apply();
                    if (weatherLocationView != null) weatherLocationView.setText(loc);
                    if (weatherUpdatedView != null) weatherUpdatedView.setText("Loading…");
                    fetchWeather(loc);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private static final class WeatherData {
        String location, tempC, condition, iconUrl, humidity, windKmph, pressure, visibility, updated;
        final java.util.ArrayList<String[]> hourly = new java.util.ArrayList<>();
        final java.util.ArrayList<String[]> daily = new java.util.ArrayList<>();
        final java.util.ArrayList<String[]> weekly = new java.util.ArrayList<>();
    }

    private void fetchWeather(final String location) {
        worker.execute(() -> {
            try {
                String url = "https://wttr.in/" + java.net.URLEncoder.encode(location, "UTF-8") + "?format=j1";
                String text = httpText(url);
                org.json.JSONObject root = new org.json.JSONObject(text);
                WeatherData wd = new WeatherData();
                // location
                org.json.JSONArray areas = root.optJSONArray("nearest_area");
                if (areas != null && areas.length() > 0) {
                    org.json.JSONObject a = areas.getJSONObject(0);
                    org.json.JSONArray an = a.optJSONArray("areaName");
                    wd.location = (an != null && an.length() > 0) ? an.getJSONObject(0).optString("value", location) : location;
                    org.json.JSONArray cn = a.optJSONArray("country");
                    if (cn != null && cn.length() > 0) wd.location = wd.location + ", " + cn.getJSONObject(0).optString("value", "");
                } else {
                    wd.location = location;
                }
                // current
                org.json.JSONArray cur = root.optJSONArray("current_condition");
                if (cur != null && cur.length() > 0) {
                    org.json.JSONObject c = cur.getJSONObject(0);
                    wd.tempC = c.optString("temp_C", "—");
                    org.json.JSONArray desc = c.optJSONArray("weatherDesc");
                    wd.condition = (desc != null && desc.length() > 0) ? desc.getJSONObject(0).optString("value", "") : "";
                    org.json.JSONArray icons = c.optJSONArray("weatherIconUrl");
                    wd.iconUrl = (icons != null && icons.length() > 0) ? icons.getJSONObject(0).optString("value", "") : "";
                    wd.humidity = c.optString("humidity", "—");
                    wd.windKmph = c.optString("windspeedKmph", "—");
                    wd.pressure = c.optString("pressure", "—");
                    wd.visibility = c.optString("visibility", "—");
                }
                // daily + hourly
                org.json.JSONArray weather = root.optJSONArray("weather");
                if (weather != null) {
                    for (int i = 0; i < weather.length(); i++) {
                        org.json.JSONObject w = weather.getJSONObject(i);
                        String date = w.optString("date", "");
                        String maxC = w.optString("maxtempC", "—");
                        String minC = w.optString("mintempC", "—");
                        org.json.JSONArray h = w.optJSONArray("hourly");
                        String dCond = "", dIcon = "";
                        if (h != null && h.length() > 0) {
                            org.json.JSONObject hh = h.getJSONObject(h.length() / 2);
                            org.json.JSONArray dd = hh.optJSONArray("weatherDesc");
                            dCond = (dd != null && dd.length() > 0) ? dd.getJSONObject(0).optString("value", "") : "";
                            org.json.JSONArray ii = hh.optJSONArray("weatherIconUrl");
                            dIcon = (ii != null && ii.length() > 0) ? ii.getJSONObject(0).optString("value", "") : "";
                        }
                        wd.daily.add(new String[]{date, maxC, minC, dCond, dIcon});
                        if (h != null) {
                            for (int j = 0; j < h.length(); j++) {
                                org.json.JSONObject hh = h.getJSONObject(j);
                                String time = hh.optString("time", "0");
                                // format time "0" -> "00:00"
                                int t = 0;
                                try { t = Integer.parseInt(time); } catch (Throwable ignored) {}
                                String ts = String.format(java.util.Locale.US, "%02d:%02d", t / 100, t % 100);
                                String hCond = "";
                                org.json.JSONArray dd = hh.optJSONArray("weatherDesc");
                                if (dd != null && dd.length() > 0) hCond = dd.getJSONObject(0).optString("value", "");
                                org.json.JSONArray ii = hh.optJSONArray("weatherIconUrl");
                                String hIcon = (ii != null && ii.length() > 0) ? ii.getJSONObject(0).optString("value", "") : "";
                                wd.hourly.add(new String[]{ts, hh.optString("tempC", "—"), hCond, hIcon});
                            }
                        }
                    }
                }
                // lat/lon from nearest_area
                double la = lastWeatherLat, lo = lastWeatherLon;
                if (areas != null && areas.length() > 0) {
                    org.json.JSONObject a = areas.getJSONObject(0);
                    try { la = Double.parseDouble(a.optString("latitude", String.valueOf(la))); } catch (Throwable ignored) {}
                    try { lo = Double.parseDouble(a.optString("longitude", String.valueOf(lo))); } catch (Throwable ignored) {}
                }
                // Weekly forecast from Open-Meteo (7 days)
                try {
                    String omUrl = "https://api.open-meteo.com/v1/forecast?latitude=" + la
                            + "&longitude=" + lo
                            + "&daily=weathercode,temperature_2m_max,temperature_2m_min"
                            + "&timezone=auto&forecast_days=7";
                    String omText = httpText(omUrl);
                    org.json.JSONObject om = new org.json.JSONObject(omText);
                    org.json.JSONObject daily = om.optJSONObject("daily");
                    if (daily != null) {
                        org.json.JSONArray dates = daily.optJSONArray("time");
                        org.json.JSONArray maxs = daily.optJSONArray("temperature_2m_max");
                        org.json.JSONArray mins = daily.optJSONArray("temperature_2m_min");
                        org.json.JSONArray codes = daily.optJSONArray("weathercode");
                        if (dates != null) {
                            for (int i = 0; i < dates.length(); i++) {
                                String d = dates.optString(i, "");
                                String mx = (maxs != null && i < maxs.length()) ? String.valueOf(Math.round(maxs.optDouble(i, 0))) : "—";
                                String mn = (mins != null && i < mins.length()) ? String.valueOf(Math.round(mins.optDouble(i, 0))) : "—";
                                int code = (codes != null && i < codes.length()) ? codes.optInt(i, 0) : 0;
                                String cond = wmoCondition(code);
                                String icon = weatherIconName(cond, false);
                                wd.weekly.add(new String[]{d, mx, mn, cond, icon});
                            }
                        }
                    }
                } catch (Throwable ignored) {}
                wd.updated = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.US).format(new java.util.Date());
                // push to watch (7-day weekly)
                final int baseHum = parseInt(wd.humidity, lastWeatherHumidity);
                final int baseTemp = parseInt(wd.tempC, lastWeatherTemp);
                final int[] wCode = new int[7];
                final int[] wTemp = new int[7];
                final int[] wMn = new int[7];
                final int[] wMx = new int[7];
                final int[] wHum = new int[7];
                if (!wd.weekly.isEmpty()) {
                    for (int i = 0; i < 7; i++) {
                        String[] d = (i < wd.weekly.size()) ? wd.weekly.get(i) : wd.weekly.get(wd.weekly.size() - 1);
                        int mx = baseTemp, mn = baseTemp;
                        try { mx = Integer.parseInt(d[1]); } catch (Throwable ignored) {}
                        try { mn = Integer.parseInt(d[2]); } catch (Throwable ignored) {}
                        wCode[i] = cmfConditionCode(d[3], false);
                        wTemp[i] = (mx + mn) / 2;
                        wMn[i] = mn;
                        wMx[i] = mx;
                        wHum[i] = baseHum;
                    }
                } else {
                    int cCode = cmfConditionCode(wd.condition, isNightTime());
                    int cTemp = baseTemp;
                    int cMn = wd.daily.isEmpty() ? cTemp : parseInt(wd.daily.get(0)[2], cTemp);
                    int cMx = wd.daily.isEmpty() ? cTemp : parseInt(wd.daily.get(0)[1], cTemp);
                    for (int i = 0; i < 7; i++) {
                        wCode[i] = cCode; wTemp[i] = cTemp; wMn[i] = cMn; wMx[i] = cMx; wHum[i] = baseHum;
                    }
                }
                lastWeatherTemp = wTemp[0]; lastWeatherMin = wMn[0]; lastWeatherMax = wMx[0];
                lastWeatherHumidity = wHum[0]; lastWeatherCondition = wCode[0];
                lastWeatherLocation = wd.location; lastWeatherLat = la; lastWeatherLon = lo;
                final String cLocF = wd.location; final double cLaF = la, cLoF = lo;
                Watch w = watch; Ble b = ble;
                if (w != null && b != null && b.isConnected()) {
                    worker.execute(() -> {
                        try { w.sendWeather(wCode, wTemp, wMn, wMx, wHum, cLocF, cLaF, cLoF); }
                        catch (Throwable ignored) {}
                    });
                }
                ui.post(() -> renderWeather(wd));
            } catch (Throwable t) {
                ui.post(() -> {
                    if (weatherUpdatedView != null) weatherUpdatedView.setText("Failed: " + msg(t));
                });
            }
        });
    }

    private boolean isNightTime() {
        int h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        return h < 6 || h >= 18;
    }

    private String weatherIconName(String condition, boolean night) {
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
        boolean tornado = c.contains("tornado");
        boolean wind = c.contains("wind");

        String base;
        if (tornado) base = "tornado";
        else if (thunder && snow) base = night ? "night_full_moon_snow_thunder" : "day_snow_thunder";
        else if (thunder && (rain || sleet)) base = night ? "night_full_moon_rain_thunder" : "day_rain_thunder";
        else if (thunder) base = "thunder";
        else if (snow) base = night ? "night_full_moon_snow" : "day_snow";
        else if (sleet) base = night ? "night_full_moon_sleet" : "day_sleet";
        else if (rain) base = night ? "night_full_moon_rain" : "day_rain";
        else if (fog) base = "fog";
        else if (mist) base = "mist";
        else if (overcast) base = "overcast";
        else if (cloudy) base = night ? "night_full_moon_partial_cloud" : "day_partial_cloud";
        else if (clear) base = night ? "night_full_moon_clear" : "day_clear";
        else if (wind) base = "wind";
        else if (c.contains("angry")) base = "angry_clouds";
        else base = night ? "night_full_moon_clear" : "day_clear";

        int id = getResources().getIdentifier(base, "drawable", getPackageName());
        if (id == 0) {
            // fallbacks
            if (night) {
                id = getResources().getIdentifier("night_full_moon_clear", "drawable", getPackageName());
            }
            if (id == 0) id = getResources().getIdentifier("day_clear", "drawable", getPackageName());
            if (id != 0) {
                // return the fallback name so caller's getIdentifier succeeds
                if (night) base = "night_full_moon_clear"; else base = "day_clear";
            }
        }
        return base;
    }

    private int cmfConditionCode(String condition, boolean night) {
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
        if (thunder) idx = 6;          // Thunder
        else if (snow) idx = 4;        // Snow
        else if (sleet) idx = 7;       // Sleet
        else if (rain) idx = 3;        // Showers
        else if (fog) idx = 5;         // Fog
        else if (mist) idx = 13;       // Haze
        else if (overcast) idx = 2;    // Overcast
        else if (cloudy && night) idx = 11; // Cloudy night
        else if (cloudy) idx = 1;      // Cloudy
        else if (clear && night) idx = 12; // Clear night
        else if (clear) idx = 0;       // Sunny
        else if (c.contains("hot")) idx = 8;
        else if (c.contains("cold")) idx = 9;
        else if (c.contains("wind")) idx = 10; // Windy
        else if (c.contains("partly")) idx = 14; // Partly cloudy
        else idx = clear ? (night ? 12 : 0) : (night ? 11 : 1);
        return CONDITION_CODES[idx];
    }

    private String wmoCondition(int code) {
        switch (code) {
            case 0: return "Clear";
            case 1: return "Mainly clear";
            case 2: return "Partly cloudy";
            case 3: return "Overcast";
            case 45: case 48: return "Fog";
            case 51: case 53: case 55: return "Drizzle";
            case 56: case 57: return "Freezing drizzle";
            case 61: case 63: case 65: return "Rain";
            case 66: case 67: return "Freezing rain";
            case 71: case 73: case 75: return "Snow";
            case 77: return "Snow grains";
            case 80: case 81: case 82: return "Rain showers";
            case 85: case 86: return "Snow showers";
            case 95: return "Thunderstorm";
            case 96: case 99: return "Thunderstorm with hail";
            default: return "Cloudy";
        }
    }

    private void renderWeather(WeatherData wd) {
        if (weatherLocationView != null) weatherLocationView.setText(wd.location);
        if (weatherTempView != null) weatherTempView.setText(wd.tempC + "°");
        if (weatherCondView != null) weatherCondView.setText(wd.condition);
        if (weatherUpdatedView != null) weatherUpdatedView.setText("Updated " + wd.updated);
        // icon (local from icon pack)
        if (weatherIconView != null) {
            String iconName = weatherIconName(wd.condition, isNightTime());
            int resId = getResources().getIdentifier(iconName, "drawable", getPackageName());
            if (resId == 0) resId = getResources().getIdentifier("day_clear", "drawable", getPackageName());
            if (resId != 0) weatherIconView.setImageResource(resId);
        }
        // expanded
        if (weatherExpanded != null) {
            weatherExpanded.removeAllViews();
            // details row
            LinearLayout details = new LinearLayout(this);
            details.setOrientation(LinearLayout.HORIZONTAL);
            details.setPadding(0, dp(8), 0, dp(4));
            addWeatherDetail(details, "Humidity", wd.humidity + "%");
            addWeatherDetail(details, "Wind", wd.windKmph + " km/h");
            addWeatherDetail(details, "Pressure", wd.pressure + " hPa");
            addWeatherDetail(details, "Visibility", wd.visibility + " km");
            weatherExpanded.addView(details);
            // hourly
            TextView hLabel = new TextView(this);
            hLabel.setText("Hourly");
            hLabel.setTextColor(INK);
            hLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            hLabel.setTypeface(geistBold());
            hLabel.setPadding(0, dp(8), 0, dp(4));
            weatherExpanded.addView(hLabel);
            android.widget.HorizontalScrollView hScroll = new android.widget.HorizontalScrollView(this);
            hScroll.setHorizontalScrollBarEnabled(false);
            LinearLayout hRow = new LinearLayout(this);
            hRow.setOrientation(LinearLayout.HORIZONTAL);
            int count = 0;
            for (String[] h : wd.hourly) {
                if (count++ >= 12) break;
                LinearLayout item = new LinearLayout(this);
                item.setOrientation(LinearLayout.VERTICAL);
                item.setGravity(Gravity.CENTER_HORIZONTAL);
                item.setPadding(dp(8), dp(4), dp(8), dp(4));
                TextView tTime = new TextView(this);
                tTime.setText(h[0]);
                tTime.setTextColor(MUTED);
                tTime.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
                tTime.setGravity(Gravity.CENTER);
                item.addView(tTime);
                ImageView tIcon = new ImageView(this);
                LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(28), dp(28));
                tIcon.setLayoutParams(ilp);
                tIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
                String iName = weatherIconName(h[2], isNightTime());
                int iRes = getResources().getIdentifier(iName, "drawable", getPackageName());
                if (iRes == 0) iRes = getResources().getIdentifier("day_clear", "drawable", getPackageName());
                if (iRes != 0) tIcon.setImageResource(iRes);
                item.addView(tIcon);
                TextView tTemp = new TextView(this);
                tTemp.setText(h[1] + "°");
                tTemp.setTextColor(INK);
                tTemp.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                tTemp.setTypeface(geistBold());
                tTemp.setGravity(Gravity.CENTER);
                item.addView(tTemp);
                hRow.addView(item);
            }
            hScroll.addView(hRow);
            weatherExpanded.addView(hScroll);
            // daily
            TextView dLabel = new TextView(this);
            dLabel.setText("3-Day Forecast");
            dLabel.setTextColor(INK);
            dLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            dLabel.setTypeface(geistBold());
            dLabel.setPadding(0, dp(8), 0, dp(4));
            weatherExpanded.addView(dLabel);
            for (String[] d : wd.daily) {
                LinearLayout dRow = new LinearLayout(this);
                dRow.setOrientation(LinearLayout.HORIZONTAL);
                dRow.setGravity(Gravity.CENTER_VERTICAL);
                dRow.setPadding(dp(4), dp(6), dp(4), dp(6));
                TextView dDay = new TextView(this);
                dDay.setText(d[0]);
                dDay.setTextColor(INK);
                dDay.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                dDay.setTypeface(geist());
                dDay.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
                dRow.addView(dDay);
                ImageView dIcon = new ImageView(this);
                LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dp(24), dp(24));
                dIcon.setLayoutParams(dlp);
                dIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
                String dName = weatherIconName(d[3], false);
                int dRes = getResources().getIdentifier(dName, "drawable", getPackageName());
                if (dRes == 0) dRes = getResources().getIdentifier("day_clear", "drawable", getPackageName());
                if (dRes != 0) dIcon.setImageResource(dRes);
                dRow.addView(dIcon);
                TextView dTemp = new TextView(this);
                dTemp.setText(d[2] + "° / " + d[1] + "°");
                dTemp.setTextColor(MUTED);
                dTemp.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                dTemp.setGravity(Gravity.RIGHT);
                LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(dp(80), -2);
                tlp.setMargins(dp(8), 0, 0, 0);
                dTemp.setLayoutParams(tlp);
                dRow.addView(dTemp);
                weatherExpanded.addView(dRow);
            }
            // Weekly forecast (7 days from Open-Meteo)
            if (!wd.weekly.isEmpty()) {
                TextView wLabel = new TextView(this);
                wLabel.setText("Weekly Forecast");
                wLabel.setTextColor(INK);
                wLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                wLabel.setTypeface(geistBold());
                wLabel.setPadding(0, dp(8), 0, dp(4));
                weatherExpanded.addView(wLabel);
                for (String[] wk : wd.weekly) {
                    LinearLayout wRow = new LinearLayout(this);
                    wRow.setOrientation(LinearLayout.HORIZONTAL);
                    wRow.setGravity(Gravity.CENTER_VERTICAL);
                    wRow.setPadding(dp(4), dp(6), dp(4), dp(6));
                    TextView wDay = new TextView(this);
                    // show short date (MM-DD)
                    String dateStr = wk[0];
                    if (dateStr.length() >= 10) dateStr = dateStr.substring(5);
                    wDay.setText(dateStr);
                    wDay.setTextColor(INK);
                    wDay.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                    wDay.setTypeface(geist());
                    wDay.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
                    wRow.addView(wDay);
                    ImageView wIcon = new ImageView(this);
                    LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(dp(24), dp(24));
                    wIcon.setLayoutParams(wlp);
                    wIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    String wName = weatherIconName(wk[3], false);
                    int wRes = getResources().getIdentifier(wName, "drawable", getPackageName());
                    if (wRes == 0) wRes = getResources().getIdentifier("day_clear", "drawable", getPackageName());
                    if (wRes != 0) wIcon.setImageResource(wRes);
                    wRow.addView(wIcon);
                    TextView wTemp = new TextView(this);
                    wTemp.setText(wk[2] + "° / " + wk[1] + "°");
                    wTemp.setTextColor(MUTED);
                    wTemp.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                    wTemp.setGravity(Gravity.RIGHT);
                    LinearLayout.LayoutParams wtlp = new LinearLayout.LayoutParams(dp(80), -2);
                    wtlp.setMargins(dp(8), 0, 0, 0);
                    wTemp.setLayoutParams(wtlp);
                    wRow.addView(wTemp);
                    weatherExpanded.addView(wRow);
                }
            }
        }
    }

    private void addWeatherDetail(LinearLayout row, String label, String value) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(4), 0, dp(4), 0);
        col.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        TextView l = new TextView(this);
        l.setText(label);
        l.setTextColor(MUTED);
        l.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9);
        l.setGravity(Gravity.CENTER);
        col.addView(l);
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextColor(INK);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        v.setTypeface(geistBold());
        v.setGravity(Gravity.CENTER);
        col.addView(v);
        row.addView(col);
    }

    private LinearLayout createSleepCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setBackground(round(CARD, 20));

        LinearLayout heading = new LinearLayout(this);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(this);
        title.setText("Sleep summary");
        title.setTextColor(INK);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        title.setTypeface(geistBold());
        heading.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView last = new TextView(this);
        last.setText("LAST NIGHT");
        last.setTextColor(MUTED);
        last.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        last.setTypeface(geistBold());
        last.setGravity(Gravity.RIGHT);
        heading.addView(last);
        card.addView(heading);

        Health h = Health.shared();
        int total = h.sleepMinutes;
        if (total <= 0) {
            TextView none = new TextView(this);
            none.setText("No sleep data yet — run a Health Preview.");
            none.setTextColor(MUTED);
            none.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            none.setPadding(0, dp(12), 0, dp(4));
            card.addView(none);
            return card;
        }
        int deep = h.sleepDeepMinutes > 0 ? h.sleepDeepMinutes : 0;
        int light = h.sleepLightMinutes > 0 ? h.sleepLightMinutes : 0;
        int rem = h.sleepRemMinutes > 0 ? h.sleepRemMinutes : 0;
        int awake = h.sleepAwakeMinutes > 0 ? h.sleepAwakeMinutes : 0;
        if (deep + light + rem + awake == 0) {
            // fall back: treat all as light
            light = total;
        }

        // Total + quality
        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);
        topRow.setPadding(0, dp(12), 0, dp(8));
        TextView totalTv = new TextView(this);
        totalTv.setText((total / 60) + "h " + String.format("%02d", total % 60) + "m");
        totalTv.setTextColor(INK);
        totalTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        totalTv.setTypeface(geistBold());
        topRow.addView(totalTv, new LinearLayout.LayoutParams(0, -2, 1f));

        int quality = sleepQuality(total, deep, light, rem, awake);
        TextView qTv = new TextView(this);
        qTv.setText("Quality " + quality + "/100");
        qTv.setTextColor(quality >= 80 ? Color.rgb(46, 160, 67) : (quality >= 60 ? Color.rgb(230, 162, 60) : Color.rgb(220, 53, 69)));
        qTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        qTv.setTypeface(geistBold());
        qTv.setGravity(Gravity.RIGHT);
        topRow.addView(qTv);
        card.addView(topRow);

        // stage bars
        addSleepStage(card, "Deep", deep, total, Color.rgb(70, 85, 180));
        addSleepStage(card, "Light", light, total, Color.rgb(100, 160, 230));
        addSleepStage(card, "REM", rem, total, Color.rgb(150, 100, 200));
        addSleepStage(card, "Awake", awake, total, Color.rgb(180, 180, 180));

        // bedtime/wake placeholders
        TextView meta = new TextView(this);
        meta.setText("Efficiency " + sleepEfficiency(total, deep, light, rem, awake) + "%");
        meta.setTextColor(MUTED);
        meta.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        meta.setPadding(0, dp(8), 0, dp(4));
        card.addView(meta);

        return card;
    }

    private void addSleepStage(LinearLayout card, String label, int minutes, int total, int color) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(4), 0, dp(4));
        TextView l = new TextView(this);
        l.setText(label);
        l.setTextColor(MUTED);
        l.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        l.setLayoutParams(new LinearLayout.LayoutParams(dp(56), -2));
        row.addView(l);
        // bar
        LinearLayout barBg = new LinearLayout(this);
        barBg.setBackgroundColor(Color.rgb(235, 235, 240));
        barBg.setLayoutParams(new LinearLayout.LayoutParams(0, dp(10), 1f));
        barBg.setOrientation(LinearLayout.HORIZONTAL);
        View bar = new View(this);
        int pct = total > 0 ? Math.max(2, minutes * 100 / total) : 0;
        bar.setLayoutParams(new LinearLayout.LayoutParams(0, -1, pct));
        bar.setBackgroundColor(color);
        barBg.addView(bar);
        row.addView(barBg);
        TextView v = new TextView(this);
        int h = minutes / 60, m = minutes % 60;
        v.setText(String.format("%dh %02dm  %d%%", h, m, total > 0 ? minutes * 100 / total : 0));
        v.setTextColor(INK);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        v.setGravity(Gravity.RIGHT);
        LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(dp(110), -2);
        vlp.setMargins(dp(8), 0, 0, 0);
        v.setLayoutParams(vlp);
        row.addView(v);
        card.addView(row);
    }

    private int sleepQuality(int total, int deep, int light, int rem, int awake) {
        if (total <= 0) return 0;
        double deepRatio = deep / (double) total;
        double remRatio = rem / (double) total;
        double eff = total / (double) (total + awake);
        int q = (int) Math.round(40 * deepRatio * 2 + 30 * remRatio * 3 + 30 * eff);
        return Math.max(0, Math.min(100, q));
    }

    private int sleepEfficiency(int total, int deep, int light, int rem, int awake) {
        int inBed = total + awake;
        if (inBed <= 0) return 0;
        return Math.max(0, Math.min(100, total * 100 / inBed));
    }

    private LinearLayout createHealthDashboard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setBackground(round(CARD, 20));

        LinearLayout heading = new LinearLayout(this);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(this);
        title.setText("Health overview");
        title.setTextColor(INK);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        title.setTypeface(geistBold());
        title.setPadding(0, 0, 0, 0);
        heading.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        healthUpdatedView = new TextView(this);
        healthUpdatedView.setText("LAST UPDATE\n—");
        healthUpdatedView.setTextColor(MUTED);
        healthUpdatedView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        healthUpdatedView.setTypeface(geistBold());
        healthUpdatedView.setGravity(Gravity.RIGHT);
        heading.addView(healthUpdatedView);
        card.addView(heading);

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        healthStepsView = addMetric(row1, "Steps", "—", "steps", Color.rgb(235, 229, 255));
        healthHeartView = addMetric(row1, "Heart rate", "—", "bpm", Color.rgb(255, 232, 228));
        card.addView(row1);

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        healthSleepView = addMetric(row2, "Sleep", "—", "hours", Color.rgb(222, 247, 245));
        healthSpo2View = addMetric(row2, "Blood oxygen", "—", "%", Color.rgb(238, 235, 255));
        card.addView(row2);

        LinearLayout row3 = new LinearLayout(this);
        row3.setOrientation(LinearLayout.HORIZONTAL);
        healthDistanceView = addMetric(row3, "Distance", "—", "km", Color.rgb(230, 244, 255));
        healthCaloriesView = addMetric(row3, "Calories", "—", "kcal", Color.rgb(255, 235, 243));
        card.addView(row3);

        LinearLayout row4 = new LinearLayout(this);
        row4.setOrientation(LinearLayout.HORIZONTAL);
        healthStressView = addMetric(row4, "Stress", "—", "%", Color.rgb(241, 238, 255));
        healthWorkoutsView = addMetric(row4, "Workouts", "—", "sessions", Color.rgb(235, 248, 235));
        card.addView(row4);
        return card;
    }

    private TextView addMetric(LinearLayout row, String label, String value,
                               String unit, int color) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(12), dp(10), dp(8), dp(10));
        box.setBackground(round(color, 14));
        TextView name = new TextView(this);
        name.setText(label);
        name.setTextColor(MUTED);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        box.addView(name);
        TextView number = new TextView(this);
        number.setText(value);
        number.setTextColor(INK);
        number.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        number.setTypeface(geistBold());
        box.addView(number);
        TextView suffix = new TextView(this);
        suffix.setText(unit);
        suffix.setTextColor(MUTED);
        suffix.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        box.addView(suffix);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(88), 1f);
        lp.setMargins(dp(2), dp(2), dp(2), dp(6));
        row.addView(box, lp);
        return number;
    }

    private void renderHealthDashboard(Health h, String prefix) {
        healthStepsView.setText(h.hasActivity() ? String.format("%,d", h.steps) : "—");
        healthHeartView.setText(h.heartRate > 0 ? String.valueOf(h.heartRate) : "—");
        if (h.sleepMinutes > 0) {
            healthSleepView.setText(String.format("%d.%02d", h.sleepMinutes / 60,
                    (h.sleepMinutes % 60) * 100 / 60));
        } else {
            healthSleepView.setText("—");
        }
        healthSpo2View.setText(h.spo2 >= 0 ? h.spo2 + "%" : "—");
        healthDistanceView.setText(h.distanceM > 0 ? String.format("%.1f", h.distanceM / 1000.0) : "—");
        healthCaloriesView.setText(h.calories > 0 ? String.format("%,.0f", h.kcal()) : "—");
        healthStressView.setText(h.stress >= 0 ? String.valueOf(h.stress) : "—");
        healthWorkoutsView.setText(h.workouts > 0 ? String.valueOf(h.workouts) : "—");
        if (h.updatedAt > 0) {
            String stamp = new java.text.SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
                    .format(new Date(h.updatedAt));
            healthUpdatedView.setText("LAST UPDATE\n" + stamp);
        } else {
            healthUpdatedView.setText("LAST UPDATE\n—");
        }
        batteryTopView.setText(h.battery >= 0 ? "BAT " + h.battery + "%" : "BAT —");
    }

    private Button navButton(String label, boolean selected) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        b.setTypeface(geistBold());
        b.setTextColor(selected ? Color.WHITE : MUTED);
        b.setBackground(round(selected ? DARK_CARD : CARD, 14));
        b.setStateListAnimator(null);
        b.setLayoutParams(new LinearLayout.LayoutParams(0, -1, 1f));
        return b;
    }

    private void selectPage(FrameLayout pages, int index, Button... nav) {
        for (int i = 0; i < pages.getChildCount(); i++) {
            pages.getChildAt(i).setVisibility(i == index ? View.VISIBLE : View.GONE);
        }
        for (int i = 0; i < nav.length; i++) {
            nav[i].setTextColor(i == index ? Color.WHITE : MUTED);
            nav[i].setBackground(round(i == index ? DARK_CARD : CARD, 14));
        }
    }

    /** Opens the in-app app guide; reached from the Debug tab via the "?" icon. */
    private void showDocPage(FrameLayout pages, Button facesNav, Button quickNav, Button debugNav) {
        for (int i = 0; i < pages.getChildCount(); i++) {
            pages.getChildAt(i).setVisibility(i == 3 ? View.VISIBLE : View.GONE);
        }
        facesNav.setTextColor(MUTED);
        quickNav.setTextColor(MUTED);
        debugNav.setTextColor(Color.WHITE);
        debugNav.setBackground(round(true ? DARK_CARD : CARD, 14));
    }

    private Button button(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        styleButton(b, label.toLowerCase().contains("connect") ||
                label.toLowerCase().contains("upload") ||
                label.toLowerCase().contains("browse"));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(4);
        b.setLayoutParams(lp);
        return b;
    }

    private TextView section(String label) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextColor(INK);
        t.setTypeface(geistBold(), android.graphics.Typeface.BOLD);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        t.setLetterSpacing(.12f);
        t.setPadding(dp(4), dp(18), dp(4), dp(6));
        return t;
    }

    private GradientDrawable round(int color, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        return d;
    }

    private GradientDrawable roundStroke(int color, int radiusDp, int strokeColor, int strokeDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        d.setStroke(dp(strokeDp), strokeColor);
        return d;
    }

    private void styleButton(Button b, boolean primary) {
        b.setTextColor(primary ? Color.WHITE : INK);
        b.setTypeface(geistBold());
        b.setAllCaps(false);
        b.setMinHeight(dp(primary ? 54 : 48));
        b.setPadding(dp(14), dp(8), dp(14), dp(8));
        b.setBackground(primary ? round(DARK_CARD, 16)
                : roundStroke(CARD, 16, ACCENT, 1));
        b.setStateListAnimator(null);
    }

    private int dp(int v) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }

    private void status(String s) {
        ui.post(() -> statusView.setText(s));
    }

    /** Reuse the single BLE session owned by WatchService when the activity was recreated. */
    private Watch activeWatch() {
        Watch w = watch;
        Ble b = ble;
        if ((w == null || b == null || !b.isConnected()) && WatchService.sessionConnected()) {
            w = WatchService.sessionWatch();
            b = WatchService.sessionBle();
            if (w != null && b != null && b.isConnected()) {
                watch = w;
                ble = b;
                logLine("Adopted existing companion session for face transfer.");
            }
        }
        if (w != null) w.setProgressListener(pct -> ui.post(() -> {
            progress.setProgress(pct);
            if (downloadProgressText != null) {
                downloadProgressText.setText("Installing " + pct + "%");
            }
        }));
        return w;
    }

    /** Uploads a downloaded face and offers a slot picker when the watch is full. */
    private void installFace(Watch w, byte[] bytes, String seed, String label) {
        try {
            w.upload(bytes, -1, seed);
            status("Installed " + label);
            ui.post(() -> {
                progress.setProgress(100);
                downloadProgressText.setText("Installed  ·  " + label);
            });
        } catch (Watch.AlreadyInstalledException e) {
            logLine("Already installed: " + e.getMessage());
            status(label + " is already on the watch — nothing sent");
            ui.post(() -> {
                downloadProgressText.setText("Already installed  ·  " + label);
                Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
            });
        } catch (Watch.NeedSlotException e) {
            logLine("The watch cannot take another face — asking which dial to replace.");
            ui.post(() -> showSlotPicker(w, bytes, seed, label, e));
        } catch (Throwable t) {
            String m = msg(t);
            logLine("Face install failed: " + m);
            // The watch sometimes refuses INIT_1 (0x0a) when its BLE session is in a stale/busy
            // state left over from an interrupted transfer (or when its dial list is unknown).
            // Reset the session and retry once.
            if (m.contains("INIT_1")) {
                logLine("Resetting BLE session and retrying install…");
                WatchService.resetSession();
                if (WatchService.awaitConnected(30000)) {
                    Ble nb = WatchService.sessionBle();
                    Watch nw = WatchService.sessionWatch();
                    if (nb != null && nb.isConnected() && nw != null) {
                        ble = nb; watch = nw;
                        try {
                            nw.upload(bytes, -1, seed);
                            status("Installed " + label);
                            ui.post(() -> {
                                progress.setProgress(100);
                                downloadProgressText.setText("Installed  ·  " + label);
                            });
                            return;
                        } catch (Throwable t2) {
                            fail("Face install failed", t2);
                            return;
                        }
                    }
                }
            }
            fail("Face install failed", t);
        }
    }

    /**
     * Lists the watch's own dial ids so one can be overwritten with the new face.
     * The rows are plain buttons because a framework {@link AlertDialog} shows either
     * a message or an item list, never both.
     */
    private void showSlotPicker(final Watch w, final byte[] bytes, final String seed,
                                final String label, final Watch.NeedSlotException e) {
        status("Choose a face for the watch to overwrite");
        final int[] ids = e.installed;
        if (ids.length == 0) {
            fail("Face install failed", new IOException(
                    "the watch refused the upload, but it has not reported its dial ids yet"
                            + " — reconnect and try again"));
            return;
        }

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = dp(16);
        box.setPadding(p, p, p, p);

        TextView note = new TextView(this);
        note.setText("The watch holds " + ids.length + " of " + e.capacity + " faces.\n\n"
                + e.getMessage() + "\n\nChoose the dial to overwrite with “" + label + "”.");
        note.setTextColor(MUTED);
        note.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        box.addView(note);

        int current = (seed == null || seed.isEmpty()) ? -1 : Proto.dialId(seed);
        final AlertDialog[] holder = new AlertDialog[1];
        boolean selectable = false;
        for (int i = 0; i < ids.length; i++) {
            final int id = ids[i];
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(52));
            lp.setMargins(0, dp(3), 0, dp(3));

            if (id == current) {
                // The watch rolls a face back instead of overwriting it with itself.
                TextView self = new TextView(this);
                self.setText("Slot " + (i + 1) + "  ·  id " + id
                        + "   (this face — the watch cannot overwrite it in place)");
                self.setTextColor(MUTED);
                self.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                self.setPadding(dp(12), dp(10), dp(12), dp(10));
                box.addView(self, lp);
                continue;
            }
            selectable = true;

            Button row = new Button(this);
            row.setText("Slot " + (i + 1) + "  ·  id " + id);
            row.setAllCaps(false);
            row.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            row.setTypeface(geistBold());
            row.setTextColor(INK);
            row.setBackground(round(CARD, 14));
            row.setStateListAnimator(null);
            box.addView(row, lp);
            row.setOnClickListener(v -> {
                AlertDialog d = holder[0];
                if (d != null) d.dismiss();
                replaceAndInstall(w, bytes, seed, label, id);
            });
        }

        if (!selectable) {
            TextView none = new TextView(this);
            none.setText("No other face is available to overwrite. Remove a face on the watch "
                    + "first, then install again.");
            none.setTextColor(MUTED);
            none.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            box.addView(none);
        }

        ScrollView sc = new ScrollView(this);
        sc.addView(box);
        holder[0] = new AlertDialog.Builder(this)
                .setTitle("Replace a watch face")
                .setView(sc)
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void replaceAndInstall(final Watch w, final byte[] bytes, final String seed,
                                   final String label, final int slot) {
        if (busy.getAndSet(true)) return;
        setBusy(true);
        acquireWakeLock();
        status("Installing " + label + " over dial " + slot + "…");
        worker.execute(() -> {
            try {
                w.upload(bytes, slot, seed);
                status("Installed " + label);
                ui.post(() -> {
                    progress.setProgress(100);
                    downloadProgressText.setText("Installed  ·  " + label);
                });
            } catch (Watch.AlreadyInstalledException e) {
                logLine("Already installed: " + e.getMessage());
                status(e.getMessage());
                ui.post(() -> Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show());
            } catch (Watch.NeedSlotException e) {
                logLine("The watch refused that dial — pick another.");
                ui.post(() -> showSlotPicker(w, bytes, seed, label, e));
            } catch (Throwable t) {
                fail("Face install failed", t);
            } finally {
                releaseWakeLock();
                setBusy(false);
            }
        });
    }

    private void logLine(String line) {
        android.util.Log.i("CMFx", line);
        appendPublicLog(line);
        ui.post(() -> {
            logView.append(line + "\n");
            if (logView.getText().length() > MAX_LOG_CHARS) {
                logView.setText(logView.getText().subSequence(
                        logView.getText().length() - MAX_LOG_CHARS / 2, logView.getText().length()));
            }
            logScroll.post(() -> logScroll.fullScroll(View.FOCUS_DOWN));
        });
    }

    private void ensureCmfXStorage() {
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                if (findPublicFile("", "README.txt") == null) {
                    writePublicFile("", "README.txt", "text/plain",
                            ("CMFx storage\n\n"
                            + "Faces: downloaded FMC watch-face binaries.\n"
                            + "Health: labeled health-preview snapshots.\n"
                            + "Logs: CMFx diagnostic session logs.\n"
                            + "Pairing keys remain private to CMFx.\n").getBytes(StandardCharsets.UTF_8));
                }
            } else {
                File root = new File(Environment.getExternalStorageDirectory(), "Documents/CMFx");
                new File(root, "Faces").mkdirs();
                new File(root, "Health").mkdirs();
                new File(root, "Logs").mkdirs();
            }
        } catch (Throwable t) {
            android.util.Log.w("CMFx", "Storage folder unavailable", t);
        }
    }

    private Uri findPublicFile(String folder, String displayName) {
        if (Build.VERSION.SDK_INT < 29) return null;
        Uri root = MediaStore.Files.getContentUri("external");
        String rel = "Documents/CMFx/" + (folder == null ? "" : folder);
        if (!rel.endsWith("/")) rel += "/";
        try (android.database.Cursor c = getContentResolver().query(root,
                new String[]{MediaStore.MediaColumns._ID},
                MediaStore.MediaColumns.DISPLAY_NAME + "=? AND "
                        + MediaStore.MediaColumns.RELATIVE_PATH + "=?",
                new String[]{displayName, rel}, null)) {
            if (c != null && c.moveToFirst()) return Uri.withAppendedPath(root, c.getString(0));
        } catch (Throwable ignored) {}
        return null;
    }

    private Uri writePublicFile(String folder, String name, String mime, byte[] data) throws Exception {
        String rel = "Documents/CMFx/" + (folder == null || folder.isEmpty() ? "" : folder);
        if (!rel.endsWith("/")) rel += "/";
        // strip trailing slash for the helper (it re-adds)
        rel = rel.substring(0, rel.length() - 1);
        return writePublicFileAt(rel, name, mime, data);
    }

    /** Writes a file to an arbitrary relative path under external storage. */
    private Uri writePublicFileAt(String relativePath, String name, String mime, byte[] data) throws Exception {
        String clean = safeStorageName(name);
        if (Build.VERSION.SDK_INT >= 29) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, clean);
            values.put(MediaStore.MediaColumns.MIME_TYPE, mime);
            String rel = relativePath == null ? "" : relativePath.trim();
            if (rel.isEmpty()) rel = "Documents";
            if (!rel.endsWith("/")) rel += "/";
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, rel);
            Uri uri = getContentResolver().insert(MediaStore.Files.getContentUri("external"), values);
            if (uri == null) throw new IOException("could not create " + rel + clean);
            try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                if (out == null) throw new IOException("could not open " + clean);
                out.write(data);
            } catch (Throwable t) {
                getContentResolver().delete(uri, null, null);
                throw t;
            }
            return uri;
        }
        File dir = new File(Environment.getExternalStorageDirectory(),
                (relativePath == null ? "" : relativePath).replace("/", java.io.File.separator));
        if (!dir.exists() && !dir.mkdirs()) throw new IOException("could not create " + dir);
        File outFile = new File(dir, clean);
        try (OutputStream out = new java.io.FileOutputStream(outFile)) { out.write(data); }
        return Uri.fromFile(outFile);
    }

    private static String safeStorageName(String name) {
        String clean = name == null ? "data" : name.replaceAll("[^A-Za-z0-9._-]+", "_");
        return clean.isEmpty() ? "data" : clean;
    }

    private void appendPublicLog(String line) {
        try {
            if (sessionLogUri == null) {
                sessionLogUri = writePublicFile("Logs", sessionLogName, "text/plain",
                        (line + "\n").getBytes(StandardCharsets.UTF_8));
                return;
            }
            try (OutputStream out = getContentResolver().openOutputStream(sessionLogUri, "wa")) {
                if (out != null) out.write((line + "\n").getBytes(StandardCharsets.UTF_8));
            }
        } catch (Throwable ignored) {}
    }

    private void onHealthClicked() {
        if (busy.getAndSet(true)) return;
        final Watch w = activeWatch();
        final Ble b = ble;
        ui.post(() -> {
            connectBtn.setEnabled(false);
            browseBtn.setEnabled(false);
            healthBtn.setEnabled(false);
        });
        if (w == null || b == null || !b.isConnected()) {
            setBusy(false);
            fail("Health Preview unavailable", new IOException("Not connected to the watch"));
            return;
        }
        status("Health Preview…");
        worker.execute(() -> {
            try {
                w.fetchActivityData();
                w.getBattery();
                status("Health Preview complete");
            } catch (Throwable t) {
                fail("Health Preview failed", t);
            } finally {
                setBusy(false);
                ui.post(() -> {
                    String detail = w.health().describe();
                    renderHealthDashboard(w.health(), w.lastFetchOk() ? "Preview updated" : "Preview failed");
                    try {
                        String stamp = new java.text.SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
                                .format(new Date());
                        String snapshot = "CMFx Health Preview\n"
                                + "Last update: " + stamp + "\n\n" + detail + "\n";
                        writePublicFile("Health", "health-" + stamp + ".txt", "text/plain",
                                snapshot.getBytes(StandardCharsets.UTF_8));
                    } catch (Throwable e) {
                        logLine("Health snapshot storage unavailable: " + msg(e));
                    }
                    logLine("Health snapshot: " + detail.replace('\n', ' '));
                });
            }
        });
    }

    private void setBusy(boolean b) {
        busy.set(b);
        ui.post(() -> {
            connectBtn.setEnabled(!b);
            browseBtn.setEnabled(!b);
            healthBtn.setEnabled(!b);
            uploadBtn.setEnabled(!b && fileBytes != null && ble != null && ble.isConnected());
        });
    }

    private void fail(String what, Throwable t) {
        String m = t == null ? "" : (t.getMessage() == null ? t.toString() : t.getMessage());
        logLine("!! " + what + (m.isEmpty() ? "" : ": " + m));
        String userMessage = what;
        if (m.contains("finish=0x0a")) {
            userMessage = "The watch refused that face. Re-run the install and pick a dial to "
                    + "replace, or delete a face on the watch first.";
        }
        status(userMessage);
        final String toastMessage = userMessage;
        ui.post(() -> Toast.makeText(this, toastMessage, Toast.LENGTH_LONG).show());
    }

    // ---- companion editors --------------------------------------------

    private boolean sessionReady() {
        Watch w = watch;
        Ble b = ble;
        if (w == null || b == null || !b.isConnected()) {
            fail("Not connected", new IOException("connect to the watch first"));
            return false;
        }
        return true;
    }

    private void onDialSlotsClicked() {
        if (busy.getAndSet(true)) return;
        final Watch w = activeWatch();
        if (w == null) {
            setBusy(false);
            fail("Not connected", new IOException("connect to the watch first"));
            return;
        }
        status("Probing installed dial ids…");
        worker.execute(() -> {
            try {
                final String raw = w.probeWatchFaceList();
                status(raw.isEmpty() ? "The watch did not report its installed faces"
                        : "Dial list captured — see log");
                ui.post(() -> showDialSlotsDialog(w, raw));
            } catch (Throwable t) {
                fail("Dial probe failed", t);
            } finally {
                setBusy(false);
            }
        });
    }

    private void showTextDialog(String title, String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextIsSelectable(true);
        tv.setTypeface(Typeface.MONOSPACE);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        int p = dp(10);
        ScrollView sc = new ScrollView(this);
        sc.setPadding(p, p, p, p);
        sc.addView(tv);
        new AlertDialog.Builder(this).setTitle(title).setView(sc)
                .setPositiveButton("Close", null).show();
    }

    /**
     * Shows the dial ids the watch actually reported so one can be named in the
     * manual replace field. Nothing is invented: when the watch has not sent its
     * installed-dial list ({@code FFFF/A055}) the dialog says so instead.
     */
    private void showDialSlotsDialog(Watch w, String raw) {
        final int[] ids = w == null ? new int[0] : w.installedDialIds();
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = dp(14);
        box.setPadding(p, p, p, p);

        TextView note = new TextView(this);
        if (ids.length == 0) {
            note.setText("The watch has not reported its installed faces, so there are no "
                    + "dial ids to show. Reconnect (Quick tools → Connect) and probe again."
                    + "\n\nProbe reply: "
                    + (raw == null || raw.isEmpty() ? "(none)" : raw));
        } else {
            note.setText("Dial ids reported by the watch — tap one to fill the replace field."
                    + "\n" + ids.length + " of " + w.dialCapacity() + " slots in use.");
        }
        note.setTextColor(MUTED);
        note.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        box.addView(note);

        LinearLayout line = null;
        for (int i = 0; i < ids.length; i++) {
            if (i % 3 == 0) {
                line = new LinearLayout(this);
                line.setOrientation(LinearLayout.HORIZONTAL);
                box.addView(line, new LinearLayout.LayoutParams(-1, dp(94)));
            }
            final int id = ids[i];
            Button slot = new Button(this);
            slot.setText((i + 1) + "\nID " + id);
            slot.setAllCaps(false);
            slot.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            slot.setGravity(Gravity.CENTER);
            slot.setTextColor(INK);
            slot.setTypeface(geistBold());
            slot.setBackground(roundStroke(CARD, 14, ACCENT, 1));
            slot.setStateListAnimator(null);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(88), 1f);
            lp.setMargins(dp(3), dp(3), dp(3), dp(3));
            line.addView(slot, lp);
            slot.setOnClickListener(v -> {
                replaceIdView.setText(String.valueOf(id));
                status("Replace ID set to " + id + " — upload to reuse that slot");
                Toast.makeText(this, "Replace ID set to " + id, Toast.LENGTH_SHORT).show();
            });
        }

        new AlertDialog.Builder(this)
                .setTitle("Installed face slots")
                .setView(box)
                .setPositiveButton("Close", null)
                .show();
    }

    private EditText input(String hint, String value, int type) {
        EditText e = new EditText(this);
        e.setHint(hint);
        if (value != null) e.setText(value);
        e.setSingleLine(true);
        e.setInputType(type);
        return e;
    }

    private LinearLayout labeled(String label, View field) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        t.setTextColor(Color.rgb(120, 120, 128));
        row.addView(t);
        row.addView(field, new LinearLayout.LayoutParams(-1, -2));
        return row;
    }

    private static int parseInt(String s, int fallback) {
        try { return Integer.parseInt(s.trim()); } catch (Throwable t) { return fallback; }
    }

    // ---- alarms -------------------------------------------------------

    private void onAlarmsClicked() {
        if (!sessionReady()) return;
        final Watch w = watch;
        status("Reading alarms…");
        worker.execute(() -> {
            java.util.List<Watch.AlarmEntry> existing;
            try {
                existing = w.readAlarms();
            } catch (Throwable t) {
                fail("Could not read alarms", t);
                return;
            }
            ui.post(() -> showAlarmDialog(w, existing));
        });
    }

    private void showAlarmDialog(Watch w, java.util.List<Watch.AlarmEntry> existing) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = dp(14);
        box.setPadding(p, p, p, p);

        TextView note = new TextView(this);
        StringBuilder sb = new StringBuilder();
        if (existing.isEmpty()) {
            sb.append("The watch reported no alarms. Sending replaces the full alarm list.");
        } else {
            sb.append("Uncheck an alarm to delete it.\n");
            sb.append("On the watch now:\n");
            for (Watch.AlarmEntry a : existing) sb.append("• ").append(a.describe()).append('\n');
        }
        note.setText(sb.toString());
        note.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        box.addView(note);

        final java.util.List<android.widget.CheckBox> keep = new ArrayList<>();
        for (Watch.AlarmEntry a : existing) {
            android.widget.CheckBox cb = new android.widget.CheckBox(this);
            cb.setText(a.describe());
            cb.setChecked(true);
            keep.add(cb);
            box.addView(cb);
        }

        android.widget.CheckBox addNew = new android.widget.CheckBox(this);
        addNew.setText("Add the alarm below");
        addNew.setChecked(true);
        box.addView(addNew);

        EditText time = input("HH:MM", "07:30", android.text.InputType.TYPE_CLASS_TEXT);
        box.addView(labeled("Alarm time", time));
        EditText label = input("Label (optional)", "", android.text.InputType.TYPE_CLASS_TEXT);
        box.addView(labeled("Label", label));

        Spinner repeat = new Spinner(this);
        repeat.setAdapter(new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_dropdown_item, REPEAT_NAMES));
        box.addView(labeled("Repeat", repeat));

        android.widget.CheckBox enabled = new android.widget.CheckBox(this);
        enabled.setText("Enabled");
        enabled.setChecked(true);
        box.addView(enabled);

        EditText raw = input("Hex payload (advanced, empty = normal)", "",
                android.text.InputType.TYPE_CLASS_TEXT);
        box.addView(labeled("Raw ALARMS_SET payload", raw));

        ScrollView scroll = new ScrollView(this);
        scroll.addView(box);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Alarms")
                .setView(scroll)
                .setPositiveButton("Send", null)
                .setNegativeButton("Cancel", null)
                .create();
        dialog.setOnShowListener(x -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String rawText = raw.getText().toString().replaceAll("[^0-9a-fA-F]", "");
                    if (!rawText.isEmpty()) {
                        final byte[] payload = Proto.unhex(rawText);
                        dialog.dismiss();
                        status("Writing raw alarm payload…");
                        worker.execute(() -> {
                            try {
                                String back = w.sendRawAlarm(payload);
                                status("Raw write done");
                                ui.post(() -> showTextDialog("Alarm read-back",
                                        back.isEmpty() ? "(no reply)" : back));
                            } catch (Throwable t) {
                                fail("Raw alarm write failed", t);
                            }
                        });
                        return;
                    }
                    final java.util.List<Watch.AlarmEntry> all = new ArrayList<>();
                    for (int i = 0; i < keep.size(); i++) {
                        if (keep.get(i).isChecked()) all.add(existing.get(i));
                    }
                    Watch.AlarmEntry entry = null;
                    if (addNew.isChecked()) {
                        int hour = 7, minute = 30;
                        String[] parts = time.getText().toString().trim().split(":");
                        if (parts.length >= 2) {
                            hour = parseInt(parts[0], 7);
                            minute = parseInt(parts[1], 30);
                        } else {
                            Toast.makeText(this, "Use HH:MM", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
                            Toast.makeText(this, "Time out of range", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        entry = new Watch.AlarmEntry(hour, minute, enabled.isChecked(),
                                REPEAT_VALUES[repeat.getSelectedItemPosition()],
                                label.getText().toString());
                        all.add(entry);
                    }
                    if (all.size() > 5) {
                        Toast.makeText(this, "The watch holds 5 alarms — uncheck one first",
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                    final Watch.AlarmEntry added = entry;
                    dialog.dismiss();
                    status("Sending alarms…");
                    worker.execute(() -> {
                        try {
                            w.sendAlarms(all);
                            status("Alarms sent (" + all.size() + ")");
                            if (added != null) logLine("Alarm added: " + added.describe());
                        } catch (Throwable t) {
                            fail("Alarm send failed", t);
                        }
                    });
                }));
        dialog.show();
    }

    // ---- contacts -----------------------------------------------------

    private void onContactsClicked() {
        if (!sessionReady()) return;
        final Watch w = watch;
        status("Reading contacts…");
        worker.execute(() -> {
            java.util.List<Watch.ContactEntry> existing;
            try {
                existing = w.readContacts();
            } catch (Throwable t) {
                fail("Could not read contacts", t);
                return;
            }
            ui.post(() -> showContactDialog(w, existing));
        });
    }

    private void showContactDialog(Watch w, java.util.List<Watch.ContactEntry> existing) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = dp(14);
        box.setPadding(p, p, p, p);

        TextView note = new TextView(this);
        StringBuilder sb = new StringBuilder();
        if (existing.isEmpty()) {
            sb.append("The watch reported no contacts. Sending replaces the full contact list.");
        } else {
            sb.append("Uncheck a contact to delete it.\n");
            sb.append("On the watch now:\n");
            for (Watch.ContactEntry c : existing) sb.append("• ").append(c.name)
                    .append("  ").append(c.number).append('\n');
        }
        note.setText(sb.toString());
        note.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        box.addView(note);

        final java.util.List<android.widget.CheckBox> keep = new ArrayList<>();
        for (Watch.ContactEntry c : existing) {
            android.widget.CheckBox cb = new android.widget.CheckBox(this);
            cb.setText(c.name + "  " + c.number);
            cb.setChecked(true);
            keep.add(cb);
            box.addView(cb);
        }

        android.widget.CheckBox addNew = new android.widget.CheckBox(this);
        addNew.setText("Add the contact below");
        addNew.setChecked(true);
        box.addView(addNew);

        EditText name = input("Name", "", android.text.InputType.TYPE_CLASS_TEXT);
        box.addView(labeled("Name", name));
        EditText number = input("Phone number", "", android.text.InputType.TYPE_CLASS_PHONE);
        box.addView(labeled("Number", number));

        ScrollView scroll = new ScrollView(this);
        scroll.addView(box);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Contacts")
                .setView(scroll)
                .setPositiveButton("Send", null)
                .setNegativeButton("Cancel", null)
                .create();
        dialog.setOnShowListener(x -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    final java.util.List<Watch.ContactEntry> all = new ArrayList<>();
                    for (int i = 0; i < keep.size(); i++) {
                        if (keep.get(i).isChecked()) all.add(existing.get(i));
                    }
                    Watch.ContactEntry added = null;
                    if (addNew.isChecked()) {
                        String n = name.getText().toString().trim();
                        String num = number.getText().toString().trim();
                        if (n.isEmpty() || num.isEmpty()) {
                            Toast.makeText(this, "Name and number are required", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        added = new Watch.ContactEntry(n, num);
                        all.add(added);
                    }
                    if (all.size() > 20) {
                        Toast.makeText(this, "The watch holds 20 contacts", Toast.LENGTH_LONG).show();
                        return;
                    }
                    final Watch.ContactEntry sent = added;
                    dialog.dismiss();
                    status("Sending contacts…");
                    worker.execute(() -> {
                        try {
                            w.sendContacts(all);
                            status("Contacts sent (" + all.size() + ")");
                            if (sent != null) logLine("Contact added: " + sent.name + " / " + sent.number);
                        } catch (Throwable t) {
                            fail("Contact send failed", t);
                        }
                    });
                }));
        dialog.show();
    }

    // ---- weather ------------------------------------------------------

    private void onWeatherClicked() {
        if (!sessionReady()) return;

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = dp(14);
        box.setPadding(p, p, p, p);

        EditText temp = input("°C", String.valueOf(lastWeatherTemp), android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);
        EditText min = input("°C", String.valueOf(lastWeatherMin), android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);
        EditText max = input("°C", String.valueOf(lastWeatherMax), android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);
        EditText humidity = input("%", String.valueOf(lastWeatherHumidity), android.text.InputType.TYPE_CLASS_NUMBER);
        EditText location = input("City", lastWeatherLocation, android.text.InputType.TYPE_CLASS_TEXT);
        EditText lat = input("0", String.valueOf(lastWeatherLat), android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText lon = input("0", String.valueOf(lastWeatherLon), android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);

        Spinner condition = new Spinner(this);
        condition.setAdapter(new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_dropdown_item, CONDITION_NAMES));
        int selected = 0;
        for (int i = 0; i < CONDITION_CODES.length; i++) {
            if (CONDITION_CODES[i] == lastWeatherCondition) selected = i;
        }
        condition.setSelection(selected);

        box.addView(labeled("Current temperature", temp));
        box.addView(labeled("Minimum", min));
        box.addView(labeled("Maximum", max));
        box.addView(labeled("Humidity", humidity));
        box.addView(labeled("Condition", condition));
        box.addView(labeled("Location", location));
        box.addView(labeled("Latitude (for sunrise/sunset)", lat));
        box.addView(labeled("Longitude (for sunrise/sunset)", lon));

        ScrollView scroll = new ScrollView(this);
        scroll.addView(box);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Send weather")
                .setView(scroll)
                .setPositiveButton("Send", null)
                .setNegativeButton("Cancel", null)
                .create();
        dialog.setOnShowListener(x -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    final int t = parseInt(temp.getText().toString(), lastWeatherTemp);
                    final int mn = parseInt(min.getText().toString(), lastWeatherMin);
                    final int mx = parseInt(max.getText().toString(), lastWeatherMax);
                    final int hum = Math.max(0, Math.min(100,
                            parseInt(humidity.getText().toString(), lastWeatherHumidity)));
                    final int code = CONDITION_CODES[condition.getSelectedItemPosition()];
                    final String loc = location.getText().toString().trim();
                    double la, lo;
                    try { la = Double.parseDouble(lat.getText().toString().trim()); }
                    catch (Throwable e) { la = lastWeatherLat; }
                    try { lo = Double.parseDouble(lon.getText().toString().trim()); }
                    catch (Throwable e) { lo = lastWeatherLon; }

                    lastWeatherTemp = t; lastWeatherMin = mn; lastWeatherMax = mx;
                    lastWeatherHumidity = hum; lastWeatherCondition = code;
                    lastWeatherLocation = loc; lastWeatherLat = la; lastWeatherLon = lo;

                    final double flat = la, flon = lo;
                    dialog.dismiss();
                    status("Sending weather…");
                    final Watch w = watch;
                    worker.execute(() -> {
                        try {
                            w.sendWeather(code, t, mn, mx, hum, loc, flat, flon);
                            status("Weather sent");
                        } catch (Throwable e) {
                            fail("Weather send failed", e);
                        }
                    });
                }));
        dialog.show();
    }

    // ---- permissions --------------------------------------------------

    private String[] missingPermissions() {
        List<String> need = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 31) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                need.add(Manifest.permission.BLUETOOTH_SCAN);
            }
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                need.add(Manifest.permission.BLUETOOTH_CONNECT);
            }
        } else {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                need.add(Manifest.permission.ACCESS_FINE_LOCATION);
            }
        }
        return need.toArray(new String[0]);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode != REQ_PERMS) return;
        for (int r : results) {
            if (r != PackageManager.PERMISSION_GRANTED) {
                fail("Bluetooth permission denied", null);
                return;
            }
        }
        Runnable r = afterPerms;
        afterPerms = null;
        if (r != null) r.run();
    }

    // ---- connect ------------------------------------------------------

    private void onConnectClicked() {
        if (busy.get()) return;
        String[] need = missingPermissions();
        if (need.length > 0) {
            afterPerms = this::connectNow;
            requestPermissions(need, REQ_PERMS);
            return;
        }
        connectNow();
    }

    private void connectNow() {
        if (busy.getAndSet(true)) return;
        status("Connecting…");
        ui.post(() -> {
            connectBtn.setEnabled(false);
            uploadBtn.setEnabled(false);
        });

        worker.execute(() -> {
            try {
                BluetoothManager mgr = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
                BluetoothAdapter adapter = mgr == null ? null : mgr.getAdapter();
                if (adapter == null) throw new IOException("this device has no Bluetooth adapter");
                if (!adapter.isEnabled()) throw new IOException("Bluetooth is off — turn it on and retry");

                // The companion service is the single owner of the BLE session, so the
                // uploader, notifications and dashboard never open competing connections.
                startService(new Intent(this, WatchService.class));
                if (!WatchService.sessionConnected()) {
                    logLine("Waiting for the companion session…");
                    if (!WatchService.awaitConnected(30000)) {
                        throw new IOException("could not connect — is Nothing X holding the watch?");
                    }
                }
                Ble b = WatchService.sessionBle();
                Watch w = WatchService.sessionWatch();
                if (b == null || !b.isConnected() || w == null) {
                    throw new IOException("companion session unavailable");
                }

                ble = b;
                watch = w;
                logLine("Adopted companion session.");
                status("Connected to " + b.name());
                ui.post(() -> {
                    progress.setProgress(0);
                    uploadBtn.setEnabled(fileBytes != null);
                    Runnable after = afterConnect;
                    afterConnect = null;
                    if (after != null) after.run();
                });
            } catch (Throwable t) {
                afterConnect = null;
                fail("Connect failed", t);
                watch = null;
            } finally {
                setBusy(false);
            }
        });
    }

    // ---- auth key -----------------------------------------------------

    private void onCheckAuthKeyClicked() {
        showAuthKeyDialog();
    }

    private void showAuthKeyDialog() {
        String key = Watch.storedAuthKeyHex();
        Watch w = WatchService.sessionWatch();
        boolean connected = WatchService.sessionConnected();

        String statusText;
        if (key == null) {
            statusText = "Auth key: (none stored yet)\n\n"
              + "Connect to the watch once. CMFx will pair, derive the key "
              + "and store it here.";
        } else {
            String status = connected && w != null && w.authKeyVerified()
                    ? "Status: VERIFIED - the watch accepted this key without re-pairing."
                    : (connected && w != null && w.authKeyFreshPairing())
                    ? "Status: FRESHLY PAIRED - the key was just re-derived. "
                      + "If Gadgetbridge rejects its old key, paste this new value there."
                    : "Status: STORED - not verified in this session. "
                      + "Tap Test now to check it against the watch.";
            statusText = status + "\n\n16 bytes / 32 hex chars. Gadgetbridge -> Device settings -> Auth key.";
        }

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);

        // Current auth key with a one-tap copy button.
        LinearLayout keyRow = new LinearLayout(this);
        keyRow.setOrientation(LinearLayout.HORIZONTAL);
        keyRow.setGravity(Gravity.CENTER_VERTICAL);
        keyRow.setPadding(0, dp(4), 0, dp(10));

        TextView keyTv = new TextView(this);
        keyTv.setText(key == null ? "(none stored yet)" : key);
        keyTv.setTextColor(INK);
        keyTv.setTypeface(Typeface.MONOSPACE);
        keyTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        keyTv.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        keyRow.addView(keyTv, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        ImageButton copyBtn = new ImageButton(this);
        copyBtn.setContentDescription("Copy auth key");
        copyBtn.setScaleType(ImageView.ScaleType.FIT_CENTER);
        copyBtn.setBackgroundResource(android.R.drawable.btn_default);
        copyBtn.setImageTintList(ColorStateList.valueOf(INK));
        copyBtn.setImageResource(getResources().getIdentifier("ic_copy", "drawable", getPackageName()));
        copyBtn.setLayoutParams(new LinearLayout.LayoutParams(dp(34), dp(34)));
        copyBtn.setOnClickListener(v -> copyAuthKey(key));
        keyRow.addView(copyBtn, new LinearLayout.LayoutParams(dp(34), dp(34)));
        content.addView(keyRow);

        TextView statusTv = new TextView(this);
        statusTv.setText(statusText);
        statusTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        statusTv.setTextColor(INK);
        statusTv.setPadding(0, 0, 0, dp(10));
        content.addView(statusTv);

        TextView warnTv = new TextView(this);
        warnTv.setText(GADGETBRIDGE_WARNING);
        warnTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        warnTv.setTextColor(MUTED);
        warnTv.setPadding(0, 0, 0, dp(14));
        content.addView(warnTv);

        ScrollView sc = new ScrollView(this);
        sc.addView(content);

        new AlertDialog.Builder(this)
                .setTitle("Check auth key")
                .setView(sc)
                .setPositiveButton("Test now", (d, x) -> testAuthKey())
                .setNegativeButton("Close", null)
                .show();
    }

    /** Copies the stored auth key to the clipboard so it can be pasted into Gadgetbridge. */
    private void copyAuthKey(String key) {
        if (key == null || key.isEmpty()) {
            Toast.makeText(this, "No auth key stored yet", Toast.LENGTH_SHORT).show();
            return;
        }
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("cmfx_auth_key", key));
        }
        Toast.makeText(this, "Auth key copied", Toast.LENGTH_SHORT).show();
    }

    /** Verifies the stored key by making sure a session authenticated with it. */
    private void testAuthKey() {
        if (WatchService.sessionConnected()) {
            showAuthKeyDialog();
            return;
        }
        if (busy.get()) return;
        status(Watch.storedAuthKeyHex() == null
                ? "No auth key yet — connecting to pair…"
                : "Testing the stored auth key against the watch…");
        afterConnect = this::showAuthKeyDialog;
        connectNow();
    }

    // ---- native FMC browser -------------------------------------------

    private static final String FMC_API =
            "https://fmc.freethinkel.dev/api/collections/watchfaces/records"
            + "?page=1&perPage=100&skipTotal=1&sort=-created"
            + "&filter=published%20%3D%20true";

    private static final class Face {
        final String id, name, device, preview, description, created;
        final int downloads, likes;
        Face(String id, String name, String device, String preview, String description,
             int downloads, String created, int likes) {
            this.id = id; this.name = name; this.device = device; this.preview = preview;
            this.description = description; this.downloads = downloads; this.created = created;
            this.likes = likes;
        }
    }

    /** Reusable tile view holder for the FMC browser (avoids recreating hundreds of views). */
    private static final class FaceTile {
        LinearLayout root;
        ImageView thumb;
        TextView name;
        TextView meta;
        Button download;
    }

    /** Square image view that clips FMC previews into a watch-face circle. */
    private static final class RoundFaceImage extends ImageView {
        RoundFaceImage(Context context) {
            super(context);
            setClipToOutline(true);
            setOutlineProvider(new ViewOutlineProvider() {
                @Override public void getOutline(View view, Outline outline) {
                    outline.setOval(0, 0, view.getWidth(), view.getHeight());
                }
            });
        }

        @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, widthMeasureSpec);
            setMeasuredDimension(getMeasuredWidth(), getMeasuredWidth());
        }
    }

    private void browseFmc() {
        if (busy.get()) return;
        final EditText search = new EditText(this);
        search.setHint("Search faces");
        search.setSingleLine(true);
        final LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setMinimumHeight(dp(700));
        box.setBackgroundColor(FMC_BG);
        int p = dp(12);
        box.setPadding(p, p, p, 0);

        search.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        search.setTypeface(geist());
        search.setTextColor(Color.WHITE);
        search.setHintTextColor(FMC_MUTED);
        search.setCompoundDrawablesWithIntrinsicBounds(android.R.drawable.ic_menu_search, 0, 0, 0);
        search.setCompoundDrawablePadding(dp(8));
        search.setPadding(dp(12), 0, dp(12), 0);
        search.setBackground(round(FMC_CARD, 12));
        if (search.getCompoundDrawables()[0] != null) search.getCompoundDrawables()[0].setTint(FMC_MUTED);
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(-1, dp(46));
        searchParams.bottomMargin = dp(8);
        box.addView(search, searchParams);

        LinearLayout filters = new LinearLayout(this);
        filters.setOrientation(LinearLayout.HORIZONTAL);
        filters.setGravity(Gravity.CENTER_VERTICAL);

        Spinner device = new Spinner(this);
        device.setAdapter(fmcSpinnerAdapter(new String[]{"Watch Pro 2"}));
        styleFmcSpinner(device);
        LinearLayout.LayoutParams deviceParams = new LinearLayout.LayoutParams(0, dp(46), 1f);
        deviceParams.setMargins(0, 0, dp(6), dp(8));
        filters.addView(device, deviceParams);

        final Spinner sort = new Spinner(this);
        sort.setAdapter(fmcSpinnerAdapter(new String[]{"Newest", "Most downloads", "Most liked"}));
        sort.setSelection(0, false);
        styleFmcSpinner(sort);
        LinearLayout.LayoutParams sortParams = new LinearLayout.LayoutParams(0, dp(46), 1f);
        sortParams.setMargins(0, 0, 0, dp(8));
        filters.addView(sort, sortParams);
        box.addView(filters);
        final LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setTag(100);
        final ScrollView results = new ScrollView(this);
        results.setFillViewport(true);
        results.addView(list, new ScrollView.LayoutParams(-1, -2));
        box.addView(results, new LinearLayout.LayoutParams(-1, 0, 1f));
        final TextView loading = new TextView(this);
        loading.setText("Loading FMC catalog…");
        loading.setTextColor(MUTED);
        loading.setPadding(dp(6), dp(18), dp(6), dp(18));
        list.addView(loading);
        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(box)
                .setNegativeButton("Close", null)
                .create();
        dialog.show();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawable(round(FMC_BG, 18));
        Button close = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        close.setText("CLOSE");
        close.setAllCaps(false);
        close.setTextColor(Color.WHITE);
        close.setTypeface(geistBold());
        close.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        close.setPadding(dp(22), dp(7), dp(22), dp(7));
        close.setMinHeight(dp(42));
        close.setBackground(round(DARK_CARD, 22));
        close.setStateListAnimator(null);

        worker.execute(() -> {
            try {
                final ArrayList<Face> faces = getCachedFaces(false);
                ui.post(() -> {
                    list.removeView(loading);
                    populateFaces(list, search, sort, faces, dialog);
                    // Debounced search: rebuild at most once per 200 ms of idle typing.
                    final Handler searchDebounce = new Handler(Looper.getMainLooper());
                    final Runnable[] searchTask = new Runnable[1];
                    search.addTextChangedListener(new android.text.TextWatcher() {
                        public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
                        public void onTextChanged(CharSequence s, int st, int b, int c) {
                            if (searchTask[0] != null) searchDebounce.removeCallbacks(searchTask[0]);
                            searchTask[0] = () -> populateFaces(list, search, sort, faces, dialog);
                            searchDebounce.postDelayed(searchTask[0], 200);
                        }
                        public void afterTextChanged(android.text.Editable e) {}
                    });
                    sort.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                        public void onNothingSelected(android.widget.AdapterView<?> p) {}
                        public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id) {
                            populateFaces(list, search, sort, faces, dialog);
                        }
                    });
                });
            } catch (Throwable t) {
                ui.post(() -> loading.setText("Could not load FMC: " + msg(t)));
            }
        });
    }

    private static final class DownloadedFace {
        final String displayName;
        final String faceName;
        final long size;
        final Uri uri;
        final File file;
        final byte[] bytes;
        final byte[] previewBytes;
        final String folderRel;
        DownloadedFace(String displayName, String faceName, long size, Uri uri, File file, byte[] bytes, byte[] previewBytes, String folderRel) {
            this.displayName = displayName; this.faceName = faceName; this.size = size;
            this.uri = uri; this.file = file; this.bytes = bytes; this.previewBytes = previewBytes;
            this.folderRel = folderRel;
        }
    }

    private void scanFaceDir(File dir, ArrayList<DownloadedFace> out, java.util.LinkedHashSet<String> seen) {
        File[] fs = dir.listFiles();
        if (fs == null) return;
        java.util.HashMap<String, File> imageFiles = new java.util.HashMap<>();
        for (File f : fs) {
            if (!f.isFile()) continue;
            String lower = f.getName().toLowerCase();
            if (lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png")) {
                int dot = f.getName().lastIndexOf('.');
                String base = (dot < 0) ? f.getName() : f.getName().substring(0, dot);
                if (!imageFiles.containsKey(base)) imageFiles.put(base, f);
            }
        }
        for (File f : fs) {
            if (!f.isFile() || !f.getName().toLowerCase().endsWith(".bin")) continue;
            if (!seen.add(f.getName())) continue;
            byte[] header = readFileHeader(f, 64);
            String fn = parseFaceName(header);
            String base = f.getName().substring(0, f.getName().length() - 4);
            File imgFile = imageFiles.get(base);
            byte[] preview = (imgFile == null) ? null : readFileBytes(imgFile);
            out.add(new DownloadedFace(f.getName(), fn, f.length(), null, f, null, preview, null));
        }
    }

    private ArrayList<DownloadedFace> listDownloadedFaces() {
        ArrayList<DownloadedFace> out = new ArrayList<>();
        java.util.LinkedHashSet<String> seen = new java.util.LinkedHashSet<>();
        String[] nestedBases = new String[]{
                "Documents/CMFx/.Faces/", "Documents/CMFx/.faces/",
                "Downloads/CMFx/.Faces/",
        };
        String[] flatBases = new String[]{
                "Documents/CMFx/Faces/", "Documents/CMFx/faces/",
                "Downloads/CMFx/Faces/",
        };
        java.util.HashSet<String> nestedSet = new java.util.HashSet<>(java.util.Arrays.asList(nestedBases));
        java.util.HashSet<String> flatSet = new java.util.HashSet<>(java.util.Arrays.asList(flatBases));
        if (Build.VERSION.SDK_INT >= 29) {
            Uri root = MediaStore.Files.getContentUri("external");
            String[] proj = new String[]{MediaStore.MediaColumns._ID,
                    MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.SIZE,
                    MediaStore.MediaColumns.RELATIVE_PATH};
            // First collect image companions keyed by base name (without extension).
            java.util.HashMap<String, byte[]> imageMap = new java.util.HashMap<>();
            java.util.ArrayList<long[]> binIds = new java.util.ArrayList<>();
            try (Cursor c = getContentResolver().query(root, proj, null, null,
                    MediaStore.MediaColumns.DISPLAY_NAME)) {
                if (c != null) {
                    int idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID);
                    int nameCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME);
                    int sizeCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE);
                    int relCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH);
                    // First pass: images
                    // We need two passes; re-query is cheap. Collect bins first.
                    java.util.ArrayList<Object[]> bins = new java.util.ArrayList<>();
                    c.moveToPosition(-1);
                    while (c.moveToNext()) {
                        String rel = c.getString(relCol);
                        if (rel == null) continue;
                        String folderRel = rel.endsWith("/") ? rel : rel + "/";
                        boolean isNested = false, isFlat = false;
                        for (String b : nestedBases) if (folderRel.startsWith(b)) { isNested = true; break; }
                        if (!isNested) for (String b : flatBases) if (folderRel.equalsIgnoreCase(b)) { isFlat = true; break; }
                        if (!isNested && !isFlat) continue;
                        String dn = c.getString(nameCol);
                        if (dn == null) continue;
                        String lower = dn.toLowerCase();
                        long id = c.getLong(idCol);
                        Uri uri = ContentUris.withAppendedId(root, id);
                        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png")) {
                            String base = dn.substring(0, dn.lastIndexOf('.'));
                            String key = folderRel + base;
                            if (!imageMap.containsKey(key)) imageMap.put(key, readUriBytes(uri));
                        } else if (lower.endsWith(".bin")) {
                            bins.add(new Object[]{dn, c.getLong(sizeCol), uri, folderRel});
                        }
                    }
                    for (Object[] b : bins) {
                        String dn = (String) b[0];
                        long sz = (Long) b[1];
                        Uri uri = (Uri) b[2];
                        String folderRel = (String) b[3];
                        if (!seen.add(dn)) continue;
                        byte[] header = readUriHeader(uri, 64);
                        String fn = parseFaceName(header);
                        // find companion image: base name = dn without ".bin"
                        String base = dn.substring(0, dn.length() - 4);
                        byte[] preview = imageMap.get(folderRel + base);
                        out.add(new DownloadedFace(dn, fn, sz, uri, null, null, preview, folderRel));
                    }
                }
            } catch (Throwable ignored) {}
        } else {
            String[] legacyDirs = new String[]{"Documents/CMFx/Faces", "Documents/CMFx/faces",
                    "Downloads/CMFx/Faces"};
            for (String d : legacyDirs) {
                File dir = new File(Environment.getExternalStorageDirectory(), d);
                File[] fs = dir.listFiles();
                if (fs == null) continue;
                scanFaceDir(dir, out, seen);
            }
            // Nested .Faces layout (one subfolder per face).
            String[] nestedDirs = new String[]{"Documents/CMFx/.Faces", "Documents/CMFx/.faces",
                    "Downloads/CMFx/.Faces"};
            for (String d : nestedDirs) {
                File baseDir = new File(Environment.getExternalStorageDirectory(), d);
                File[] subs = baseDir.listFiles();
                if (subs == null) continue;
                for (File sub : subs) {
                    if (!sub.isDirectory()) continue;
                    scanFaceDir(sub, out, seen);
                }
            }
        }
        return out;
    }

    private byte[] readUriBytes(Uri uri) {
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) return null;
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toByteArray();
        } catch (Throwable t) { return null; }
    }

    private byte[] readFileBytes(File f) {
        try (InputStream in = new java.io.FileInputStream(f)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toByteArray();
        } catch (Throwable t) { return null; }
    }

    /** Reads only the first N bytes (header) — enough to parse the embedded face name. */
    private byte[] readFileHeader(File f, int len) {
        try (InputStream in = new java.io.FileInputStream(f)) {
            byte[] buf = new byte[len];
            int off = 0, n;
            while (off < len && (n = in.read(buf, off, len - off)) > 0) off += n;
            if (off >= len) return buf;
            byte[] out = new byte[off];
            System.arraycopy(buf, 0, out, 0, off);
            return out;
        } catch (Throwable t) { return null; }
    }

    private byte[] readUriHeader(Uri uri, int len) {
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) return null;
            byte[] buf = new byte[len];
            int off = 0, n;
            while (off < len && (n = in.read(buf, off, len - off)) > 0) off += n;
            if (off >= len) return buf;
            byte[] out = new byte[off];
            System.arraycopy(buf, 0, out, 0, off);
            return out;
        } catch (Throwable t) { return null; }
    }

    /** Loads the full .bin on demand (called only when a face is actually selected). */
    private byte[] loadDownloadedFaceBytes(DownloadedFace df) {
        if (df.bytes != null) return df.bytes;
        return (df.uri != null) ? readUriBytes(df.uri)
                : (df.file != null ? readFileBytes(df.file) : null);
    }

    private static String parseFaceName(byte[] b) {
        if (b == null || b.length < 24) return null;
        return Proto.cstring(b, 8);
    }

    private static String previewExt(String preview) {
        if (preview == null) return ".jpg";
        int dot = preview.lastIndexOf('.');
        if (dot < 0 || dot == preview.length() - 1) return ".jpg";
        return preview.substring(dot);
    }

    private static String guessMime(String preview) {
        String e = previewExt(preview).toLowerCase();
        if (e.equals(".png")) return "image/png";
        if (e.equals(".webp")) return "image/webp";
        return "image/jpeg";
    }

    private CharSequence boldDocHeadings(String text) {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        String[] lines = text.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int start = sb.length();
            sb.append(line);
            if (i < lines.length - 1) sb.append("\n");
            String trimmed = line.trim();
            // Headings = non-empty lines that are not bullet points.
            if (!trimmed.isEmpty() && !trimmed.startsWith("-")) {
                sb.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), start,
                        start + line.length(), SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        return sb;
    }

    private void updateSelectedFaceThumbnail() {
        ui.post(() -> {
            if (fileBytes == null) {
                selectedFaceThumb.setVisibility(View.GONE);
                selectedFaceThumb.setImageBitmap(null);
                return;
            }
            Bitmap bmp = null;
            if (selectedFacePreviewBytes != null) {
                try { bmp = BitmapFactory.decodeByteArray(selectedFacePreviewBytes, 0, selectedFacePreviewBytes.length); } catch (Throwable ignored) {}
            }
            if (bmp == null) bmp = extractPhotoThumbnail(fileBytes);
            if (bmp != null) {
                selectedFaceThumb.setImageBitmap(bmp);
                selectedFaceThumb.setVisibility(View.VISIBLE);
            } else {
                selectedFaceThumb.setVisibility(View.GONE);
            }
        });
    }

    private static Bitmap extractPhotoThumbnail(byte[] b) {
        if (b == null || b.length < 36) return null;
        if ((b[0] & 0xff) != 0x6c || (b[1] & 0xff) != 0x8d
                || (b[2] & 0xff) != 0xc4 || (b[3] & 0xff) != 0xa5) return null;
        int idx = -1;
        for (int i = 0; i + 8 <= b.length; i++) {
            if ((b[i] & 0xff) == 0x04 && (b[i + 1] & 0xff) == 0x38
                    && (b[i + 2] & 0xff) == 0xc4 && (b[i + 3] & 0xff) == 0x21) { idx = i; break; }
        }
        if (idx < 0) return null;
        long payloadLen = ((long) (b[idx + 4] & 0xff))
                | (((long) (b[idx + 5] & 0xff)) << 8)
                | (((long) (b[idx + 6] & 0xff)) << 16)
                | (((long) (b[idx + 7] & 0xff)) << 24);
        if (payloadLen <= 0 || payloadLen > (1 << 20)) return null;
        int srcOff = idx + 8;
        if (srcOff + payloadLen > b.length) return null;
        byte[] src = new byte[(int) payloadLen];
        System.arraycopy(b, srcOff, src, 0, (int) payloadLen);
        int outSize = 270 * 270 * 2;
        byte[] out = lz4Decompress(src, outSize);
        if (out == null) return null;
        Bitmap bmp = Bitmap.createBitmap(270, 270, Bitmap.Config.ARGB_8888);
        int i = 0;
        for (int y = 0; y < 270; y++) {
            for (int x = 0; x < 270; x++) {
                int lo = out[i] & 0xff;
                int hi = out[i + 1] & 0xff;
                i += 2;
                int rgb = (hi << 8) | lo;
                int r = ((rgb >> 11) & 0x1f) * 255 / 31;
                int g = ((rgb >> 5) & 0x3f) * 255 / 63;
                int bl = (rgb & 0x1f) * 255 / 31;
                bmp.setPixel(x, y, Color.rgb(r, g, bl));
            }
        }
        return bmp;
    }

    private static byte[] lz4Decompress(byte[] src, int dstSize) {
        byte[] dst = new byte[dstSize];
        int si = 0, di = 0;
        int srcEnd = src.length;
        try {
            while (si < srcEnd && di < dstSize) {
                int token = src[si++] & 0xff;
                int litLen = token >>> 4;
                if (litLen == 15) {
                    int bb;
                    do {
                        if (si >= srcEnd) return null;
                        bb = src[si++] & 0xff;
                        litLen += bb;
                    } while (bb == 255);
                }
                if (litLen > 0) {
                    if (si + litLen > srcEnd || di + litLen > dstSize) return null;
                    System.arraycopy(src, si, dst, di, litLen);
                    si += litLen;
                    di += litLen;
                }
                if (si >= srcEnd) break;
                if (si + 2 > srcEnd) return null;
                int offset = (src[si] & 0xff) | ((src[si + 1] & 0xff) << 8);
                si += 2;
                if (offset == 0 || offset > di) return null;
                int matchLen = (token & 0x0f) + 4;
                if ((token & 0x0f) == 15) {
                    int bb;
                    do {
                        if (si >= srcEnd) return null;
                        bb = src[si++] & 0xff;
                        matchLen += bb;
                    } while (bb == 255);
                }
                if (di + matchLen > dstSize) return null;
                for (int k = 0; k < matchLen; k++) {
                    dst[di] = dst[di - offset];
                    di++;
                }
            }
        } catch (Throwable t) { return null; }
        if (di != dstSize) return null;
        return dst;
    }

    private void deleteDownloadedFace(DownloadedFace df) {
        try {
            if (df.uri != null && Build.VERSION.SDK_INT >= 29) {
                String folder = df.folderRel;
                if (folder == null || folder.isEmpty()) {
                    // derive from uri
                    try (Cursor c = getContentResolver().query(df.uri,
                            new String[]{MediaStore.MediaColumns.RELATIVE_PATH}, null, null, null)) {
                        if (c != null && c.moveToFirst()) folder = c.getString(0);
                    } catch (Throwable ignored) {}
                }
                if (folder != null && !folder.isEmpty()) {
                    if (!folder.endsWith("/")) folder += "/";
                    Uri root = MediaStore.Files.getContentUri("external");
                    String[] proj = new String[]{MediaStore.MediaColumns._ID};
                    try (Cursor c = getContentResolver().query(root, proj,
                            MediaStore.MediaColumns.RELATIVE_PATH + "=?", new String[]{folder}, null)) {
                        if (c != null) {
                            int idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID);
                            java.util.ArrayList<Uri> toDelete = new java.util.ArrayList<>();
                            while (c.moveToNext()) {
                                toDelete.add(ContentUris.withAppendedId(root, c.getLong(idCol)));
                            }
                            for (Uri u : toDelete) {
                                try { getContentResolver().delete(u, null, null); } catch (Throwable ignored) {}
                            }
                        }
                    }
                } else {
                    try { getContentResolver().delete(df.uri, null, null); } catch (Throwable ignored) {}
                }
            } else if (df.file != null) {
                File parent = df.file.getParentFile();
                if (parent != null && parent.isDirectory()) {
                    File[] fs = parent.listFiles();
                    if (fs != null) for (File f : fs) { try { f.delete(); } catch (Throwable ignored) {} }
                    try { parent.delete(); } catch (Throwable ignored) {}
                } else {
                    try { df.file.delete(); } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable t) {
            android.util.Log.w("CMFx", "delete face failed", t);
        }
    }

    private void browseDownloadedFaces() {
        if (busy.get()) return;
        final LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundColor(FMC_BG);
        int p = dp(12);
        box.setPadding(p, p, p, p);
        final TextView loading = new TextView(this);
        loading.setText("Loading downloaded faces…");
        loading.setTextColor(MUTED);
        loading.setPadding(dp(6), dp(18), dp(6), dp(18));
        box.addView(loading);
        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(box).setNegativeButton("Close", null).create();
        dialog.show();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawable(round(FMC_BG, 18));
        worker.execute(() -> {
            final ArrayList<DownloadedFace> faces = listDownloadedFaces();
            ui.post(() -> {
                box.removeView(loading);
                if (faces.isEmpty()) {
                    TextView none = new TextView(this);
                    none.setText("No downloaded faces found in Documents/CMFx/.Faces.");
                    none.setTextColor(MUTED);
                    none.setPadding(dp(6), dp(18), dp(6), dp(18));
                    box.addView(none);
                    return;
                }
                for (final DownloadedFace df : faces) {
                    LinearLayout row = new LinearLayout(this);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    row.setGravity(Gravity.CENTER_VERTICAL);
                    row.setPadding(dp(6), dp(6), dp(6), dp(6));
                    row.setBackground(round(FMC_CARD, 12));
                    LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, -2);
                    rowLp.setMargins(0, 0, 0, dp(6));
                    ImageView thumb = new ImageView(this);
                    Bitmap bmp = null;
                    if (df.previewBytes != null) {
                        try { bmp = BitmapFactory.decodeByteArray(df.previewBytes, 0, df.previewBytes.length); } catch (Throwable ignored) {}
                    }
                    if (bmp == null && df.bytes != null) bmp = extractPhotoThumbnail(df.bytes);
                    if (bmp != null) thumb.setImageBitmap(bmp);
                    else thumb.setImageResource(android.R.drawable.ic_menu_gallery);
                    thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    LinearLayout.LayoutParams thumbLp = new LinearLayout.LayoutParams(dp(64), dp(64));
                    thumbLp.setMargins(0, 0, dp(10), 0);
                    row.addView(thumb, thumbLp);
                    LinearLayout col = new LinearLayout(this);
                    col.setOrientation(LinearLayout.VERTICAL);
                    col.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
                    TextView nameTv = new TextView(this);
                    String label = (df.faceName != null && !df.faceName.isEmpty()) ? df.faceName : df.displayName;
                    nameTv.setText(label);
                    nameTv.setTextColor(Color.WHITE);
                    nameTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                    nameTv.setTypeface(geistBold());
                    col.addView(nameTv);
                    TextView metaTv = new TextView(this);
                    metaTv.setText(df.displayName + "  ·  " + (df.size / 1024) + " KB");
                    metaTv.setTextColor(FMC_MUTED);
                    metaTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
                    col.addView(metaTv);
                    row.addView(col);
                    Button inst = new Button(this);
                    inst.setText("Select Face");
                    inst.setAllCaps(false);
                    inst.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                    inst.setTypeface(geistBold());
                    inst.setTextColor(Color.WHITE);
                    GradientDrawable instBg = new GradientDrawable();
                    instBg.setColor(FMC_CARD);
                    instBg.setCornerRadius(dp(12));
                    instBg.setStroke(dp(1), Color.WHITE);
                    inst.setBackground(instBg);
                    inst.setStateListAnimator(null);
                    inst.setPadding(dp(14), dp(6), dp(14), dp(6));
                    inst.setOnClickListener(v -> {
                        dialog.dismiss();
                        status("Loading " + ((df.faceName != null && !df.faceName.isEmpty())
                                ? df.faceName : df.displayName) + "…");
                        worker.execute(() -> {
                            byte[] full = loadDownloadedFaceBytes(df);
                            if (full == null) {
                                ui.post(() -> fail("Face storage unavailable",
                                        new IOException("could not read " + df.displayName)));
                                return;
                            }
                            fileBytes = full;
                            fileName = (df.faceName != null && !df.faceName.isEmpty())
                                    ? df.faceName + ".bin" : df.displayName;
                            faceSeed = null;
                            selectedFacePreviewBytes = df.previewBytes;
                            selectedDownloadedFace = df.displayName;
                            final int len = full.length;
                            ui.post(() -> {
                                fileView.setText(fileName + "  ·  " + len + " bytes");
                                updateSelectedFaceThumbnail();
                                status("Selected " + ((df.faceName != null && !df.faceName.isEmpty())
                                        ? df.faceName : df.displayName) + " — tap Upload to install");
                            });
                        });
                    });
                    row.addView(inst);
                    // delete button
                    ImageButton del = new ImageButton(this);
                    del.setImageResource(android.R.drawable.ic_menu_delete);
                    del.setContentDescription("Delete face");
                    del.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                    GradientDrawable delBg = new GradientDrawable();
                    delBg.setColor(FMC_CARD);
                    delBg.setCornerRadius(dp(12));
                    delBg.setStroke(dp(1), Color.WHITE);
                    del.setBackground(delBg);
                    del.setStateListAnimator(null);
                    int delSize = dp(40);
                    LinearLayout.LayoutParams delLp = new LinearLayout.LayoutParams(delSize, delSize);
                    delLp.setMargins(dp(6), 0, 0, 0);
                    del.setLayoutParams(delLp);
                    del.setPadding(dp(8), dp(8), dp(8), dp(8));
                    del.setOnClickListener(v -> {
                        // clear selection if this face was selected
                        if (selectedDownloadedFace != null && selectedDownloadedFace.equals(df.displayName)) {
                            fileBytes = null;
                            fileName = null;
                            faceSeed = null;
                            selectedFacePreviewBytes = null;
                            selectedDownloadedFace = null;
                            ui.post(() -> fileView.setText("No file selected"));
                            updateSelectedFaceThumbnail();
                        }
                        deleteDownloadedFace(df);
                        // remove the row from the dialog
                        try { box.removeView(row); } catch (Throwable ignored) {}
                        // if no rows left, show empty message
                        boolean anyBin = false;
                        for (int i = 0; i < box.getChildCount(); i++) {
                            if (box.getChildAt(i) instanceof LinearLayout) { anyBin = true; break; }
                        }
                        if (!anyBin) {
                            TextView none = new TextView(this);
                            none.setText("No downloaded faces found in Documents/CMFx/.Faces.");
                            none.setTextColor(MUTED);
                            none.setPadding(dp(6), dp(18), dp(6), dp(18));
                            box.addView(none);
                        }
                        status("Deleted " + ((df.faceName != null && !df.faceName.isEmpty())
                                ? df.faceName : df.displayName));
                    });
                    row.addView(del);
                    box.addView(row, rowLp);
                }
            });
        });
    }

    private ArrayAdapter<String> fmcSpinnerAdapter(String[] values) {
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, values) {
            @Override public View getView(int position, View convertView, android.view.ViewGroup parent) {
                TextView view = (TextView) super.getView(position, convertView, parent);
                view.setTextColor(Color.WHITE);
                view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                view.setTypeface(geist());
                view.setGravity(Gravity.CENTER_VERTICAL);
                view.setPadding(dp(10), 0, dp(4), 0);
                return view;
            }

            @Override public View getDropDownView(int position, View convertView, android.view.ViewGroup parent) {
                TextView view = (TextView) super.getDropDownView(position, convertView, parent);
                view.setTextColor(Color.WHITE);
                view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                view.setTypeface(geist());
                view.setGravity(Gravity.CENTER_VERTICAL);
                view.setPadding(dp(14), dp(10), dp(14), dp(10));
                view.setBackgroundColor(FMC_CARD);
                return view;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return adapter;
    }

    private void styleFmcSpinner(Spinner spinner) {
        spinner.setBackground(round(FMC_CARD, 12));
        spinner.setPadding(dp(4), 0, dp(8), 0);
        spinner.setMinimumHeight(dp(46));
        spinner.setDropDownVerticalOffset(dp(4));
        if (Build.VERSION.SDK_INT >= 21) spinner.setPopupBackgroundDrawable(round(FMC_CARD, 12));
    }

    private FaceTile createFaceTile() {
        FaceTile t = new FaceTile();
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setPadding(dp(8), dp(8), dp(8), dp(10));
        tile.setBackground(round(FMC_CARD, 12));
        tile.setElevation(0);
        ImageView thumb = new RoundFaceImage(this);
        thumb.setImageResource(android.R.drawable.ic_menu_gallery);
        thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
        thumb.setBackground(round(Color.BLACK, 100));
        tile.addView(thumb, new LinearLayout.LayoutParams(-1, dp(132)));
        TextView name = new TextView(this);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        name.setTypeface(notoSerif());
        name.setTextColor(Color.WHITE);
        name.setMaxLines(2);
        name.setPadding(dp(2), dp(8), dp(2), 0);
        tile.addView(name, new LinearLayout.LayoutParams(-1, -2));
        TextView meta = new TextView(this);
        meta.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        meta.setTextColor(FMC_MUTED);
        meta.setMaxLines(1);
        meta.setPadding(dp(2), dp(4), dp(2), dp(2));
        tile.addView(meta, new LinearLayout.LayoutParams(-1, -2));
        Button get = new Button(this);
        get.setText("Download");
        styleButton(get, true);
        get.setTextColor(Color.WHITE);
        get.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        get.setBackground(round(FMC_TAG, 7));
        get.setMinHeight(dp(42));
        tile.addView(get, new LinearLayout.LayoutParams(-1, -2));
        t.root = tile; t.thumb = thumb; t.name = name; t.meta = meta; t.download = get;
        return t;
    }

    private void bindFaceTile(FaceTile t, Face face, AlertDialog dialog, int gen, int thumbSize) {
        t.root.setTag(face.id);
        t.name.setText(face.name);
        t.meta.setText("↓ " + face.downloads + "   ♥ " + face.likes
                + (face.device.isEmpty() ? "" : "   ·   " + face.device));
        final Face f = face;
        t.download.setOnClickListener(v -> { dialog.dismiss(); downloadFace(f); });

        Bitmap cached = thumbCache.get(face.id);
        if (cached != null) {
            t.thumb.setImageBitmap(cached);
        } else {
            t.thumb.setImageResource(android.R.drawable.ic_menu_gallery);
            imagePool.execute(() -> {
                try {
                    byte[] image = httpBytes("https://fmc.freethinkel.dev/api/files/pbc_984338259/"
                            + face.id + "/" + face.preview);
                    Bitmap bitmap = decodeSampled(image, thumbSize, thumbSize);
                    if (bitmap != null) thumbCache.put(face.id, bitmap);
                    final Bitmap bmp = bitmap;
                    // #6: skip stale results after a search/sort rebuild.
                    if (bmp != null && gen == facesGeneration && face.id.equals(t.root.getTag())) {
                        ui.post(() -> {
                            if (gen == facesGeneration && face.id.equals(t.root.getTag())) {
                                t.thumb.setImageBitmap(bmp);
                            }
                        });
                    }
                } catch (Throwable ignored) {}
            });
        }
    }

    private void populateFaces(LinearLayout box, EditText search, Spinner sort, ArrayList<Face> faces,
                               AlertDialog dialog) {
        final int gen = ++facesGeneration;
        String q = search.getText().toString().trim().toLowerCase();
        ArrayList<Face> ordered = new ArrayList<>();
        for (Face f : faces) if (q.isEmpty() || f.name.toLowerCase().contains(q)) ordered.add(f);
        int mode = sort.getSelectedItemPosition();
        java.util.Collections.sort(ordered, (a, b) -> mode == 1
                ? Integer.compare(b.downloads, a.downloads)
                : mode == 2 ? Integer.compare(b.likes, a.likes)
                : b.created.compareTo(a.created));
        int limit = listLimit(box);
        int shown = Math.min(limit, ordered.size());
        int neededRows = (shown + 1) / 2;
        int thumbSize = dp(132);

        // #8: grow the reusable pools only when necessary; never recreate existing views.
        while (fmcRows.size() < neededRows) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            fmcRows.add(row);
        }
        while (fmcTiles.size() < shown) fmcTiles.add(createFaceTile());

        for (int i = 0; i < shown; i++) bindFaceTile(fmcTiles.get(i), ordered.get(i), dialog, gen, thumbSize);

        while (box.getChildCount() > 0) box.removeViewAt(0);
        for (int r = 0; r < neededRows; r++) {
            LinearLayout row = fmcRows.get(r);
            while (row.getChildCount() > 0) row.removeViewAt(0);
            for (int c = 0; c < 2; c++) {
                int idx = r * 2 + c;
                if (idx >= shown) break;
                LinearLayout.LayoutParams tileParams = new LinearLayout.LayoutParams(0, -2, 1f);
                tileParams.setMargins(dp(4), dp(6), dp(4), dp(6));
                row.addView(fmcTiles.get(idx).root, tileParams);
            }
            box.addView(row, new LinearLayout.LayoutParams(-1, -2));
        }
        if (shown == 0) {
            TextView none = new TextView(this);
            none.setText("No matching faces");
            none.setTextColor(Color.LTGRAY);
            none.setPadding(0, dp(16), 0, dp(16));
            box.addView(none);
        } else if (ordered.size() > shown) {
            Button more = new Button(this);
            more.setText("Load more faces (" + (ordered.size() - shown) + " remaining)");
            styleButton(more, false);
            more.setTextColor(Color.WHITE);
            more.setBackground(round(FMC_TAG, 10));
            more.setOnClickListener(v -> {
                box.setTag(limit + 100);
                populateFaces(box, search, sort, faces, dialog);
            });
            box.addView(more);
        }
    }

    private int listLimit(LinearLayout list) {
        Object tag = list.getTag();
        return tag instanceof Integer ? (Integer) tag : 100;
    }

    /** Returns the cached catalog when fresh, otherwise fetches and caches it. */
    private ArrayList<Face> getCachedFaces(boolean force) throws Exception {
        long now = System.currentTimeMillis();
        if (!force && cachedFaces != null && (now - cachedFacesAt) < CATALOG_TTL_MS) {
            return cachedFaces;
        }
        ArrayList<Face> faces = fetchFaces();
        cachedFaces = faces;
        cachedFacesAt = now;
        return faces;
    }

    /** Decodes a bitmap downsampled to roughly the target size (avoids full-res decode). */
    private static Bitmap decodeSampled(byte[] data, int targetW, int targetH) {
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, opts);
        int sample = 1;
        if (opts.outHeight > targetH || opts.outWidth > targetW) {
            int halfH = opts.outHeight / 2, halfW = opts.outWidth / 2;
            while (halfH / sample >= targetH && halfW / sample >= targetW) sample *= 2;
        }
        opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        opts.inPreferredConfig = Bitmap.Config.RGB_565;
        return BitmapFactory.decodeByteArray(data, 0, data.length, opts);
    }

    private ArrayList<Face> fetchFaces() throws Exception {
        ArrayList<Face> out = new ArrayList<>();
        for (int page = 1; page <= 100; page++) {
            String url = FMC_API.replace("page=1", "page=" + page).replace("perPage=100", "perPage=1000");
            JSONObject root = new JSONObject(httpText(url));
            JSONArray items = root.optJSONArray("items");
            if (items == null || items.length() == 0) break;
            for (int i = 0; i < items.length(); i++) {
                JSONObject o = items.getJSONObject(i);
            String device = o.optString("device", "");
            if (!device.isEmpty() && !device.equals("watch_pro_2")
                    && !device.equals("watch_3_pro")) continue;
            out.add(new Face(o.getString("id"), o.optString("name", "Unnamed face"), device,
                    o.optString("preview", ""), o.optString("description", ""),
                    o.optInt("downloads", 0), o.optString("created", ""), 0));
            }
            if (items.length() < 1000) break;
        }
        JSONObject likes = new JSONObject(httpText("https://fmc.freethinkel.dev/api/collections/likes/records"
                + "?page=1&perPage=1000&skipTotal=1"));
        java.util.HashMap<String, Integer> counts = new java.util.HashMap<>();
        JSONArray liked = likes.optJSONArray("items");
        if (liked != null) for (int i = 0; i < liked.length(); i++) {
            JSONObject l = liked.getJSONObject(i);
            String id = l.optString("watchface", "");
            counts.put(id, counts.containsKey(id) ? counts.get(id) + 1 : 1);
        }
        for (int i = 0; i < out.size(); i++) {
            Face f = out.get(i); Integer n = counts.get(f.id);
            out.set(i, new Face(f.id, f.name, f.device, f.preview, f.description, f.downloads,
                    f.created, n == null ? 0 : n));
        }
        return out;
    }

    private void downloadFace(Face face) {
        if (busy.getAndSet(true)) return;
        status("Downloading " + face.name + "…");
        setBusy(true);
        worker.execute(() -> {
            try {
                ui.post(() -> {
                    progress.setProgress(0);
                    downloadProgressText.setText("Downloading 0%  ·  " + face.name);
                });
                byte[] bytes = httpBytesWithProgress(
                        "https://fmc.freethinkel.dev/api/wf/" + face.id + "/download",
                        pct -> ui.post(() -> {
                            progress.setProgress(pct);
                            downloadProgressText.setText("Downloading " + pct + "%  ·  " + face.name);
                        }));
                fileBytes = bytes;
                fileName = face.name + ".bin";
                faceSeed = face.id;
                String faceFolder = "Documents/CMFx/.Faces/" + safeStorageName(face.name);
                try {
                    writePublicFileAt(faceFolder, face.id + "-" + fileName,
                            "application/octet-stream", bytes);
                    logLine("Saved face file: " + faceFolder + "/" + face.id + "-" + fileName);
                } catch (Throwable e) {
                    logLine("Face storage unavailable: " + msg(e));
                }
                // Also fetch the FMC preview image so the downloaded-face browser can show a thumbnail.
                byte[] previewBytes = null;
                if (face.preview != null && !face.preview.isEmpty()) {
                    try {
                        byte[] img = httpBytes("https://fmc.freethinkel.dev/api/files/pbc_984338259/"
                                + face.id + "/" + face.preview);
                        previewBytes = img;
                        String imgName = face.id + "-" + face.name + previewExt(face.preview);
                        writePublicFileAt(faceFolder, imgName, guessMime(face.preview), img);
                        logLine("Saved face preview: " + faceFolder + "/" + imgName);
                    } catch (Throwable e) {
                        logLine("Face preview unavailable: " + msg(e));
                    }
                }
                selectedFacePreviewBytes = previewBytes;
                logLine("Downloaded FMC face: " + face.name + " (" + bytes.length + " bytes)");
                ui.post(() -> fileView.setText(fileName + "  ·  " + bytes.length + " bytes"));
                updateSelectedFaceThumbnail();
                status("Face ready — tap Upload to install");
                ui.post(() -> downloadProgressText.setText("Downloaded 100%  ·  ready to install"));
            } catch (Throwable t) {
                fail("FMC install failed", t);
            } finally {
                setBusy(false);
            }
        });
    }

    private String httpText(String address) throws Exception {
        return new String(httpBytes(address), StandardCharsets.UTF_8);
    }

    private byte[] httpBytes(String address) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(address).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        c.setRequestProperty("Accept", "application/json, application/octet-stream");
        try {
            if (c.getResponseCode() < 200 || c.getResponseCode() >= 300)
                throw new IOException("HTTP " + c.getResponseCode());
            try (InputStream in = c.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buf = new byte[65536]; int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                return out.toByteArray();
            }
        } finally { c.disconnect(); }
    }

    private interface DownloadProgress { void update(int percent); }

    private byte[] httpBytesWithProgress(String address, DownloadProgress callback) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(address).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        c.setRequestProperty("Accept", "application/octet-stream");
        try {
            if (c.getResponseCode() < 200 || c.getResponseCode() >= 300)
                throw new IOException("HTTP " + c.getResponseCode());
            int total = c.getContentLength();
            int read = 0, last = -1;
            try (InputStream in = c.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buf = new byte[65536]; int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    read += n;
                    if (total > 0) {
                        int pct = Math.min(99, (int) ((read * 100L) / total));
                        if (pct != last) { last = pct; callback.update(pct); }
                    }
                }
                callback.update(100);
                return out.toByteArray();
            }
        } finally { c.disconnect(); }
    }

    private static String msg(Throwable t) {
        return t.getMessage() == null ? t.toString() : t.getMessage();
    }

    // ---- upload -------------------------------------------------------

    private void onUploadClicked() {
        if (busy.getAndSet(true)) return;
        final Watch w = activeWatch();
        final Ble b = ble;
        final byte[] bytes = fileBytes;

        ui.post(() -> {
            connectBtn.setEnabled(false);
            uploadBtn.setEnabled(false);
            progress.setProgress(0);
        });

        if (w == null || b == null || !b.isConnected()) {
            setBusy(false);
            fail("Not connected", null);
            return;
        }
        if (bytes == null) {
            setBusy(false);
            fail("No file selected", null);
            return;
        }

        status("Uploading…");
        acquireWakeLock();
        final int replaceId = parseInt(replaceIdView.getText().toString(), 0);
        worker.execute(() -> {
            try {
                if (replaceId > 0) {
                    logLine("Replace requested for dial id " + replaceId + " (experimental)");
                    w.upload(bytes, replaceId, faceSeed);
                    status("Upload complete");
                } else {
                    String label = fileName == null ? "face" : fileName.replace(".bin", "");
                    installFace(w, bytes, faceSeed, label);
                }
            } catch (Watch.AlreadyInstalledException e) {
                logLine("Already installed: " + e.getMessage());
                status(e.getMessage());
                ui.post(() -> Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show());
            } catch (Throwable t) {
                fail("Upload failed", t);
            } finally {
                releaseWakeLock();
                setBusy(false);
            }
        });
    }

    private void acquireWakeLock() {
        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm == null) return;
            if (wakeLock == null) {
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "CMFx:upload");
                wakeLock.setReferenceCounted(false);
            }
            wakeLock.acquire(15 * 60 * 1000L);
        } catch (Throwable ignored) {}
    }

    private void releaseWakeLock() {
        try {
            if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        } catch (Throwable ignored) {}
    }
}
