[CmdletBinding()]
param(
    [string] $OutputRoot,
    [string] $DeviceSerial,
    [ValidatePattern('^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+$')]
    [string] $ApplicationId = 'com.socialapp'
)

$ErrorActionPreference = 'Stop'
if (!$OutputRoot) { $OutputRoot = Join-Path $PSScriptRoot '..\experiment_results' }
$adb = (Get-Command adb -CommandType Application -ErrorAction Stop).Source
$devices = @(& $adb devices | ForEach-Object {
    if ($_ -match '^(\S+)\s+device\s*$') { $Matches[1] }
})
if (!$DeviceSerial) {
    if ($devices.Count -ne 1) { throw 'Connect one authorized device or specify -DeviceSerial.' }
    $DeviceSerial = $devices[0]
}
if ($DeviceSerial -notin $devices) { throw "Device is not available: $DeviceSerial" }
if ($DeviceSerial -notmatch '^[a-zA-Z0-9_.:-]+$') { throw 'Unsupported device serial.' }

$external = "/sdcard/Android/data/$ApplicationId/files/metrics"
$app = $ApplicationId
$internal = 'files/metrics'
$exportId = [DateTime]::UtcNow.ToString('yyyyMMdd_HHmmss_fff') + '_' + [Guid]::NewGuid().ToString('N').Substring(0, 8)
$destination = Join-Path $OutputRoot $exportId
New-Item -ItemType Directory -Path $destination -Force | Out-Null
$destination = (Resolve-Path -LiteralPath $destination).Path
$filePattern = '^(metrics_v2_[A-Za-z0-9_.-]+|performance_v1|navigation_v1|p5_v1)\.csv$'
$exported = 0
$failures = 0

$externalFiles = @(& $adb -s $DeviceSerial shell "ls -1 $external 2>/dev/null" |
    ForEach-Object { $_.Trim() } | Where-Object { $_ -cmatch $filePattern })
$internalFiles = @(& $adb -s $DeviceSerial shell "run-as $app ls -1 $internal 2>/dev/null" |
    ForEach-Object { $_.Trim() } | Where-Object { $_ -cmatch $filePattern })

foreach ($storage in @('external', 'internal')) {
    $names = if ($storage -eq 'external') { $externalFiles } else { $internalFiles }
    if (!$names) { continue }
    $folder = Join-Path $destination $storage
    New-Item -ItemType Directory -Path $folder | Out-Null
    foreach ($name in $names) {
        $local = Join-Path $folder $name
        $stage = $local + '.partial'
        if ($storage -eq 'external') {
            & $adb -s $DeviceSerial pull "$external/$name" $stage | Out-Host
            $code = $LASTEXITCODE
        } else {
            # Redirect native bytes directly, including on Windows PowerShell 5.1.
            $process = Start-Process -FilePath $adb -ArgumentList @(
                '-s', $DeviceSerial, 'exec-out', 'run-as', $app, 'cat', "$internal/$name"
            ) -NoNewWindow -Wait -PassThru -RedirectStandardOutput $stage
            $code = $process.ExitCode
        }
        if ($code -eq 0 -and (Test-Path -LiteralPath $stage) -and
            (Get-Content -LiteralPath $stage -TotalCount 1) -match '^schema_version,') {
            Move-Item -LiteralPath $stage -Destination $local
            $exported++
        } else {
            $failures++
            Write-Warning "Could not export $storage/$name."
        }
    }
}

if ($exported -eq 0) { throw "No readable telemetry files. Export directory: $destination" }
if ($failures -gt 0) { throw "Partial export. $exported file(s) saved in $destination, $failures failed." }
Write-Verbose "Exported $exported CSV file(s). Logcat is independent of this export."
$destination
