# Build notes

## Current: standalone app (`dev.cmfx`)

The shipping deliverable is a small app of its own; it speaks the watch-face protocol
directly and does not touch Nothing X. Everything lives in `app\`:

```
app\build.ps1              icon -> aapt2 -> javac -> d8 -> ZipMerge
                                       -> zipalign -P 16 4 -> apksigner -> install
app\AndroidManifest.xml    package dev.cmfx, minSdk 26, targetSdk 36
app\src\dev\CMFx\       MainActivity, Ble, Proto, Watch
app\tools\ZipMerge.java    merges classes.dex, keeps resources.arsc STORED
app\dist\CMFx.apk       signed output
```

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File app\build.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File app\build.ps1 -SkipInstall
```

Toolchain used by that script:

- JDK 21: `C:\Program Files\Eclipse Adoptium\jdk-21.0.13.9-hotspot`
- build-tools 37.0.0: `C:\Users\Helios\Tools\android-sdk\build-tools\android-37.0`
- android.jar (API 36): `C:\Users\Helios\Tools\android-sdk\platforms\android-36\android.jar`
- signing key: provide your own at `keys/cmfx.jks`; set `CMFX_KEYPASS` or create `keys/password.txt` (gitignored)

Protocol details and verified opcodes are documented in `README.md`.

## Historical: patched Nothing X (M1)

The rest of this file describes the re-signed Nothing X build. It is kept for reference:
that APK cannot be installed on this phone, because `com.nothing.smartcenter` is a
privileged system app and its signature check is enforced globally.

Toolchain (already installed):

- JDK 21: `C:\Program Files\Eclipse Adoptium\jdk-21.0.13.9-hotspot`
- android.jar (API 36): `C:\Users\Helios\Tools\android-sdk\platforms\android-36\android.jar`
- kotlin-stdlib 2.1.0: `C:\Users\Helios\Tools\android-sdk\libs\kotlin-stdlib-2.1.0.jar`
- build-tools 37.0.0: `C:\Users\Helios\Tools\android-sdk\build-tools\android-37.0`
- apktool 3.0.3: `C:\Users\Helios\Tools\apktool\apktool.bat`
  (framework `1.apk` is the API-36 android.jar; apktool needs it to link `@android:` refs)

## 1. Inject (once)

`work/decoded-full` holds the smali. Hooks are:

- `smali_classes8/com/nothing/nt_ble/plugin/WatchPluginImpl.smali`
  - head of `initPlugin` → `invoke-static {p0}, Lcom/CMFx/Bridge;->setPlugin(Ljava/lang/Object;)V`
  - head of `writeValue` → `invoke-static/range {p0 .. p8}, Lcom/CMFx/Bridge;->onOut(...)`
- `smali_classes8/com/nothing/nt_ble/NtBlePlugin.smali`
  - head of `onAttachedToEngine` → `invoke-static/range {p0 .. p0}, ...->setNtPlugin`
- `smali_classes8/com/nothing/nt_ble/WatchCommandParser.smali`
  - head of `getReceiveCommand` → `invoke-static/range {p1 .. p3}, ...->onRaw(...)`

Use `/range` when a parameter register is above v15 (`.locals` > 15).

## 2. Java → dex

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-21.0.13.9-hotspot'
$root='C:\Users\Helios\Documents\OpenCode\Projects\REA-01\work\CMFx'
$aj='C:\Users\Helios\Tools\android-sdk\platforms\android-36\android.jar'
$kt='C:\Users\Helios\Tools\android-sdk\libs\kotlin-stdlib-2.1.0.jar'
javac -source 8 -target 8 -classpath "$aj;$kt" -d "$root\classes" (Get-ChildItem "$root\src" -Recurse -Filter *.java).FullName
d8 --min-api 23 --lib "$aj" --lib "$kt" --output "$root\dex" (Get-ChildItem "$root\classes" -Recurse -Filter *.class).FullName
```

Then copy the result to `decoded-res\unknown\classes10.dex` (apktool writes unknown files
to the APK root; do **not** use `ZipArchive` update on the finished APK — it corrupts the
manifest entry).

## 3. apktool

`decoded-res` = resources + manifest + `smali`/`smali_classes2..9` + `lib/` + `unknown/`.

- The first dex **must** live in `smali`, not `smali_classes1` (otherwise apktool emits
  `classes1.dex` and ART ignores it).
- No `*.dex` files may sit in the project root.
- `apktool.yml` needs `- so` under `doNotCompress` for the merged native libs.

```powershell
apktool b .\decoded-res -o .\CMFx-universal-unsigned.apk
```

Delete stale `decoded-res/build/apk/classesN.dex` if the layout changed; apktool reuses
its build dir.

## 4. Universal APK specifics

The Play bundle ships an empty base + `split_config.arm64_v8a.apk`, so a one-file install
needs three changes (all already applied to `decoded-res`):

1. `lib/arm64-v8a/*.so` extracted from the split into `decoded-res/lib/arm64-v8a/`
   (apktool then prints `Importing lib...` during the build).
2. `apktool.yml` → `doNotCompress: - so` (the APK declares
   `extractNativeLibs="false"`, so the libs must be stored, not deflated).
3. `AndroidManifest.xml` → removed `android:requiredSplitTypes="base__abi,base__density"`,
   the `android:isSplitRequired` it implies, and the two
   `com.android.vending.splits*` meta-data entries. Without this the installer fails
   with `INSTALL_FAILED_MISSING_SPLIT`.

The split-route base (`work/split-route/CMFx-split-base-signed.apk`) was built
**before** step 3 and still carries `requiredSplitTypes`; keep it that way if you rebuild
the SAI pair.

## 5. Package + sign

```powershell
powershell -File .\package-apk.ps1
```

`zipalign -f -P 16 4` (16 KiB page alignment — valid on 4K and 16K kernels; `-P` cannot be
combined with `-p`), then `apksigner` v1+v2+v3 with `keys/cmfx.jks` (password supplied via CMFX_KEYPASS / keys/password.txt),
then verify + `zipalign -c -P 16 4` + badging.

Expected result: two launchable activities (`com.cmfx.MainActivity` / stock) and
`native-code: 'arm64-v8a'`.

The SAI split is signed with the same key:

```powershell
apksigner sign --ks keys\cmfx.jks --ks-pass pass:$keyPass --key-pass pass:$keyPass `
  --out split-route\split_config.arm64_v8a-signed.apk bundle\split_config.arm64_v8a.apk
```
