[CmdletBinding()]
param(
    [Parameter(Mandatory)] [string] $DeviceSerial,
    [string] $OutputDirectory,
    [ValidateSet('FIXED_LAMBDA','ADAPTIVE_LAMBDA')]
    [string[]] $Mode = @('FIXED_LAMBDA','ADAPTIVE_LAMBDA')
)

$ErrorActionPreference = 'Stop'
$repo = Split-Path $PSScriptRoot -Parent
$manifest = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'replay_workload_t3.json') -Raw | ConvertFrom-Json
$asset = Join-Path $repo $manifest.asset
if ((Get-FileHash -LiteralPath $asset).Hash -ne $manifest.asset_sha256) { throw 'Replay asset hash changed.' }
$ids = @(12,13,17,18,23,31,41,46,52,53)
if (@($ids | Where-Object { $_ -notin $manifest.calibration_session_ids -or $_ -in $manifest.evaluation_session_ids }).Count) {
    throw 'The pinned workload is not exclusively from the calibration split.'
}
if (!$OutputDirectory) {
    $OutputDirectory = Join-Path $repo ('build\step-t4b1-' + [DateTime]::UtcNow.ToString('yyyyMMdd_HHmmss_fff'))
}
New-Item -ItemType Directory -Path $OutputDirectory | Out-Null
foreach ($currentMode in $Mode) {
    $log = Join-Path $OutputDirectory "$currentMode.txt"
    $arguments = @('shell','am','instrument','-w','-r','-e','class',
        'com.socialapp.core.prefetch.P5CalibrationTest#runSelectedSessions',
        '-e','p5Mode',$currentMode,'-e','sessionIds',($ids -join ','),
        'com.socialapp.test/androidx.test.runner.AndroidJUnitRunner')
    & adb -s $DeviceSerial @arguments | Tee-Object -FilePath $log | Out-Host
    $exitCode = $LASTEXITCODE
    & adb -s $DeviceSerial logcat -d -s P5Calibration:I ReplayConfirmation:I AndroidRuntime:E |
        Set-Content -LiteralPath (Join-Path $OutputDirectory "$currentMode-logcat.txt") -Encoding UTF8
    if ($exitCode -ne 0 -or !(Select-String -LiteralPath $log -SimpleMatch 'OK (1 test)' -Quiet)) {
        throw "$currentMode did not complete. Device logs are in $OutputDirectory. Stopping the calibration."
    }
}
