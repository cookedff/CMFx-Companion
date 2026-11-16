# CMFx successful versions

Only builds that complete the release checks are recorded here. APK copies are
kept in `releases/` so a known-good release can always be restored.

## 1.0.0 — standalone uploader baseline

- **Version code:** 10000
- **APK:** `releases/CMFx-v1.0.0.apk`
- **Status:** successful hardware-verified baseline
- **Features:** BLE pairing/authentication and append-only CMF Watch Pro 2 face upload
- **Notes:** Native app package is `dev.cmfx`; Nothing X remains installed separately.

## 1.1.0 — Native FMC face browser

- **Version code:** 10001
- **APK:** `releases/CMFx-v1.1.0.apk`
- **SHA256:** `35009f51d68c677c42675f4aaacf5d9de779230811c65c7717d8b62d1b1cf3e2`
- **Status:** successful release build and device install
- **Features:** Native FMC catalog search, compatible-face filtering, direct download, and direct upload to the watch.
- **Notes:** Uses FMC's PocketBase JSON API; no external browser or file picker is needed for catalog faces.

## 1.2.0 — Notification forwarding companion

- **Version code:** 10002
- **APK:** `releases/CMFx-v1.2.0.apk`
- **SHA256:** `5817b0a04bcefcc873486a7b95bdf13b6e34669addef7a746f070bc98a19fe8c`
- **Status:** successful release build and device install
- **Features:** Foreground BLE companion session and Android notification forwarding for generic messages and WhatsApp.

## 1.2.1 — Notification access setup

- **Version code:** 10003
- **APK:** `releases/CMFx-v1.2.1.apk`
- **SHA256:** `9321e0fc00bc151eef9fd81031e520049dd265a2c02b23a70ef080b4e7d9cc01`
- **Status:** successful release build and device install
- **Features:** One-tap button to open Android Notification Access settings.

## 1.3.0 — FMC thumbnails and sorting

- **Version code:** 10004
- **APK:** `releases/CMFx-v1.3.0.apk`
- **SHA256:** `fc31d81753c536167f8c4cedd49efad88680622641c931460a365887f1b61301`
- **Status:** successful release build and device install
- **Features:** FMC thumbnails, descriptions, download/like counts, and Newest / Most downloads / Most liked filters.

## 1.3.1 — Scrollable full FMC catalog

- **Version code:** 10005
- **APK:** `releases/CMFx-v1.3.1.apk`
- **SHA256:** `4269c5fbe0db17e37cb19a15a9ebaa862a978f01b6166a09ff16d6d1bb5ddbcf`
- **Status:** successful release build and device install
- **Features:** Scrollable results pane, full catalog paging, and incremental “Load more faces”.

## 1.3.2 — FMC two-column tile browser

- **Version code:** 10006
- **APK:** `releases/CMFx-v1.3.2.apk`
- **SHA256:** `70b8ad5cf4109a7f0cf7690f4abd4973c86264ad3f5ddd70a8e26b75f0607883`
- **Status:** successful release build and device install
- **Features:** Two-face-per-row FMC tiles with thumbnail, metadata, description, and install action.

## 1.3.3 — Health dashboard and data fetch

- **Version code:** 10007
- **APK:** `releases/CMFx-v1.3.3.apk`
- **SHA256:** `9bf1f8113b3ce6e3b81e9427b08c184378047c08d9958b7926b17120df02dc27`
- **Status:** successful release build and device install
- **Features:** Health sync command sequence (`ACTIVITY_FETCH_1` → ack → `ACTIVITY_FETCH_2`), battery read, and workout-summary fetch behind the Health sync button.

## 1.3.4 — Music and companion controls

- **Version code:** 10008
- **APK:** `releases/CMFx-v1.3.4.apk`
- **SHA256:** `c523c32e083c68841374a3a3b31018c6069375dc5d1facd2fab70b1f5da21fcf`
- **Status:** successful release build and device install
- **Features:** Watch-side music state push (`MUSIC_INFO_SET`), find-watch, and do-not-disturb companion commands.

## 1.3.5 — Persistent companion session

- **Version code:** 10009
- **APK:** `releases/CMFx-v1.3.5.apk`
- **SHA256:** `ba1f1231ccb7e31192471994226d8a71be5398cc39cd30ced7d1e7fdae6a3817`
- **Status:** successful release build and device install
- **Features:** Foreground service owns the authenticated BLE session and retries connection; activity can adopt it. Rebuild with call-icon handling and health status display included.

## 1.3.6 — Calls and health dashboard

- **Version code:** 10010
- **APK:** `releases/CMFx-v1.3.6.apk`
- **SHA256:** `c89de8d145d1732179978d8b58ed79d1505b065fac994a7677ceb81a82834d75`
- **Status:** successful release build; device install and partial hardware verification
- **Features:** Incoming call notifications use the watch call icon; health sync result and battery level are surfaced in the UI.
- **Fixes:** Health/notify button fields were shadowed by locals, leaving them null and crashing on tap; `setBusy()` now re-enables the Health button after a sync.
- **Verified on hardware:** connect + activity fetch sequence (`ACTIVITY_FETCH_1` → ack → `ACTIVITY_FETCH_2` → ack) without crash.
- **Known issue:** battery read timed out (watch only emits `BATTERY` notifications); fixed in 1.3.7.

## 1.3.7 — Passive battery + real health dashboard data

- **Version code:** 10011
- **APK:** `releases/CMFx-v1.3.7.apk`
- **SHA256:** `770dbece34d66b527da369def5898a398c92aaba53ce87691495966e9dd8ac8e`
- **Status:** successful build (superseded by 1.3.9 before verification)
- **Features:**
  - Battery is treated as the watch's passive `BATTERY (0x005c/0x0001)` notification; a missing reply no longer fails the sync.
  - Charging state parsed from the battery payload.
  - Sync drains the full data burst until the finish ack and aggregates activity, heart rate, SpO2, stress, sleep, and workout summaries.
  - `build.ps1` injects `version.json` values into the manifest so APK versions cannot drift.

## 1.3.8 — Sync diagnostics + battery trigger payload

- **Version code:** 10012
- **APK:** `releases/CMFx-v1.3.8.apk`
- **SHA256:** `df56ff0e6178544f7b967761cd3962a295b2d7bf3ff5c53d262615428f2a2c1a`
- **Status:** successful build (superseded by 1.3.9)
- **Fixes:** service logs are no longer no-ops; queued battery notifications survive the sync reset.

## 1.3.9 — Single-owner companion session

- **Version code:** 10013
- **APK:** `releases/CMFx-v1.3.9.apk`
- **SHA256:** `cf4c741c9df6f5fd792d69254c9293d574f430787b3148d7eeed4a8b155f60ea`
- **Status:** successful build and device install
- **Fixes:** the activity and the foreground service no longer open competing GATT connections; the activity waits for and adopts the service-owned session, and no longer closes it on exit.
- **Hardware-verified:** single connection, adopted session.

## 1.3.10 — Post-auth time sync + battery

- **Version code:** 10014
- **APK:** `releases/CMFx-v1.3.10.apk`
- **SHA256:** `dc794179ec6266efaf5dd985159a20ad7ca663f3434f5b66f5f5e9c30fcf2bd9`
- **Status:** successful build and device install
- **Features:** sends `TIME` after authentication, mirroring the reference client's phase 2. The watch begins reporting battery immediately afterwards.
- **Hardware-verified:** `BATTERY` notification arrives right after `TIME`; firmware and serial queries reply.

## 1.3.11 — Dual-channel session initialization

- **Version code:** 10015
- **APK:** `releases/CMFx-v1.3.11.apk`
- **SHA256:** `9e50a84814b6e5acdafc6963f627f7b6495842ae76f9ccfb97b519d609969a2c`
- **Status:** successful build and device install
- **Features:** writes time/firmware/serial queries to both GATT channels like the reference client; battery notification logging.

## 1.3.12 — Health dashboard card + background data capture

- **Version code:** 10016
- **APK:** `releases/CMFx-v1.3.12.apk`
- **SHA256:** `63cc5e0b40e30eb5962cb844df60e215551c92c1617d49574450f532d98bb7ac`
- **Status:** successful build and device install
- **Features:** multi-line dashboard (steps, distance, calories, heart rate, SpO2, stress, sleep, workouts, battery); health frames are captured even while awaiting sync acks.
- **Hardware-verified:** `30,260 steps · 21.1 km · 1,609 kcal · SpO2 98% · stress 39 · battery 50%`.

## 1.3.13 — Persistent dashboard snapshot

- **Version code:** 10017
- **APK:** `releases/CMFx-v1.3.13.apk`
- **SHA256:** `d10957b72907168ad56a43eaa9a6187c9a7d23f5441511f7ab8b3d043ecfdfd3`
- **Status:** successful build and device install
- **Features:** a shared health snapshot survives reconnects and activity restarts, and is shown immediately on launch.

## 1.3.14 — Companion controls + notification logging

- **Version code:** 10018
- **APK:** `releases/CMFx-v1.3.14.apk`
- **SHA256:** `5dc050cbda37e2ba68096976dc104f2bdf756076465e3a7f851434ec11bda163`
- **Status:** successful build and hardware verification
- **Features:** Find watch / DND / Send music buttons; music state is read from the active media session; notification bridge and forwarding failures are logged.
- **Hardware-verified:** test notification parsed and forwarded; `FIND_WATCH` and `DO_NOT_DISTURB` sent; music info pushed with live metadata.

## 1.4.0 — Two-way companion events

- **Version code:** 10019
- **APK:** `releases/CMFx-v1.4.0.apk`
- **SHA256:** `ac2b48716dd80ed41ce27d56b7f901ff21e1750467c0fade00088976db28cabf`
- **Status:** successful build and hardware verification
- **Features:**
  - Continuous frame listener: watch→phone `FIND_PHONE` starts/stops the ringtone.
  - Watch media buttons control the active media session (pause/play/next/previous/volume).
  - Battery notifications received outside a sync update the dashboard immediately.
  - `FIND_WATCH` now uses the reference client's payload-less form.
- **Verified on hardware:** watch media buttons (`0x0001`, `0x0101`) handled; notifications forwarded; dashboard and battery update.

## Planned companion milestones

- Slot detector and explicit replace/append choice, only where the watch protocol permits it.
- Alarms (`0x0063`), contacts (`0x00d5`), and weather (`0x906b` + `0x0066`) editors and payload writers.
- Canned responses / quick replies (not implemented by the reference client).
- Identify `0x0155/0x0001` and `0xffff/0xa055` notifications seen on current watch firmware.
- Persist the dashboard snapshot across process death (currently survives reconnects only).

## 1.5.0 — Alarms, contacts, weather, dial-slot probe

- **Version code:** 10020
- **APK:** `releases/CMFx-v1.5.0.apk`
- **SHA256:** `33f9982b87699843af48dfa1e5a6bac118f3e13d437979771eed6c1e8476d356`
- **Status:** hardware-verified build
- **Changes:** Added Geist Sans and Noto Serif font assets. App UI uses Geist,
  while CMF.X/face titles use Noto Serif. FMC face downloads now report live
  percentage progress in the Faces tab.


## 1.5.1 — Big-endian alarm sync, auth key logging

- **Version code:** 10021
- **APK:** `releases/CMFx-v1.5.1.apk`
- **SHA256:** `ec69c88611a79c711069fd0855e73438f9fb58d57393395daf5d93bedff8c777`
- **Status:** successful build


## 1.5.2 - Stable pairing key (Gadgetbridge-compatible auth key)

- **Version code:** 10022
- **APK:** `releases/CMFx-v1.5.2.apk`
- **SHA256:** `7757f1103b91354ff800fcd8c911ccfaef80d0009d27e3aa98d9be386187555c`
- **Status:** hardware-verified (pairing + stored-key fast path)
- **Notes:** Stores K1 (SHA-256 of both nonces + watch secret) after the first
  pairing and reuses it instead of re-pairing. K1 is exactly what Gadgetbridge
  stores as its `authkey`, so the two apps can share one key. Rotating K1 was
  what caused Gadgetbridge's "Auth Key error".


## 1.5.3 — Auth key check + Gadgetbridge guidance

- **Version code:** 10023
- **APK:** `releases/CMFx-v1.5.3.apk`
- **SHA256:** `a792c105bb4c0b868b0435b3ddcbe44fd4578bcb513dc4b7f4991b1776a3c356`
- **Status:** successful build


## 1.5.4 - Auth key check works before the service starts

- **Version code:** 10024
- **APK:** `releases/CMFx-v1.5.4.apk`
- **SHA256:** `96cef2e00ad46e500ea494c9397cd587bed9cc1434a19b0dc74269db99b4fbfb`
- **Status:** hardware-verified UI
- **Features:** "Check auth key (Gadgetbridge)" dialog shows the stored K1,
  a VERIFIED / FRESHLY PAIRED / STORED status, a "Test now" action that
  connects and re-checks, and the vendor-app conflict warning. Key store is
  initialized from the activity so it works before the service starts.


## 1.5.4 — Auth key check works before the service starts

- **Version code:** 10024
- **APK:** `releases/CMFx-v1.5.4.apk`
- **SHA256:** `d89af8a07646fdb4741db3b035252a5af35e1784cc09ae8081fb9350a92fd7ac`
- **Status:** successful build


## 1.5.5 — Clean editorial UI refresh

- **Version code:** 10025
- **APK:** `releases/CMFx-v1.5.5.apk`
- **SHA256:** `9fafd1d53d8ccff2e8e7fa707608573b5f072c6770c3e82fdf302b1b5174018c`
- **Status:** successful build


## 1.5.6 — Clean editorial UI refresh, scrollable dashboard

- **Version code:** 10026
- **APK:** `releases/CMFx-v1.5.6.apk`
- **SHA256:** `069957db1eb2c56a29548656b670dcd74287c00e3f4b6efd72f19d108b6c8925`
- **Status:** successful build


## 1.5.7 — Clean editorial UI refresh, polished system spacing

- **Version code:** 10027
- **APK:** `releases/CMFx-v1.5.7.apk`
- **SHA256:** `7f310787a6df38866707184cebbcf9d98575720795f150a27618cf8bbc62b67b`
- **Status:** successful build


## 1.5.8 — Clean editorial UI refresh, visible header

- **Version code:** 10028
- **APK:** `releases/CMFx-v1.5.8.apk`
- **SHA256:** `4d4c6de2942c55c299d206e5d2b49cea24ee21edb1dd14056963a94032786759`
- **Status:** successful build


## 1.5.9 — Clean editorial UI refresh, system bars

- **Version code:** 10029
- **APK:** `releases/CMFx-v1.5.9.apk`
- **SHA256:** `3330b769124a4ca97e8666a74c2319a12e45af810559f78af6fcf08c2c094694`
- **Status:** successful build


## 1.6.0 — Clean editorial UI refresh, edge-to-edge header fix

- **Version code:** 10030
- **APK:** `releases/CMFx-v1.6.0.apk`
- **SHA256:** `0a0802478e14e755f84e46168c1875b80ac490253372ae6beb32ffb7e588edac`
- **Status:** successful build


## 1.6.1 — Clean editorial UI refresh, visible brand header

- **Version code:** 10031
- **APK:** `releases/CMFx-v1.6.1.apk`
- **SHA256:** `d3959b68c1ef48a8c507d20dc8133369227be0361b572f04bf8a48d8da2e1563`
- **Status:** successful build


## 1.6.2 — Clean editorial UI refresh, visible brand lockup

- **Version code:** 10032
- **APK:** `releases/CMFx-v1.6.2.apk`
- **SHA256:** `b852cdc68f19cd7cb9db269bc0c09c5086cb364c3afbd8e47db87a347390a7b2`
- **Status:** successful build


## 1.6.3 — Clean editorial UI refresh, stable brand header

- **Version code:** 10033
- **APK:** `releases/CMFx-v1.6.3.apk`
- **SHA256:** `2080a3cb4f5cbe600bdcf982342eb1583ff36ef2aa56d3e7720a2bb6bfbadbec`
- **Status:** successful build


## 1.6.4 - Clean editorial UI refresh, status spacing

- **Version code:** 10034
- **APK:** `releases/CMFx-v1.6.4.apk`
- **SHA256:** `049292216435fdc73a1d5e49349f15fd49e16dc1c924fffb2332904590d39406`
- **Status:** successful build

## 1.6.5 - Clean editorial UI refresh, visible connection status

- **Version code:** 10035
- **APK:** `releases/CMFx-v1.6.5.apk`
- **SHA256:** `5f40a30fe940edf5fcf0474ee97603842f6ea19e1af2cf797d327f55daaf97e9`
- **Status:** successful build


## 1.6.6 - Clean editorial UI refresh, final header spacing

- **Version code:** 10036
- **APK:** `releases/CMFx-v1.6.6.apk`
- **SHA256:** `da0d69c4a7082409b9d4cd0a84f108ee59266ac411dc02b3fbd41a242b65093e`
- **Status:** hardware-verified UI build
- **Features:** Figma-inspired pale lavender canvas, editorial black/coral
  action hierarchy, rounded white controls, labeled sections, scrollable
  dashboard, and preserved auth-key, health, companion, weather, contacts,
  alarms, and face-upload actions.

## 1.7.0 - Bottom navigation and grouped functionality

- **Version code:** 10037
- **APK:** `releases/CMFx-v1.7.0.apk`
- **SHA256:** `8641619cae4bf67b956fa2936c97bb205785bcb5f260b3bf675d86447875b23e`
- **Status:** hardware-verified UI build
- **Features:** persistent bottom navigation for **Faces**, **Quick**, and
  **Debug**; face browsing/upload tools grouped together; connection, health,
  find-watch, DND, music, alarms, contacts, weather, and notifications grouped
  under Quick; Gadgetbridge auth-key check and logs grouped under Debug.


## 1.7.0 — Bottom navigation: faces, quick controls, debug

- **Version code:** 10037
- **APK:** `releases/CMFx-v1.7.0.apk`
- **SHA256:** `8641619cae4bf67b956fa2936c97bb205785bcb5f260b3bf675d86447875b23e`
- **Status:** successful build


## 1.7.1 - FMC-only face workflow

- **Version code:** 10038
- **APK:** `releases/CMFx-v1.7.1.apk`
- **SHA256:** `64a8380f99305ea87e59c8cc800c8a51b5871ce973a2a286110838d26a044d78`
- **Status:** hardware-verified UI build
- **Changes:** Removed the local `Choose face .bin` picker and its file-provider
  workflow. Faces are now selected exclusively through the native FMC browser;
  FMC downloads, upload, replace/append, and dial probing remain available.

## 1.7.2 - Watch graphic launcher icon

- **Version code:** 10039
- **APK:** `releases/CMFx-v1.7.2.apk`
- **SHA256:** `bcab985406a1df82829c76795e6dc307a9118679ea695e62266b3e90df5aedf2`
- **Status:** hardware-verified build
- **Changes:** Replaced the generated CX launcher icon with the supplied
  dotted CMF-style watch graphic at all Android density sizes.

## 1.7.4 - QuickTools, Faces, Debug navigation order

- **Version code:** 10041
- **APK:** `releases/CMFx-v1.7.4.apk`
- **SHA256:** `2d7afd6c83700bebb3933ecf53c417a5bb589d7b40fcdd9809eddb5bf6929ae4`
- **Status:** hardware-verified UI build
- **Changes:** Bottom navigation is now **QuickTools | Faces | Debug**.
  Health sync sits below the Alarms/Contacts/Weather row. FMC browse and
  upload actions are adjacent in the Faces tab.

## 1.7.6 - Tappable six-slot dial replacement chooser

- **Version code:** 10043
- **APK:** `releases/CMFx-v1.7.6.apk`
- **SHA256:** `7e3d3ebcfd194031741353043cdf5656baf42d76d9148db5c19059c86828b027`
- **Status:** hardware-verified build
- **Features:** Probe installed dial IDs now opens six tappable conventional
  face-slot tiles. Selecting a tile fills the experimental replace ID and
  keeps the raw probe response visible. Empty firmware responses are clearly
  labeled because actual installed IDs are not currently exposed.

## 1.7.7 - In-app six-face slot replacement grid

- **Version code:** 10044
- **APK:** `releases/CMFx-v1.7.7.apk`
- **SHA256:** `733ce98968b5d258ef2b3038f259532f1362339af3ebd0c08d093a331dfd38b6`
- **Status:** hardware-verified build
- **Features:** Faces tab now contains six direct, tappable face-slot tiles.
  Selecting a tile fills the replacement ID and highlights the selected face
  before upload; the probe dialog remains available for raw firmware data.

## 1.7.8 - Remove in-app face slot replacement grid

- **Version code:** 10045
- **APK:** `releases/CMFx-v1.7.8.apk`
- **SHA256:** `9330337ad7a09c2f5d8a46ab04a653ae2638ba6a65880e4d95201b269b5e05b0`
- **Status:** hardware-verified build
- **Changes:** Removed the six direct face-slot tiles and their tap-to-replace
  behavior from the Faces tab. FMC browsing, upload, replacement ID field,
  and diagnostic probe remain available.


## 1.7.2 — Watch graphic launcher icon

- **Version code:** 10039
- **APK:** `releases/CMFx-v1.7.2.apk`
- **SHA256:** `bcab985406a1df82829c76795e6dc307a9118679ea695e62266b3e90df5aedf2`
- **Status:** successful build


## 1.7.3 — QuickTools-first navigation and grouped face actions

- **Version code:** 10040
- **APK:** `releases/CMFx-v1.7.3.apk`
- **SHA256:** `cc4f6005411c37b314b7c27f1d787ab16c190da9caa237413635ad1905b9400c`
- **Status:** successful build


## 1.7.4 — QuickTools, Faces, Debug navigation order

- **Version code:** 10041
- **APK:** `releases/CMFx-v1.7.4.apk`
- **SHA256:** `2d7afd6c83700bebb3933ecf53c417a5bb589d7b40fcdd9809eddb5bf6929ae4`
- **Status:** successful build


## 1.7.6 — Tappable six-slot dial replacement chooser

- **Version code:** 10043
- **APK:** `releases/CMFx-v1.7.6.apk`
- **SHA256:** `7e3d3ebcfd194031741353043cdf5656baf42d76d9148db5c19059c86828b027`
- **Status:** successful build


## 1.7.7 — In-app six-face slot replacement grid

- **Version code:** 10044
- **APK:** `releases/CMFx-v1.7.7.apk`
- **SHA256:** `733ce98968b5d258ef2b3038f259532f1362339af3ebd0c08d093a331dfd38b6`
- **Status:** successful build


## 1.7.8 — Remove in-app face slot replacement grid

- **Version code:** 10045
- **APK:** `releases/CMFx-v1.7.8.apk`
- **SHA256:** `9330337ad7a09c2f5d8a46ab04a653ae2638ba6a65880e4d95201b269b5e05b0`
- **Status:** successful build


## 1.7.9 - Phone data sync menu and Health Preview

- **Version code:** 10046
- **APK:** `releases/CMFx-v1.7.9.apk`
- **SHA256:** `6518663867958c9c2addf1d58105ee662a89fa3f3f1ffb95124097c44853a200`
- **Status:** hardware-verified build
- **Changes:** Replaced the separate Alarms, Contacts, and Weather buttons
  with a single **Sync phone data to watch** menu. The menu opens the phone
  alarm app or the existing Contacts/Weather sync flows. Renamed Health Sync
  to **Health Preview**.

## 1.8.0 - Remove phone data sync button

- **Version code:** 10047
- **APK:** `releases/CMFx-v1.8.0.apk`
- **SHA256:** `aa7111bcbbbf6b79d122002cb95319e91cf0086b5b177923c9a07daae5347358`
- **Status:** hardware-verified build
- **Changes:** Removed the temporary **Sync phone data to watch** button and
  menu until a reliable phone-app integration is available. **Health Preview**
  remains in the Watch Data section.

## 1.8.1 - Health overview dashboard redesign

- **Version code:** 10048
- **APK:** `releases/CMFx-v1.8.1.apk`
- **SHA256:** `7d14079c9115b3149bbe55e3611e7ccba06e3cf52f502a90a12c7066c661b2c1`
- **Status:** hardware-verified build
- **Changes:** Redesigned Health Preview with a Figma-inspired white
  dashboard card, accent header, four metric cards for steps, heart rate,
  sleep, and battery, plus a latest-preview summary.


## 1.8.0 — Remove phone data sync button

- **Version code:** 10047
- **APK:** `releases/CMFx-v1.8.0.apk`
- **SHA256:** `aa7111bcbbbf6b79d122002cb95319e91cf0086b5b177923c9a07daae5347358`
- **Status:** successful build


## 1.8.1 — Health overview dashboard redesign

- **Version code:** 10048
- **APK:** `releases/CMFx-v1.8.1.apk`
- **SHA256:** `7d14079c9115b3149bbe55e3611e7ccba06e3cf52f502a90a12c7066c661b2c1`
- **Status:** successful build


## 1.8.2 — Health dashboard header and update layout

- **Version code:** 10049
- **APK:** `releases/CMFx-v1.8.2.apk`
- **SHA256:** `4a2716f164403fdb8429ddb6e2ef2bc31800107264b8ae551f196310f1fef512`
- **Status:** hardware-verified build
- **Changes:** Renamed the action to **Update Health Preview** and moved it
  below the health card. The dashboard now shows a last-update timestamp,
  removes the battery metric, and displays watch battery beside CMFx.


## 1.8.3 - Health dashboard metric cards

- **Version code:** 10050
- **APK:** `releases/CMFx-v1.8.3.apk`
- **SHA256:** `40a6a323c3e4d5f9d9a99ed9cf8fe94e8df9dfd555190b4abc80623d4d6610a4`
- **Status:** hardware-verified build
- **Changes:** Removed the text preview/status line from Watch Data and
  converted distance, calories, stress, and workouts into dashboard cards
  alongside steps, heart rate, sleep, and blood oxygen.


## 1.8.4 - Remove health overview icon

- **Version code:** 10051
- **APK:** `releases/CMFx-v1.8.4.apk`
- **SHA256:** `258646b948257b4d43085ed23b708f095cce406ca9e87b752070d981f6e62190`
- **Status:** hardware-verified build
- **Changes:** Removed the icon from the Health overview header.


## 1.8.5 — Organized CMFx shared storage

- **Version code:** 10052
- **APK:** `releases/CMFx-v1.8.5.apk`
- **SHA256:** `087b8d790b734854f8aa5e509192072299465d0f7129ec86ccf16f4560062fe1`
- **Status:** successful build


## 1.8.6 - Organized CMFx Documents storage

- **Version code:** 10053
- **APK:** `releases/CMFx-v1.8.6.apk`
- **SHA256:** `2c7ea8f2111ce75c3691a9866c1734ba064c98a9352047e521f928d15d588fb6`
- **Status:** hardware-verified build
- **Changes:** Added organized shared storage under
  `Documents/CMFx/`: `Faces/`, `Health/`, and `Logs/`, plus a README.
  Downloaded face binaries, health snapshots, and diagnostic session logs
  are labeled and stored in their matching folders. Pairing keys remain
  private to the app.


## 1.8.7 - FMC browser dashboard styling

- **Version code:** 10054
- **APK:** `releases/CMFx-v1.8.7.apk`
- **SHA256:** `1129f42be4c64e8a115c48a3c0cdb5ce326fef89339a2e065d53ad48ffdc9b07`
- **Status:** hardware-verified build
- **Changes:** Restyled the FMC browser popup with a lavender canvas, white
  rounded face cards, cleaner search and sort controls, compact metadata,
  and coral **Install face** actions.


## 1.8.8 - Round FMC face preview masks

- **Version code:** 10055
- **APK:** `releases/CMFx-v1.8.8.apk`
- **SHA256:** `8576bc5b4eaa409500723137bf319b71cc98525f8cad3dbfc019ffda1ac196bf`
- **Status:** successful build
- **Changes:** FMC face previews now use square circular masks for a
  watch-face presentation in the browser catalog.


## 1.8.9 - Nothing X style FMC close button

- **Version code:** 10056
- **APK:** `releases/CMFx-v1.8.9.apk`
- **SHA256:** `62f17df42e0bd0acb559968f044272d1f58aa2e9907c3cad3ef93c34aa01bcb9`
- **Status:** installed build
- **Changes:** Restyled the FMC browser Close action as a compact dark,
  rounded Nothing X-inspired pill button.


## 1.9.0 - CMF.X app branding

- **Version code:** 10057
- **APK:** `releases/CMFx-v1.9.0.apk`
- **SHA256:** `8d5509ca2cf9021862bd4aa91f12d2a9ee267629f49e931019db00e8984027eb`
- **Status:** hardware-verified build
- **Changes:** Renamed the user-facing app and notification-access labels to
  **CMF.X** and updated the in-app header. Package identity and existing
  `Documents/CMFx` storage remain unchanged.


## 1.9.1 - CMF.X official launcher icon

- **Version code:** 10058
- **APK:** `releases/CMFx-v1.9.1.apk`
- **SHA256:** `a178af317db8e540cc7952daeeee7f6dbc43987eca7ff3dd53b8f35f863127ad`
- **Status:** installed build
- **Changes:** Added the supplied black-and-coral CMF.X mark as the official
  launcher icon across Android density sizes.


## 1.9.2 - Original CMF.X SVG launcher icon

- **Version code:** 10059
- **APK:** `releases/CMFx-v1.9.2.apk`
- **SHA256:** `cf05f27b6edf7fc10f798dc55bd543fdb498d541fe5ef1687d7e0be28ba62ad2`
- **Status:** hardware-verified build
- **Changes:** Replaced the generated launcher artwork with the original
  `og-banner.svg` vector paths, used unchanged as the CMF.X Android launcher
  drawable. The supplied SVG copy is retained at `work/standalone/og-banner.svg`.


## 1.9.3 - CMF.X in-app brand mark

- **Version code:** 10060
- **APK:** `releases/CMFx-v1.9.3.apk`
- **SHA256:** `14f1d12f9ca44db514663947aa422e967eb51dec86e14e567c67b0c64ce02ae8`
- **Status:** hardware-verified build
- **Changes:** Added the original CMF.X vector mark beside the CMF.X header
  inside the app.


## 1.9.4 - FMC browser search and sort controls

- **Version code:** 10061
- **APK:** `releases/CMFx-v1.9.4.apk`
- **SHA256:** `324de80372733ad9263d10d589aa5dcfcaae0fb33bd62b8c6a5caa5512ea7720`
- **Status:** hardware-verified build
- **Changes:** Rebuilt the FMC search and sort controls as a clean rounded
  horizontal control row with a search icon, consistent spacing, and a
  correctly sized dropdown selector.


## 1.9.5 — FMC dark catalog UI style

- **Version code:** 10062
- **APK:** `releases/CMFx-v1.9.5.apk`
- **SHA256:** `8c37132a9fb5059eba1954f484629937582900c651150edcfae7d7e51515f1b7`
- **Status:** successful build


## 1.9.6 - FMC reference dark catalog style

- **Version code:** 10063
- **APK:** `releases/CMFx-v1.9.6.apk`
- **SHA256:** `2581734f3328061a5ceb6dc320f028d0ed44f865213f7f542508a0d5aea08a0b`
- **Status:** installed build
- **Changes:** FMC browser now follows the reference dark catalog style with
  a dark search field, Watch Pro 2 and Newest selector pills, dark face cards,
  circular previews, white serif names, and muted metadata.


## 1.9.7 - Geist UI fonts and face download progress

- **Version code:** 10064
- **APK:** `releases/CMFx-v1.9.7.apk`
- **SHA256:** `aae71e3bafc57fd97452c4d1cd865a16dfdbab15e8c865d0994fec8be91745b4`
- **Status:** hardware-verified build
- **Changes:** Added Geist Sans and Noto Serif font assets. App UI uses Geist,
  while CMF.X/face titles use Noto Serif. FMC face downloads now report live
  percentage progress in the Faces tab.


## 1.9.8 - Adopt companion session for face upload

- **Version code:** 10065
- **APK:** `releases/CMFx-v1.9.8.apk`
- **SHA256:** `d4b0a15a5adf39369ebf88da0a9ccdaee3ceba72e1f0c53bdcfb14f477c354ec`
- **Status:** installed build
- **Changes:** Face downloads adopt the WatchService BLE session automatically so an
  FMC face can proceed directly into upload.


## 1.9.9 - Face slot replacement and watch-sourced health

- **Version code:** 10066
- **APK:** `releases/CMFx-v1.9.9.apk`
- **SHA256:** `a2be819b9f211b654f0eac671df62f2cd2b2361a2d807c5f139199340b10f9ef`
- **Status:** hardware-verified build (installed, face install and health pull tested)
- **Changes:** Fixed the `finish=0x0a` upload dead end. The app reads the real
  installed-dial list with the documented `DIAL_COMMAND` (`FFFF/9055`) type-0
  query, which the watch answers with `FFFF/A055`
  (`result ‖ active ‖ total ‖ max ‖ ids`), parses resting HR, and appends while
  there is room. Re-installing a face that is already on the watch is refused
  before any bytes are sent, because the watch answers that upload with
  `0x0a` and rolls its dial list back (observed to drop faces). When the watch
  reports a full list the app shows a slot picker with the watch's own dial
  ids and replaces the chosen dial via `old_wf_id`. The upload progress bar is
  driven by the watch's chunk percentages again. Health now parses resting HR,
  normalises activity calories to kcal, buckets samples by local day, and the
  dashboard shows only values the watch actually reported (`—` otherwise). The
  dial-slot dialog lists only ids the watch reported, and the header adopts the
  companion session on launch instead of showing "Not connected".


## 1.9.10 — Fresh health totals on every pull

- **Version code:** 10067
- **APK:** `releases/CMFx-v1.9.10.apk`
- **SHA256:** `834a11285b8df955c379883ecb4245a7b51e64077a11d8ed80a561c44748ad90`
- **Status:** hardware-verified (two consecutive pulls returned identical totals)
- **Changes:** Health Preview no longer inflates. The watch re-sends the whole day on
  every activity pull, so steps, distance and calories used to be added on top of
  the numbers already on screen. Those totals are now accumulated per sync, deduped
  by sample timestamp, and committed with `commitSync()`, which replaces the
  dashboard values instead of adding to them. Verified on the watch: pulls at 04:49
  and 04:50 both reported `379 steps, 256 m, 7 kcal`.


## 1.9.11 — Last update only reflects real health pulls

- **Version code:** 10068
- **APK:** `releases/CMFx-v1.9.11.apk`
- **SHA256:** `be07039d213bdfef9626d05d13cd8f75b1c6cdff13f522bf483633d606dfbf62`
- **Status:** hardware-verified (fresh launch showed no stale data; two pulls identical)
- **Changes:** The battery notification no longer sets the dashboard's `LAST UPDATE`
  stamp, so connecting to the watch can't make the health card look freshly synced
  with no data behind it. The card now starts fully empty (`—` everywhere, including
  `LAST UPDATE`) until a pull actually returns records. "Has activity" also follows
  the last committed sync instead of the per-sync record counter, so a pull that
  returns nothing new keeps the previous totals on screen instead of blanking them.
  Verified: after relaunch on 1.9.11 the card showed all `—`; pulls at 04:52:21 and
  04:52:54 both reported `379 steps, 0.3 km, 7 kcal` and the card stayed at 379.


## 1.9.12 - Copy auth key button

- **Version code:** 10069
- **APK:** `releases/CMFx-v1.9.12.apk`
- **SHA256:** `a822418139e3eca0cc5221197a470cb169a58f95aeb67a1c74f8dd8cfca936d4`
- **Status:** hardware-verified (dialog shows current key + copy icon, verified via
  uiautomator; tap fired without crash)
- **Changes:** The "Check auth key" dialog now shows the current stored auth key with a
  one-tap copy button (clipboard copy + "Auth key copied" toast). Previously the key had
  to be long-press selected and copied manually. Verified on device: key text rendered
  and the copy ImageButton appeared with content-desc "Copy auth key" in the dialog.
  Dialog body was then cleaned up into distinct sections (key row, status, note, warning)
  with adjusted typography and spacing; re-verified via uiautomator after rebuild.


## 1.9.12 — Copy auth key button

- **Version code:** 10069
- **APK:** `releases/CMFx-v1.9.12.apk`
- **SHA256:** `a822418139e3eca0cc5221197a470cb169a58f95aeb67a1c74f8dd8cfca936d4`
- **Status:** successful build

