# Builds build\GlassLauncher.apk with the Android SDK tools directly (no Gradle).
$ErrorActionPreference = 'Stop'
$sdk = "$env:LOCALAPPDATA\Android\Sdk"
$bt = "$sdk\build-tools\35.0.0"
$jar = "$sdk\platforms\android-35\android.jar"
$env:JAVA_HOME = "$env:USERPROFILE\.jdks\jdk-17.0.20.1+1"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
$root = $PSScriptRoot
$out = "$root\build"

function Check($what) { if ($LASTEXITCODE) { throw "$what failed ($LASTEXITCODE)" } }

Remove-Item $out -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory "$out\classes", "$out\dex" | Out-Null

& "$bt\aapt2.exe" compile --dir "$root\res" -o "$out\res.zip"; Check 'aapt2 compile'
& "$bt\aapt2.exe" link -I $jar --manifest "$root\AndroidManifest.xml" `
    --min-sdk-version 26 --target-sdk-version 28 -o "$out\unsigned.apk" "$out\res.zip"; Check 'aapt2 link'

$src = Get-ChildItem "$root\src" -Recurse -Filter *.java | ForEach-Object FullName
& javac -source 8 -target 8 -bootclasspath $jar -Xlint:-options -encoding UTF-8 -d "$out\classes" $src; Check 'javac'

$classes = Get-ChildItem "$out\classes" -Recurse -Filter *.class | ForEach-Object FullName
& "$bt\d8.bat" --release --lib $jar --min-api 26 --output "$out\dex" $classes; Check 'd8'

Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [IO.Compression.ZipFile]::Open("$out\unsigned.apk", 'Update')
[void][IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, "$out\dex\classes.dex", 'classes.dex')
$zip.Dispose()

& "$bt\zipalign.exe" -f 4 "$out\unsigned.apk" "$out\aligned.apk"; Check 'zipalign'

# Local signing key for this sideloaded app only.
$ks = "$root\glass.keystore"
if (-not (Test-Path $ks)) {
    & keytool -genkeypair -keystore $ks -storepass glasslauncher -keypass glasslauncher -alias glass `
        -keyalg RSA -keysize 2048 -validity 10000 -dname 'CN=Glass Launcher' 2>&1 | Out-Null; Check 'keytool'
}
& "$bt\apksigner.bat" sign --ks $ks --ks-pass pass:glasslauncher --out "$out\GlassLauncher.apk" "$out\aligned.apk"; Check 'apksigner'
Write-Host "Built $out\GlassLauncher.apk"
