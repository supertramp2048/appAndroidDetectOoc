[CmdletBinding()]
param(
    [Parameter(Mandatory)] [string[]] $TelemetryDirectory,
    [Parameter(Mandatory)] [guid[]] $RunId,
    [string] $JourneyCatalog,
    [ValidateRange(2,2)] [int] $K = 2,
    [string] $PreviousGeneration,
    [string] $VerifiedM1Directory,
    [string] $DeviceSerial,
    [Parameter(Mandatory)] [string] $OutputDirectory,
    [switch] $AnalyzeOnly,
    [switch] $Smoke,
    [switch] $ForceRegenerate
)

$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
if (!$JourneyCatalog) { $JourneyCatalog = Join-Path $repo 'baselineprofile/journey-catalog.json' }
if (Test-Path -LiteralPath $OutputDirectory) { throw 'Each generation requires a new output directory.' }
$output = (New-Item -ItemType Directory -Path $OutputDirectory).FullName
$report = [ordered]@{ schemaVersion=1; startedAt=[DateTimeOffset]::Now.ToString('o'); status='RUNNING'; steps=[ordered]@{} }
function Save-Json($value, $name) {
    $value | ConvertTo-Json -Depth 15 | Set-Content -LiteralPath (Join-Path $output $name) -Encoding UTF8
}
function Complete-Step($name) {
    $report.steps[$name] = 'OK'
    Save-Json $report 'generation-report.json'
}

try {
    $report.steps.validation = 'RUNNING'
    if (@($RunId | Sort-Object -Unique).Count -ne $RunId.Count) { throw 'Duplicate source run ID.' }
    $workload = Get-Content -Raw -LiteralPath (Join-Path $repo 'scripts/replay_workload_t3.json') | ConvertFrom-Json
    $assetPath = Join-Path $repo $workload.asset
    if ((Get-FileHash -LiteralPath $assetPath).Hash -ne $workload.asset_sha256) { throw 'Replay asset checksum mismatch.' }
    $asset = Get-Content -Raw -LiteralPath $assetPath | ConvertFrom-Json
    $sessions = @{}
    foreach ($session in $asset.sessions) { $sessions[[int]$session.session_id] = $session.steps }
    $files = @(foreach ($directory in $TelemetryDirectory) { Get-ChildItem -LiteralPath $directory -Recurse -File })
    $ids = @($RunId | ForEach-Object ToString)
    $terminal = @(foreach ($file in $files | Where-Object Name -Like 'metrics_v2_*.csv') {
        Import-Csv -LiteralPath $file.FullName | Where-Object { $_.run_id -in $ids -and $_.row_type -ceq 'RUN_END' }
    })
    $nav = @(foreach ($file in $files | Where-Object Name -CEQ 'navigation_v1.csv') {
        Import-Csv -LiteralPath $file.FullName | Where-Object run_id -In $ids
    })
    $seenSessions = @{}
    $seenEvents = @{}
    $transitions = @{}
    $sources = @(foreach ($id in $ids) {
        $end = @($terminal | Where-Object run_id -EQ $id)
        if ($end.Count -ne 1 -or $end[0].schema_version -cne '2' -or
            $end[0].run_valid -cne 'true' -or $end[0].run_status -cne 'RUN_FINISHED') {
            throw "Missing, duplicate or invalid RUN_END for $id."
        }
        $rows = @($nav | Where-Object run_id -EQ $id)
        if (!$rows.Count -or $end[0].navigation_events -notmatch '^\d+$' -or $rows.Count -ne [int]$end[0].navigation_events) {
            throw "Incomplete NavigationEvent data for $id."
        }
        $stepCount = 0
        $runSessions = @(foreach ($group in $rows | Group-Object session_id) {
            $first = $group.Group[0]
            if (!$first.session_id -or $first.replay_session_id -notmatch '^[1-9]\d*$') { throw "Missing Replay session in $id." }
            $sessionId = [int]$first.replay_session_id
            if ($sessionId -in $workload.evaluation_session_ids -or $sessionId -notin $workload.calibration_session_ids) {
                throw "Session $sessionId is outside T3 calibration."
            }
            if ($seenSessions.ContainsKey($sessionId)) { throw "Calibration session $sessionId counted twice." }
            $seenSessions[$sessionId] = $id
            $steps = @($sessions[$sessionId])
            $stepCount += $steps.Count
            $expected = @(for ($i=0; $i -lt $steps.Count; $i++) {
                if ($i -eq 0 -or $steps[$i].screen -cne $steps[$i-1].screen) { $steps[$i].screen }
            })
            if ($group.Count -ne $expected.Count) { throw "Navigation length mismatch in session $sessionId." }
            $previous = ''
            $i = 0
            foreach ($row in $group.Group | Sort-Object { [int]$_.sequence }) {
                if (!$row.navigation_id -or $seenEvents.ContainsKey($row.navigation_id) -or
                    $row.schema_version -cne '1' -or $row.source -cne 'REPLAY' -or
                    $row.replay_session_id -ne $first.replay_session_id -or [int]$row.sequence -ne ($i+1) -or
                    [string]$row.from_screen -cne $previous -or $row.to_screen -cne $expected[$i]) {
                    throw "Invalid NavigationEvent chain in session $sessionId."
                }
                $seenEvents[$row.navigation_id] = $true
                if ($previous) { $transitions["$previous>$($row.to_screen)"]++ }
                $previous = $row.to_screen
                $i++
            }
            $sessionId
        })
        if ($end[0].confirmed_steps -notmatch '^\d+$' -or $stepCount -ne [int]$end[0].confirmed_steps) {
            throw "Confirmed step count differs from the calibration asset for $id."
        }
        [pscustomobject]@{runId=$id;sessionIds=$runSessions;navigationEvents=$rows.Count;transitions=$rows.Count-$runSessions.Count}
    })
    $report.sources = $sources
    $report.telemetryDirectories = @($TelemetryDirectory | ForEach-Object { (Resolve-Path -LiteralPath $_).Path })
    $report.mergePolicy = 'Count complete confirmed runtime chains once per T3 calibration session ID; reject overlapping sessions.'
    Complete-Step validation

    $report.steps.selection = 'RUNNING'
    $catalog = Get-Content -LiteralPath $JourneyCatalog -Raw | ConvertFrom-Json
    $eligible = @($catalog.journeys | Where-Object { $_.status -ceq 'VERIFIED' -and $_.requiresLogin -eq $false })
    $startup = @($eligible | Where-Object includeInStartupProfile)
    if ($startup.Count -ne 1 -or $catalog.postStartupBudget -ne $K -or $catalog.authState -cne 'guest' -or $catalog.prefetchPolicy -cne 'P0') {
        throw 'Catalog does not match the startup, budget or fixture configuration.'
    }
    $counts = [ordered]@{}
    $ranking = @($eligible | Where-Object { !$_.includeInStartupProfile } | ForEach-Object {
        $n = [int]$transitions["$($_.fromScreen)>$($_.toScreen)"]
        $counts[$_.id]=$n
        [pscustomobject]@{id=$_.id;count=$n}
    } | Sort-Object @{Expression='count';Descending=$true}, id)
    $selection = [ordered]@{
        schemaVersion=1; method='M1'; journeyIds=@($startup[0].id)+@($ranking | Select-Object -First $K -ExpandProperty id)
        calibration=[ordered]@{
            source='REPLAY'; workloadManifest='scripts/replay_workload_t3.json'; runIds=$ids
            sessionIds=@($seenSessions.Keys | Sort-Object); candidateTransitionCounts=$counts
        }
    }
    Save-Json $selection 'selection.json'
    Save-Json $ranking 'ranking.json'
    $manifest = Join-Path $output 'selection.json'
    $plan = & "$PSScriptRoot/select_profile_journeys.ps1" -Manifest $manifest -JourneyCatalog $JourneyCatalog
    Complete-Step selection

    $report.steps.decision = 'RUNNING'
    $inputs = @('settings.gradle','build.gradle','gradle.properties','app/build.gradle','app/proguard-rules.pro',
        'baselineprofile/build.gradle','scripts/continuous_profile_artifacts.ps1','scripts/select_profile_journeys.ps1','scripts/VerifyDexProfile.java') |
        ForEach-Object { Get-Item -LiteralPath (Join-Path $repo $_) }
    $inputs += @(Get-ChildItem -LiteralPath (Join-Path $repo 'gradle'),(Join-Path $repo 'app/src/main'),
        (Join-Path $repo 'app/src/release'),(Join-Path $repo 'baselineprofile/src/main') -File -Recurse)
    $filesForFingerprint = @($inputs | Sort-Object FullName | ForEach-Object {
        "$($_.FullName.Substring($repo.Length + 1))=$((Get-FileHash -LiteralPath $_.FullName).Hash)"
    })
    $device = if ($DeviceSerial) { (& adb -s $DeviceSerial shell getprop ro.build.fingerprint).Trim() }
    if ($DeviceSerial -and !$device) { throw 'Generator device is unavailable.' }
    $java = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/java.exe' } else { 'java' }
    $javaVersion = & $java --version
    if ($LASTEXITCODE -ne 0) { throw 'JDK version check failed.' }
    $key = [ordered]@{
        selection=@($selection.journeyIds | Sort-Object); startup=$startup[0].id; K=$K
        catalog=(Get-FileHash -LiteralPath $JourneyCatalog).Hash; inputs=$filesForFingerprint
        device=$device; java=$javaVersion; startupLayoutEnabled=$false
    }
    Save-Json $key 'profile-inputs.json'
    $report.inputFingerprint = (Get-FileHash -LiteralPath (Join-Path $output 'profile-inputs.json')).Hash
    $prior = if ($PreviousGeneration) { Get-Content -LiteralPath (Join-Path $PreviousGeneration 'generation-report.json') -Raw | ConvertFrom-Json }
    if ($VerifiedM1Directory -and ($prior -or $ForceRegenerate)) { throw 'Verified M1 import cannot be combined with a previous generation or force regeneration.' }
    if ($prior -and $prior.status -cnotin 'GENERATED','REUSED') { throw 'Previous generation did not publish artifacts.' }
    if ($prior) {
        $old = Get-Content -LiteralPath (Join-Path $PreviousGeneration 'selection.json') -Raw | ConvertFrom-Json
        $report.previousGeneration = (Resolve-Path -LiteralPath $PreviousGeneration).Path
        $report.added = @($selection.journeyIds | Where-Object { $_ -cnotin $old.journeyIds })
        $report.removed = @($old.journeyIds | Where-Object { $_ -cnotin $selection.journeyIds })
    }
    $reuse = !$ForceRegenerate -and (($prior -and $prior.inputFingerprint -eq $report.inputFingerprint) -or [bool]$VerifiedM1Directory)
    $report.decision = if ($reuse) { 'REUSE' } else { 'REGENERATE' }
    $report.decisionReason = if ($ForceRegenerate) { 'FORCED_TECHNICAL_CHECK' }
        elseif ($reuse) { 'VERIFIED_COMPATIBLE_ARTIFACT' }
        elseif (!$prior) { 'NO_PREVIOUS_GENERATION' }
        elseif ($report.added.Count -or $report.removed.Count) { 'SELECTION_CHANGED' }
        else { 'PROFILE_INPUTS_CHANGED' }
    Complete-Step decision
    if ($AnalyzeOnly) {
        $report.status = 'ANALYZED'
    } else {
        if (!$DeviceSerial) { throw 'A generator device is required to publish a generation.' }
        $report.steps.artifacts = 'RUNNING'
        if ($VerifiedM1Directory) {
            $report.artifacts = & "$PSScriptRoot/import_verified_profile_m1.ps1" -Directory $VerifiedM1Directory -Selection $manifest -DeviceFingerprint $device
            $report.importedT6C2Directory = (Resolve-Path -LiteralPath $VerifiedM1Directory).Path
            $report.importVerification = 'Fixture APK bytes, archived source/toolchain, selected test methods and iteration count, raw artifacts and output hashes verified. The historical collector APK digest is recorded; the current runner is not assumed to be byte-identical.'
        } elseif ($reuse) {
            foreach ($name in 'baseline','startup','apk','compilationEvidence') {
                $entry = $prior.artifacts.$name
                if (!$entry -or (Get-FileHash -LiteralPath $entry.path).Hash -ne $entry.sha256) { throw "Invalid reuse checksum: $name." }
            }
            $report.artifacts = $prior.artifacts
        }
        $new = & "$PSScriptRoot/continuous_profile_artifacts.ps1" -Output $output -Manifest $manifest -JourneyCatalog $JourneyCatalog -Serial $DeviceSerial -ReuseArtifacts $report.artifacts -Smoke:$Smoke
        if (!$reuse) { $report.artifacts = $new.artifacts }
        $report.feedback = $new.feedback
        $report.status = if ($reuse) { 'REUSED' } else { 'GENERATED' }
        Complete-Step artifacts
    }
    $report.finishedAt = [DateTimeOffset]::Now.ToString('o')
    Save-Json $report 'generation-report.json'
    [pscustomobject]$report
} catch {
    $report.status = 'FAILED'
    $report.error = $_.Exception.Message
    Save-Json $report 'generation-report.json'
    throw
}
