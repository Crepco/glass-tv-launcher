# Re-enables every package listed in disabled-packages.txt on the BRAVIA.
param([string]$Tv = "192.168.29.51:5555")
$adb = "$env:LOCALAPPDATA\Android\platform-tools\adb.exe"
& $adb connect $Tv | Out-Null
Get-Content "$PSScriptRoot\disabled-packages.txt" | ForEach-Object {
    & $adb -s $Tv shell pm enable $_
}
# Bring back the stock Google home screen (replaced by Projectivy).
& $adb -s $Tv shell pm enable com.google.android.tvlauncher
& $adb -s $Tv shell cmd package set-home-activity com.google.android.tvlauncher/.MainActivity
# Reset animation speed to normal.
foreach ($k in "window_animation_scale", "transition_animation_scale", "animator_duration_scale") {
    & $adb -s $Tv shell settings put global $k 1
}
