param(
    [Parameter(Mandatory = $true)]
    [string]$Manifest,
    [switch]$ListTests,
    [string]$Serial,
    [string]$ProfileOutputDirectory,
    [string]$JourneyCatalog
)

$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
if (!$JourneyCatalog) { $JourneyCatalog = Join-Path $repo 'baselineprofile/journey-catalog.json' }
$catalog = Get-Content -LiteralPath $JourneyCatalog -Raw | ConvertFrom-Json
$selection = Get-Content -LiteralPath $Manifest -Raw | ConvertFrom-Json
if ($catalog.schemaVersion -ne 1 -or $selection.schemaVersion -ne 1 -or $selection.method -cnotin 'M0','M1') {
    throw 'Unsupported catalog or selection manifest.'
}

$eligible = @($catalog.journeys | Where-Object { $_.status -ceq 'VERIFIED' -and $_.requiresLogin -eq $false })
$ids = @($selection.journeyIds)
if ($ids.Count -ne ($catalog.postStartupBudget + 1) -or @($ids | Sort-Object -Unique).Count -ne $ids.Count) {
    throw 'Selection must contain one startup journey and the configured number of distinct post-startup journeys.'
}
$journeys = @(foreach ($id in $ids) {
    $match = @($eligible | Where-Object id -CEQ $id)
    if ($match.Count -ne 1) { throw "Unknown or unsupported journey: $id" }
    $match[0]
})
if (!$journeys[0].includeInStartupProfile -or @($journeys | Where-Object includeInStartupProfile).Count -ne 1) {
    throw 'The first journey must be the only startup journey.'
}

if ($selection.method -ceq 'M1') {
    $workload = Get-Content -LiteralPath (Join-Path $repo 'scripts/replay_workload_t3.json') -Raw | ConvertFrom-Json
    if ($selection.calibration.source -cne 'REPLAY' -or
        $selection.calibration.workloadManifest -cne 'scripts/replay_workload_t3.json' -or
        !$selection.calibration.sessionIds -or
        @($selection.calibration.sessionIds | Where-Object {
            $_ -notin $workload.calibration_session_ids -or $_ -in $workload.evaluation_session_ids
        }).Count) {
        throw 'M1 may only use Replay sessions from the T3 calibration split.'
    }
    $ranked = @($eligible | Where-Object { !$_.includeInStartupProfile } | ForEach-Object {
        $count = $selection.calibration.candidateTransitionCounts.PSObject.Properties[$_.id]
        if (!$count -or $count.Value -isnot [int] -or $count.Value -lt 0) {
            throw "Expected a nonnegative integer calibration count for $($_.id)"
        }
        [pscustomobject]@{ Id = $_.id; Count = $count.Value }
    } | Sort-Object @{Expression='Count';Descending=$true}, Id | Select-Object -First $catalog.postStartupBudget)
    if (($ids[1..($ids.Count - 1)] -join ',') -cne ($ranked.Id -join ',')) {
        throw 'M1 journeys must match the frozen calibration ranking.'
    }
}

$methods = @($journeys.testMethod)
if (@($methods | Where-Object { $_ -cnotmatch '^[a-zA-Z]\w*$' }).Count -or
    @($methods | Sort-Object -Unique).Count -ne $methods.Count) {
    throw 'Invalid or duplicate generator method.'
}
$filter = ($methods | ForEach-Object { "$($catalog.generatorClass)#$_" }) -join ','
$runner = 'com.socialapp.baselineprofile/androidx.test.runner.AndroidJUnitRunner'
$runDirectory = "t6c2-$($selection.method)-$([guid]::NewGuid().ToString('N'))"
$deviceOutputDirectory = "/sdcard/Android/media/com.socialapp.baselineprofile/$runDirectory"
$runnerArgs = @(
    '-e', 'class', $filter,
    '-e', 'targetAppId', $catalog.targetPackage,
    '-e', 'androidx.benchmark.enabledRules', 'BaselineProfile',
    '-e', 't6ProfileIterations', "$($catalog.profileIterationsPerJourney)",
    '-e', 'additionalTestOutputDir', $deviceOutputDirectory
)

if ($ListTests) {
    $device = if ($Serial) { @('-s', $Serial) } else { @() }
    $output = @(& adb @device shell am instrument -w -r @runnerArgs -e log true $runner)
    $exitCode = $LASTEXITCODE
    $actual = @($output | ForEach-Object {
        if ($_ -match '^INSTRUMENTATION_STATUS: test=(\w+)$') { $Matches[1] }
    } | Sort-Object -Unique)
    if ($exitCode -ne 0 -or $actual.Count -ne $methods.Count -or
        @($actual | Where-Object { $_ -cnotin $methods }).Count -or
        !($output -match ('^OK \(' + $methods.Count + ' tests?\)\s*$'))) {
        throw "Runner discovery did not match expected methods: $($actual -join ', ')"
    }
}

$expectedProfileFiles = @(foreach ($journey in $journeys) {
    $kind = if ($journey.includeInStartupProfile) { 'startup' } else { 'baseline' }
    "BaselineProfileGenerator_$($journey.testMethod)-$kind-prof.txt"
})
if ($ProfileOutputDirectory) {
    $files = @(Get-ChildItem -LiteralPath $ProfileOutputDirectory -File -Filter '*-prof*.txt' -Recurse)
    foreach ($expected in $expectedProfileFiles) {
        $raw = @($files | Where-Object Name -CEQ $expected)
        if ($raw.Count -ne 1 -or $raw[0].Length -eq 0) { throw "Missing, empty or duplicate profile: $expected" }
    }
    foreach ($file in $files) {
        if ($file.Name -cin $expectedProfileFiles) { continue }
        if ($file.Name -cnotmatch '^(.+-prof)-\d{4}-\d{2}-\d{2}-\d{2}-\d{2}-\d{2}\.txt$') {
            throw "Unexpected profile: $($file.Name)"
        }
        $canonicalName = $Matches[1] + '.txt'
        $canonical = @($files | Where-Object Name -CEQ $canonicalName)
        if ($canonicalName -cnotin $expectedProfileFiles -or $canonical.Count -ne 1 -or
            (Get-FileHash -LiteralPath $file.FullName).Hash -ne (Get-FileHash -LiteralPath $canonical[0].FullName).Hash) {
            throw "Unexpected or stale profile: $($file.Name)"
        }
    }
}

[pscustomobject]@{
    Method = $selection.method
    JourneyIds = $ids
    Runner = $runner
    RunnerArguments = $runnerArgs
    DeviceOutputDirectory = $deviceOutputDirectory
    HostOutputDirectory = Join-Path $repo "build/t6c2/$runDirectory"
    ExpectedProfileFiles = $expectedProfileFiles
    DiscoveredMethods = if ($ListTests) { $actual } else { $null }
    ValidatedProfileDirectory = if ($ProfileOutputDirectory) { (Resolve-Path -LiteralPath $ProfileOutputDirectory).Path } else { $null }
}
