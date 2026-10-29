# Disables (does not delete) the bloat, ad-tracking and demo packages listed in
# disabled-packages.txt. Undo everything with restore-tv.ps1.
param([string]$Tv = "192.168.29.51:5555")
$adb = "$env:LOCALAPPDATA\Android\platform-tools\adb.exe"
& $adb connect $Tv | Out-Null
Get-Content "$PSScriptRoot\disabled-packages.txt" | ForEach-Object {
    $r = (& $adb -s $Tv shell pm disable-user --user 0 $_ 2>&1) -join ' '
    if ($r -match 'disabled') { "OK    $_" } else { "SKIP  $_ ($r)" }
}
