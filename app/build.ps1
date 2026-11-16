param(
  [string]$Work = "C:\Users\Helios\Documents\OpenCode\Projects\REA-01",
  [switch]$SkipInstall
)

$ErrorActionPreference = "Stop"
$root = $PSScriptRoot

$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.13.9-hotspot"
$java  = Join-Path $env:JAVA_HOME "bin\java.exe"
$javac = Join-Path $env:JAVA_HOME "bin\javac.exe"
$bt = "C:\Users\Helios\Tools\android-sdk\build-tools\android-37.0"
$androidJar = "C:\Users\Helios\Tools\android-sdk\platforms\android-36\android.jar"
$ks = Join-Path $Work "keys\cmfx.jks"

$build = Join-Path $root "build"
$dist  = Join-Path $root "dist"
$versionFile = Join-Path $root "version.json"
$iconSource = Join-Path $root "icon-source.jpg"
$version = Get-Content $versionFile -Raw | ConvertFrom-Json
$versionCode = [int]$version.versionCode
$versionName = [string]$version.versionName
$archive = Join-Path (Split-Path $root -Parent) "releases"

if ($versionCode -lt 1 -or [string]::IsNullOrWhiteSpace($versionName)) {
  throw "Invalid version.json"
}
Remove-Item $build, $dist -Recurse -Force -ErrorAction SilentlyContinue
$null = New-Item -ItemType Directory -Force `
  $build, $dist, `
  (Join-Path $build "classes"), (Join-Path $build "dex"), `
  (Join-Path $build "tools"), (Join-Path $build "res")

Write-Host "== icon =="
$drawable = Join-Path $build "res\drawable"
$null = New-Item -ItemType Directory -Force $drawable
Copy-Item (Join-Path $root "cmf_icon.xml") (Join-Path $drawable "cmf_icon.xml") -Force
Copy-Item (Join-Path $root "ic_copy.xml") (Join-Path $drawable "ic_copy.xml") -Force
$wi = Join-Path $root "weather-icons"
if (Test-Path $wi) {
    Get-ChildItem -Path $wi -Filter *.png | ForEach-Object {
        Copy-Item $_.FullName (Join-Path $drawable $_.Name) -Force
    }
}
$fontOut = Join-Path $build "res\font"
$null = New-Item -ItemType Directory -Force $fontOut
Copy-Item (Join-Path $root "fonts\Geist-Regular.ttf") (Join-Path $fontOut "geist_regular.ttf") -Force
Copy-Item (Join-Path $root "fonts\Geist-Bold.ttf") (Join-Path $fontOut "geist_bold.ttf") -Force
Copy-Item (Join-Path $root "fonts\NotoSerif.ttf") (Join-Path $fontOut "noto_serif.ttf") -Force
Copy-Item (Join-Path $root "fonts\ndot_regular.otf") (Join-Path $fontOut "ndot_regular.otf") -Force

Write-Host "== compile resources =="
& "$bt\aapt2.exe" compile --dir (Join-Path $build "res") -o (Join-Path $build "res.zip")
if ($LASTEXITCODE -ne 0) { throw "aapt2 compile failed" }

Write-Host "== javac app =="
$sources = Get-ChildItem -Recurse -Filter *.java (Join-Path $root "src") | ForEach-Object { $_.FullName }
& $javac --release 8 -Xlint:none -classpath $androidJar `
  -d (Join-Path $build "classes") $sources
if ($LASTEXITCODE -ne 0) { throw "javac failed" }

Write-Host "== d8 =="
$classes = Get-ChildItem -Recurse -Filter *.class (Join-Path $build "classes") | ForEach-Object { $_.FullName }
& "$bt\d8.bat" --lib $androidJar --min-api 26 --output (Join-Path $build "dex") $classes
if ($LASTEXITCODE -ne 0) { throw "d8 failed" }

Write-Host "== aapt2 link =="
# version.json is the single source of truth: inject its values into the manifest.
$manifestText = Get-Content (Join-Path $root "AndroidManifest.xml") -Raw
$manifestText = $manifestText -replace 'android:versionCode="[^"]*"', "android:versionCode=`"$versionCode`""
$manifestText = $manifestText -replace 'android:versionName="[^"]*"', "android:versionName=`"$versionName`""
$builtManifest = Join-Path $build "AndroidManifest.xml"
Set-Content -Path $builtManifest -Value $manifestText -Encoding UTF8

& "$bt\aapt2.exe" link -o (Join-Path $build "base.apk") -I $androidJar `
  --manifest $builtManifest `
  (Join-Path $build "res.zip") `
  --min-sdk-version 26 --target-sdk-version 36 `
  --version-code $versionCode --version-name $versionName
if ($LASTEXITCODE -ne 0) { throw "aapt2 link failed" }

Write-Host "== merge dex =="
& $javac -d (Join-Path $build "tools") (Join-Path $root "tools\ZipMerge.java")
if ($LASTEXITCODE -ne 0) { throw "ZipMerge compile failed" }
& $java -cp (Join-Path $build "tools") ZipMerge `
  (Join-Path $build "base.apk") (Join-Path $build "merged.apk") `
  "classes.dex=$(Join-Path $build 'dex\classes.dex')"
if ($LASTEXITCODE -ne 0) { throw "ZipMerge failed" }

Write-Host "== align + sign =="
$aligned = Join-Path $build "aligned.apk"
$out = Join-Path $dist "CMFx.apk"
& "$bt\zipalign.exe" -f -P 16 4 (Join-Path $build "merged.apk") $aligned
if ($LASTEXITCODE -ne 0) { throw "zipalign failed" }

# Keystore password is never stored in source: use the CMFX_KEYPASS environment
# variable, or a local-only keys/password.txt (both gitignored).
$keyPass = $env:CMFX_KEYPASS
if ([string]::IsNullOrEmpty($keyPass)) {
  $pwFile = Join-Path $Work "keys\password.txt"
  if (Test-Path $pwFile) { $keyPass = (Get-Content $pwFile -Raw).Trim() }
}
if ([string]::IsNullOrEmpty($keyPass)) {
  throw "Keystore password missing: set CMFX_KEYPASS or create keys/password.txt"
}

& "$bt\apksigner.bat" sign --ks $ks --ks-pass pass:$keyPass --key-pass pass:$keyPass `
  --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true `
  --out $out $aligned
if ($LASTEXITCODE -ne 0) { throw "apksigner failed" }

& "$bt\apksigner.bat" verify $out | Out-Null
if ($LASTEXITCODE -ne 0) { throw "apksigner verify failed" }

& "$bt\zipalign.exe" -c -P 16 4 $out
if ($LASTEXITCODE -ne 0) { throw "alignment check failed" }

Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::OpenRead($out)
try {
  $arsc = $zip.GetEntry("resources.arsc")
  if ($arsc -eq $null) { throw "resources.arsc missing" }
  if ($arsc.CompressedLength -ne $arsc.Length) { throw "resources.arsc is compressed" }
  $dex = $zip.GetEntry("classes.dex")
  if ($dex -eq $null) { throw "classes.dex missing" }
} finally { $zip.Dispose() }

& "$bt\aapt2.exe" dump badging $out 2>$null | Select-Object -First 1
& "$bt\aapt2.exe" dump badging $out 2>$null |
  Select-String -Pattern "launchable-activity" | ForEach-Object { $_.Line.Trim() }

$item = Get-Item $out
"APK:    " + $item.FullName
"Size:   " + $item.Length + " bytes"
"SHA256: " + ([BitConverter]::ToString(
  [System.Security.Cryptography.SHA256]::Create().ComputeHash(
    [System.IO.File]::ReadAllBytes($out))) -replace '-', '').ToLower()

if (-not $SkipInstall) {
  $adb = "C:\Users\Helios\Tools\android-sdk\platform-tools\adb.exe"
  if (Test-Path $adb) {
    Write-Host "== install =="
    & $adb install -r $out
    if ($LASTEXITCODE -ne 0) { throw "adb install failed" }
  }
}

Write-Host "== archive successful release =="
$null = New-Item -ItemType Directory -Force $archive
# Keep only the current release: drop older archived versions.
Get-ChildItem -Path $archive -Filter "CMFx-v*.apk" -File |
  Where-Object { $_.Name -ne "CMFx-v$versionName.apk" } |
  Remove-Item -Force -ErrorAction SilentlyContinue
$archiveName = "CMFx-v$versionName.apk"
$archivePath = Join-Path $archive $archiveName
Copy-Item $out $archivePath -Force
$hash = ([BitConverter]::ToString(
  [System.Security.Cryptography.SHA256]::Create().ComputeHash(
    [System.IO.File]::ReadAllBytes($archivePath))) -replace '-', '').ToLower()
$label = if ($version.label) { [string]$version.label } else { "Release" }
$versionsDoc = Join-Path (Split-Path $root -Parent) "docs\VERSIONS.md"
$entry = "`n## $versionName - $label`n`n- **Version code:** $versionCode`n- **APK:** ``releases/$archiveName```n- **SHA256:** ``$hash```n- **Status:** successful build`n"
if (-not (Test-Path $versionsDoc)) {
  "# CMFx successful versions`n`nOnly builds that complete the release checks are recorded here.`n$entry" |
    Set-Content $versionsDoc -Encoding UTF8
} elseif (-not (Get-Content $versionsDoc -Raw).Contains("## $versionName -")) {
  Add-Content $versionsDoc $entry -Encoding UTF8
}
Write-Host "Archived: $archivePath"
