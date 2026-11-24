# CMFx-Companion
A companion for CMF Watch Pro 2 wrist companion ;)

# CMFx

A standalone Android companion app for the **CMF Watch Pro 2**. Could work with other watches ;). It installs watch-face (and a little bit extra.) `.bin` files over Bluetooth LE, syncs health data, pushes weather, and forwards
notifications — while **Nothing X stays installed and untouched**.

<img width="2560" height="1440" alt="Untitled" src="https://github.com/user-attachments/assets/b2ed9659-93d4-4ba0-b943-a01e692c034d" />


```
Package   dev.cmfx
App       app/
APK       app/dist/CMFx.apk
Signing   local-only keystore (set CMFX_KEYPASS or keys/password.txt; not shipped)
```

## Features

- **Faces** — browse the FMC catalog, download faces (stored in `Documents/CMFx/.Faces/<name>/`),
  browse downloaded faces with thumbnails, select and manually flash to the watch.
- **Health** — health preview (activity, heart rate, SpO2, stress, sleep), health overview
  dashboard, and a sleep summary card with deep/light/REM/awake breakdown.
- **Weather** — current conditions, hourly and 7-day forecast (Open-Meteo), automatic hourly
  sync to the watch, location settable in-app.
- **Companion controls** — find watch, do-not-disturb, send music, notification forwarding.
- **Debug** — check auth key (Gadgetbridge), probe installed dial ids, live logs, and the
  in-app **CMFx Docs** guide.

## Why a standalone app

Re-signing Nothing X is not viable: `com.nothing.smartcenter` is a preinstalled privileged
system app, so Android rejects any re-signed build with
`INSTALL_FAILED_UPDATE_INCOMPATIBLE: signatures do not match`.

CMFx instead speaks the watch protocol directly. FMC's browser client proved the watch
hands out everything a client needs: `AT GETSECRET` on the shell channel returns a
per-session secret, and the client proves possession of it to derive the session key.
No key material is extracted from Nothing X.

## How it works

1. Find the already-bonded watch, connect GATT, request MTU 247.
2. Enable notifications on `fff1`, `ffe2`, `77d4ff02`.
3. Pair: `AT GETSECRET` -> exchange proofs -> derive `SHA256(r||o||secret)[0..16]`.
4. Authenticate as `"fmc"` (`AUTH_NAME` -> `AUTH_MAC` -> nonce -> confirm).
5. Upload: `INIT_1` -> `INIT_2` (13-byte LE `[3][oldId][newId][size]`) -> answer each
   chunk request (offsets/lengths **big-endian**) -> `FINISH` (`1` = installed).
6. Frames: AES-128-CBC-PKCS7 + CRC32-LE; header
   `[0xF5][u16be len][u16be cmd1][u16be count][u16be index][u16be cmd2]`.

While CMFx connects, keep Nothing X disconnected from the watch (one client at a time);
it resumes normally afterwards.

## Quick start

1. Install `app/dist/CMFx.apk` (allow "install unknown apps" — it is self-signed).
2. Grant Bluetooth permissions, then connect to the watch.
3. **Faces** -> **Browse FMC watch faces** -> **Download** a face (no auto-flash).
4. **Browse downloaded faces** -> **Select Face**, then **Upload to watch**.
5. On the watch, long-press the current face to pick the new one.

## Build

```powershell
powershell -File app\build.ps1              # build, sign, verify, adb install
powershell -File app\build.ps1 -SkipInstall # build only
```

The script runs `aapt2` compile/link, `javac`, `d8`, merges the dex with
`app/tools/ZipMerge.java`, `zipalign -P 16 4`, and signs with v1+v2+v3. Version is read from
`app/version.json`. Sources: `app/src/dev/CMFx/`.

## Repository layout

```
.
├── app/            Android app: sources, resources, signing build script
├── docs/           Documentation, release history, in-app guide source, protocol notes
├── keys/           Signing keystore (not for redistribution)
├── reference/      Protocol docs and reference client sources
├── releases/       Current release APK (latest version only; older builds in Backup/)
└── Backup/         Historical reverse-engineering artifacts and legacy pipeline
```

## Documentation

- `docs/CMFx-Docs.txt` — source of the in-app guide (Debug -> CMFx DOCS -> View).
- `docs/VERSIONS.md` — release history.
- `docs/build.md` — historical patched-APK pipeline notes.
- `reference/cmf-protocol-README.md` — CMF Watch Pro 2 protocol reference.

## License

Released under the MIT License (see `LICENSE`). No Nothing proprietary assets are
redistributed; this is personal use.

## Credits

Protocol research from the FMC browser client and Gadgetbridge. Typeface: Ndot by
Nothing (via [xeji01/nothingfont](https://github.com/xeji01/nothingfont)).

