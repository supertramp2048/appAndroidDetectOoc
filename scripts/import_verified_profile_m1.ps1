param(
    [Parameter(Mandatory)] [string]$Directory,
    [Parameter(Mandatory)] [string]$Selection,
    [Parameter(Mandatory)] [string]$DeviceFingerprint
)

$ErrorActionPreference = 'Stop'
$repo = Split-Path $PSScriptRoot -Parent
$source = (Resolve-Path -LiteralPath $Directory).Path
$parent = Split-Path $source -Parent
$archived = Get-Content -LiteralPath "$source/provenance.json" -Raw | ConvertFrom-Json
$selectionData = Get-Content -LiteralPath $Selection -Raw | ConvertFrom-Json
$manifestPath = "$repo/baselineprofile/selection-m1.json"
$original = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
if ($archived.method -cne 'M1' -or $archived.benchmarkStartupProfile -ne $false -or
    $archived.manifestHash -ne (Get-FileHash -LiteralPath $manifestPath).Hash -or
    $archived.telemetryRunId -cne $original.calibration.runId -or
    ($archived.journeys.id -join ',') -cne ($selectionData.journeyIds -join ',') -or
    @($archived.journeys | Where-Object coldLaunchesObserved -NE 3).Count) {
    throw 'Archived M1 selection or collection configuration differs.'
}
if ($DeviceFingerprint -cnotin (Get-Content -LiteralPath "$parent/environment.txt")) { throw 'Archived device fingerprint differs.' }
$snapshot = (Get-Content -LiteralPath "$parent/sandbox-path.txt" -Raw).Trim()
$inputs = @('settings.gradle','build.gradle','gradle.properties','app/build.gradle','app/proguard-rules.pro','baselineprofile/build.gradle')
$inputs += @(Get-ChildItem -LiteralPath "$repo/gradle","$repo/app/src/main","$repo/baselineprofile/src/main" -Recurse -File |
    ForEach-Object { $_.FullName.Substring($repo.Length+1) })
$snapshotFiles = @(Get-ChildItem -LiteralPath "$snapshot/gradle","$snapshot/app/src/main","$snapshot/baselineprofile/src/main" -Recurse -File).Count
if ($snapshotFiles -ne ($inputs.Count - 6)) { throw 'Archived source file set differs.' }
foreach ($relative in $inputs) {
    if ((Get-FileHash -LiteralPath "$repo/$relative").Hash -ne (Get-FileHash -LiteralPath "$snapshot/$relative").Hash) {
        throw "Archived source or toolchain differs: $relative."
    }
}
foreach ($item in (Get-Content -LiteralPath "$parent/official-before.json" -Raw | ConvertFrom-Json)) {
    if ((Get-FileHash -LiteralPath $item.Path).Hash -ne $item.Hash) { throw 'T5 or selection checksums differ from the historical build.' }
}
$collectionApks = Get-Content -LiteralPath "$parent/collection-apk-hashes.json" -Raw | ConvertFrom-Json
$fixtureApk = @($collectionApks | Where-Object { (Split-Path $_.Path -Leaf) -ceq 'app-nonMinifiedRelease.apk' })
$collectorApk = @($collectionApks | Where-Object { (Split-Path $_.Path -Leaf) -ceq 'baselineprofile-nonMinifiedRelease.apk' })
if ($fixtureApk.Count -ne 1 -or $collectorApk.Count -ne 1 -or $collectorApk[0].Hash -notmatch '^[0-9A-Fa-f]{64}$' -or
    (Get-FileHash -LiteralPath $fixtureApk[0].Path).Hash -ne $fixtureApk[0].Hash) { throw 'Historical fixture APK or collector provenance differs.' }
$plan = Get-Content -LiteralPath "$source/collection-plan.json" -Raw | ConvertFrom-Json
if (($plan.JourneyIds -join ',') -cne ($selectionData.journeyIds -join ',') -or
    $plan.RunnerArguments[@($plan.RunnerArguments).IndexOf('t6ProfileIterations')+1] -ne '3' -or
    !(Select-String -LiteralPath "$source/collect.log" -Pattern '^OK \(3 tests\)' -Quiet)) {
    throw 'Historical test execution scope differs.'
}
foreach ($item in @($archived.rawInputs)+@($archived.outputs)) {
    if ((Get-FileHash -LiteralPath $item.Path).Hash -ne $item.Hash) { throw 'Historical artifact checksum differs.' }
}
if ($archived.rawInputs.Count -ne 3 -or $archived.outputs.Count -ne 3) { throw 'Historical artifact set is incomplete.' }
$null = & "$PSScriptRoot/select_profile_journeys.ps1" -Manifest $manifestPath -ProfileOutputDirectory "$source/raw"
$compilation = Get-Content -LiteralPath "$source/benchmark-evidence.json" -Raw | ConvertFrom-Json
if (@($compilation.PSObject.Properties | Where-Object { $_.Value.profileInstallerSuccess -eq 1 -and $_.Value.speedProfilePerformed -eq 1 }).Count -ne 5) {
    throw 'Missing historical compilation evidence.'
}
function Describe-Artifact($path) {
    [pscustomobject]@{path=(Resolve-Path -LiteralPath $path).Path;sha256=(Get-FileHash -LiteralPath $path).Hash}
}
[pscustomobject]@{
    baseline=Describe-Artifact "$source/merged/baseline-prof.txt"
    startup=Describe-Artifact "$source/merged/startup-prof.txt"
    apk=Describe-Artifact "$source/M1-benchmarkRelease.apk"
    compilationEvidence=Describe-Artifact "$source/benchmark-evidence.json"
}
