param(
    [Parameter(Mandatory)] [string]$OutputDirectory,
    [Parameter(Mandatory)] [string]$DeviceSerial,
    [Parameter(Mandatory)] [string]$Apk
)

$ErrorActionPreference = 'Stop'
$root = (New-Item -ItemType Directory -Path $OutputDirectory).FullName
$scripts = Split-Path $PSScriptRoot -Parent
$repo = Split-Path $scripts -Parent
$pipeline = Join-Path $scripts 'run_continuous_profile_pipeline.ps1'
$selector = Join-Path $scripts 'select_profile_journeys.ps1'
$asset = Get-Content -Raw "$repo/app/src/main/assets/mock_replay_100_sessions.json" | ConvertFrom-Json
$workload = Get-Content -Raw "$scripts/replay_workload_t3.json" | ConvertFrom-Json
$first = [guid]::NewGuid()
$second = [guid]::NewGuid()

function New-Fixture($name, [guid]$run, $sessionId, $invalid, $missing) {
    $path = (New-Item -ItemType Directory -Path "$root/$name").FullName
    $session = $asset.sessions | Where-Object session_id -EQ $sessionId
    $sessionKey = [guid]::NewGuid()
    $previous = ''
    $n = 0
    $nav = @(foreach ($step in $session.steps) {
        if ($step.screen -ceq $previous) { continue }
        [pscustomobject]@{schema_version=1; navigation_id=[guid]::NewGuid();source='REPLAY';run_id=$run;session_id=$sessionKey;
            replay_session_id=$sessionId;sequence=(++$n);from_screen=$previous;to_screen=$step.screen}
        $previous=$step.screen
    })
    [pscustomobject]@{schema_version=2;run_id=$run;row_type='RUN_END';run_status='RUN_FINISHED';
        run_valid=if($invalid){'false'}else{'true'};confirmed_steps=@($session.steps).Count;navigation_events=$nav.Count} |
        Export-Csv -NoTypeInformation "$path/metrics_v2_fixture.csv"
    if ($missing) { $nav = @($nav | Select-Object -Skip 1) }
    $nav | Export-Csv -NoTypeInformation "$path/navigation_v1.csv"
    $path
}

function Expect-Failure($name, $expected, [scriptblock]$action) {
    try { $null = & $action; throw 'Unexpected success.' }
    catch {
        if ($_.Exception.Message -notmatch $expected) { throw }
        "PASS $name"
    }
}

$cal = $workload.calibration_session_ids[0]
$valid = New-Fixture valid $first $cal $false $false
$invalid = New-Fixture invalid $first $cal $true $false
$missing = New-Fixture missing $first $cal $false $true
$evaluation = New-Fixture evaluation $first $workload.evaluation_session_ids[0] $false $false
$duplicate = New-Fixture duplicate $second $cal $false $false
Expect-Failure 'invalid RUN_END' 'invalid RUN_END' { & $pipeline -TelemetryDirectory $invalid -RunId $first -OutputDirectory "$root/invalid-result" -AnalyzeOnly }
Expect-Failure 'incomplete NavigationEvent' 'Incomplete NavigationEvent' { & $pipeline -TelemetryDirectory $missing -RunId $first -OutputDirectory "$root/missing-result" -AnalyzeOnly }
Expect-Failure 'evaluation session rejected' 'outside T3 calibration' { & $pipeline -TelemetryDirectory $evaluation -RunId $first -OutputDirectory "$root/evaluation-result" -AnalyzeOnly }
Expect-Failure 'duplicate session across runs' 'counted twice' { & $pipeline -TelemetryDirectory $valid,$duplicate -RunId $first,$second -OutputDirectory "$root/dup-result" -AnalyzeOnly }

$m1 = "$repo/baselineprofile/selection-m1.json"
$plan = & $selector -Manifest $m1
$unsupported = Get-Content -LiteralPath $m1 -Raw | ConvertFrom-Json
$unsupported.journeyIds[2] = 'unsupported_fixture'
$unsupported | ConvertTo-Json -Depth 8 | Set-Content "$root/unsupported.json"
Expect-Failure 'unsupported Journey ID' 'Unknown or unsupported' { & $selector -Manifest "$root/unsupported.json" }
$raw = (New-Item -ItemType Directory -Path "$root/raw").FullName
Expect-Failure 'missing profile artifact' 'Missing, empty or duplicate profile' { & $selector -Manifest $m1 -ProfileOutputDirectory $raw }
foreach ($name in $plan.ExpectedProfileFiles) { 'Lcom/socialapp/MyApplication;' | Set-Content "$raw/$name" }
'Lcom/socialapp/MyApplication;' | Set-Content "$raw/Unselected-baseline-prof.txt"
Expect-Failure 'extra profile artifact' 'Unexpected profile' { & $selector -Manifest $m1 -ProfileOutputDirectory $raw }

$candidate = & $pipeline -TelemetryDirectory $valid -RunId $first -OutputDirectory "$root/analyzed" -DeviceSerial $DeviceSerial -AnalyzeOnly
$prior = (New-Item -ItemType Directory -Path "$root/previous").FullName
Copy-Item "$root/analyzed/selection.json" $prior
@{status='GENERATED';inputFingerprint=$candidate.inputFingerprint;artifacts=@{baseline=@{path="$raw/Unselected-baseline-prof.txt";sha256=('0'*64)}}} |
    ConvertTo-Json -Depth 6 | Set-Content "$prior/generation-report.json"
Expect-Failure 'bad reuse checksum' 'Invalid reuse checksum' {
    & $pipeline -TelemetryDirectory $valid -RunId $first -DeviceSerial $DeviceSerial -PreviousGeneration $prior -OutputDirectory "$root/reuse-result"
}

Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
Copy-Item -LiteralPath $Apk "$root/bad-dex.apk"
$zip = [IO.Compression.ZipFile]::Open("$root/bad-dex.apk",[IO.Compression.ZipArchiveMode]::Update)
try {
    $entry=$zip.GetEntry('classes.dex')
    $stream=$entry.Open()
    try {
        $stream.Position=0
        $stream.WriteByte(0)
    } finally { $stream.Dispose() }
} finally { $zip.Dispose() }
$java = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/java.exe' } else { 'java' }
$ErrorActionPreference = 'Continue'
$verification = @(& $java "$scripts/VerifyDexProfile.java" "$root/bad-dex.apk" 2>&1)
if ($LASTEXITCODE -eq 0 -or !($verification -match 'DEX key or CRC mismatch')) { throw 'Invalid DEX verification did not fail as expected.' }
'PASS profile/DEX CRC mismatch'
