param(
    [string]$Serial = 'emulator-5554',
    [string]$Adb = "$env:ANDROID_HOME\platform-tools\adb.exe"
)

$ErrorActionPreference = 'Stop'
$captureDirectory = Join-Path $PSScriptRoot 'screenshots'
New-Item -ItemType Directory -Path $captureDirectory -Force | Out-Null
$previousSize = (& $Adb -s $Serial shell wm size) -join "`n"
$previousDensity = (& $Adb -s $Serial shell wm density) -join "`n"
$previousFont = (& $Adb -s $Serial shell settings get system font_scale).Trim()
$matrix = @(
    @{ Name = '320dp'; Width = 320; Height = 640; Font = '1.0' },
    @{ Name = '360dp'; Width = 360; Height = 800; Font = '1.0' },
    @{ Name = '393dp'; Width = 393; Height = 852; Font = '1.0' },
    @{ Name = '430dp'; Width = 430; Height = 932; Font = '1.0' },
    @{ Name = '600dp'; Width = 600; Height = 960; Font = '1.0' },
    @{ Name = '840dp'; Width = 840; Height = 1120; Font = '1.0' },
    @{ Name = 'landscape'; Width = 840; Height = 430; Font = '1.0' },
    @{ Name = '320dp-font200'; Width = 320; Height = 800; Font = '2.0' }
)

try {
    & $Adb -s $Serial shell wm density 320
    foreach ($configuration in $matrix) {
        $dimensions = "$($configuration.Width * 2)x$($configuration.Height * 2)"
        & $Adb -s $Serial shell wm size $dimensions
        & $Adb -s $Serial shell settings put system font_scale $configuration.Font
        $result = & $Adb -s $Serial shell am instrument -w -r `
            -e class com.scantrack.qr.VisualQaCaptureTest#capturePremiumUi `
            -e qaPrefix $configuration.Name -e qaCompact true `
            com.scantrack.qr.test/androidx.test.runner.AndroidJUnitRunner
        $result | Set-Content -LiteralPath (Join-Path $captureDirectory "$($configuration.Name)-capture.log")
        if (($result -join "`n") -notmatch 'OK \(1 test\)') {
            throw "Screenshot suite failed for $($configuration.Name). Inspect its capture log."
        }
        Write-Output "Captured $($configuration.Name)"
    }
    & $Adb -s $Serial pull /sdcard/Android/data/com.scantrack.qr/files/ui-qa/. $captureDirectory
} finally {
    if ($previousSize -match 'Override size: (\d+x\d+)') {
        & $Adb -s $Serial shell wm size $Matches[1]
    } else { & $Adb -s $Serial shell wm size reset }
    if ($previousDensity -match 'Override density: (\d+)') {
        & $Adb -s $Serial shell wm density $Matches[1]
    } else { & $Adb -s $Serial shell wm density reset }
    & $Adb -s $Serial shell settings put system font_scale $previousFont
}
