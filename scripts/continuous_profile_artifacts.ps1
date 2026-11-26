param(
    [Parameter(Mandatory)] [string]$Output,
    [Parameter(Mandatory)] [string]$Manifest,
    [Parameter(Mandatory)] [string]$JourneyCatalog,
    [Parameter(Mandatory)] [string]$Serial,
    $ReuseArtifacts,
    [switch]$Smoke
)

$ErrorActionPreference = 'Stop'
$repo = Split-Path $PSScriptRoot -Parent
$target = 'com.socialapp.profile'
$device = @('-s', $Serial)
$java = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/java.exe' } else { 'java' }
$gradle = Join-Path $repo 'gradlew.bat'
$plan = & "$PSScriptRoot/select_profile_journeys.ps1" -Manifest $Manifest -JourneyCatalog $JourneyCatalog
$steps = [ordered]@{}

function Save-Step($name, $status = 'OK') {
    $steps[$name] = $status
    $steps | ConvertTo-Json | Set-Content -LiteralPath "$Output/artifact-steps.json" -Encoding UTF8
}

function Run-Gradle($wrapper, $log, $arguments) {
    # Windows PowerShell wraps native stderr as errors, including compiler notes.
    $ErrorActionPreference = 'Continue'
    & $wrapper @arguments --console=plain *> $log
    if ($LASTEXITCODE -ne 0) { throw "Gradle failed. See $log." }
}

function Install-Apk($apk) {
    & adb @device install -r $apk | Out-Host
    if ($LASTEXITCODE -ne 0) { throw "Installation failed: $apk." }
}

function Run-Tests($arguments, $log) {
    $lines = @(& adb @device shell am instrument -w -r @arguments $plan.Runner)
    $code = $LASTEXITCODE
    $lines | Set-Content -LiteralPath $log -Encoding UTF8
    if ($code -ne 0 -or !($lines -match "^OK \($($plan.JourneyIds.Count) tests\)\s*$")) { throw "Instrumentation failed. See $log." }
}

function Artifact($path) {
    [pscustomobject]@{path=(Resolve-Path -LiteralPath $path).Path;sha256=(Get-FileHash -LiteralPath $path).Hash}
}

if (!$ReuseArtifacts) {
    Save-Step collection RUNNING
    Push-Location $repo
    try {
        Run-Gradle $gradle "$Output/generator-build.log" @(':app:assembleNonMinifiedRelease',':baselineprofile:assembleNonMinifiedRelease')
    } finally { Pop-Location }
    $collector = (Get-ChildItem "$repo/baselineprofile/build/outputs/apk/nonMinifiedRelease" -Filter '*.apk' -File | Select-Object -First 1).FullName
    Install-Apk "$repo/app/build/outputs/apk/nonMinifiedRelease/app-nonMinifiedRelease.apk"
    Install-Apk $collector
    $plan = & "$PSScriptRoot/select_profile_journeys.ps1" -Manifest $Manifest -JourneyCatalog $JourneyCatalog -ListTests -Serial $Serial
    $plan | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath "$Output/collection-plan.json" -Encoding UTF8
    if ((& adb @device shell "if test -e '$($plan.DeviceOutputDirectory)'; then echo exists; else echo absent; fi").Trim() -ne 'absent') {
        throw 'Device collection directory already exists.'
    }
    $since = (& adb @device shell date '+%m-%d_%H:%M:%S.000').Trim().Replace('_',' ')
    Run-Tests $plan.RunnerArguments "$Output/collection.log"
    & adb @device logcat -d -T $since -s TestRunner Benchmark | Set-Content -LiteralPath "$Output/collection-device.log" -Encoding UTF8
    & adb @device pull $plan.DeviceOutputDirectory "$Output/raw" | Out-Host
    if ($LASTEXITCODE -ne 0) { throw 'Profile pull failed.' }
    $null = & "$PSScriptRoot/select_profile_journeys.ps1" -Manifest $Manifest -JourneyCatalog $JourneyCatalog -ProfileOutputDirectory "$Output/raw"
    $catalog = Get-Content -LiteralPath $JourneyCatalog -Raw | ConvertFrom-Json
    $actual = @{}
    $method = $null
    foreach ($line in Get-Content -LiteralPath "$Output/collection-device.log") {
        if ($line -match 'TestRunner: started: (\w+)\(com.socialapp.baselineprofile.BaselineProfileGenerator\)') {
            $method = $Matches[1]; $actual[$method] = 0
        } elseif ($method -and $line -match 'Benchmark: LaunchState: COLD') { $actual[$method]++ }
        elseif ($line -match 'TestRunner: finished: \w+\(com.socialapp.baselineprofile.BaselineProfileGenerator\)') { $method = $null }
    }
    $observed = @(foreach ($id in $plan.JourneyIds) {
        $journey = $catalog.journeys | Where-Object id -CEQ $id
        if ($actual[$journey.testMethod] -ne $catalog.profileIterationsPerJourney) { throw "Unexpected collection count: $id." }
        [pscustomobject]@{id=$id;method=$journey.testMethod;iterations=$actual[$journey.testMethod]}
    })
    $inputs = @(foreach ($name in $plan.ExpectedProfileFiles) {
        (Get-ChildItem -LiteralPath "$Output/raw" -Recurse -File -Filter $name).FullName
    })
    $inputs | ConvertTo-Json | Set-Content -LiteralPath "$Output/merge-inputs.json" -Encoding UTF8
    [pscustomobject]@{collectedAt=[DateTimeOffset]::Now.ToString('o');serial=$Serial;journeys=$observed;files=@($inputs | ForEach-Object { Artifact $_ })} |
        ConvertTo-Json -Depth 8 | Set-Content -LiteralPath "$Output/collection-provenance.json" -Encoding UTF8
    Save-Step collection

    # Use an isolated checkout-shaped workspace and the existing AndroidX merge task.
    $work = (New-Item -ItemType Directory -Path (Join-Path $repo ('build/cp-'+[guid]::NewGuid().ToString('N').Substring(0,10)))).FullName
    Copy-Item -LiteralPath @("$repo/settings.gradle","$repo/build.gradle","$repo/gradle.properties","$repo/gradlew.bat","$repo/local.properties") -Destination $work
    Copy-Item -LiteralPath "$repo/gradle" -Destination $work -Recurse
    foreach ($module in 'app','baselineprofile') {
        $destination = (New-Item -ItemType Directory -Path "$work/$module").FullName
        Copy-Item -LiteralPath @("$repo/$module/build.gradle","$repo/$module/src") -Destination $destination -Recurse
    }
    Copy-Item -LiteralPath "$repo/app/proguard-rules.pro" -Destination "$work/app"
    @'
import groovy.json.JsonSlurper
gradle.projectsEvaluated {
    def app = gradle.rootProject.project(':app')
    def list = app.providers.gradleProperty('continuousMergeInputs').get()
    def inputs = new JsonSlurper().parseText(new File(list).getText('UTF-8').replaceFirst('^\uFEFF', '')).collect { new File(it) }
    app.tasks.named('mergeReleaseBaselineProfile').configure { task ->
        task.baselineProfileFileCollection.setFrom(inputs)
    }
}
'@ | Set-Content -LiteralPath "$Output/merge-selected.init.gradle" -Encoding ASCII

    Push-Location $work
    try {
        Save-Step merge RUNNING
        Run-Gradle '.\gradlew.bat' "$Output/merge.log" @('-I',"$Output/merge-selected.init.gradle",':app:mergeReleaseBaselineProfile',
            "-PcontinuousMergeInputs=$Output/merge-inputs.json",'-x',':baselineprofile:collectNonMinifiedReleaseBaselineProfile')
        $merged = 'app/build/intermediates/baselineprofiles/release/merged'
        Copy-Item -LiteralPath @("$merged/baseline-prof.txt","$merged/startup-prof.txt") -Destination $Output
        function Read-Descriptors($paths) {
            $set = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
            foreach ($path in $paths) {
                foreach ($line in Get-Content -LiteralPath $path) {
                    if ($line -cmatch '^[HSP]*([\[L].*)$') { [void]$set.Add($Matches[1]) }
                }
            }
            return ,$set
        }
        $startupSource = @($inputs | Where-Object { $_ -like '*-startup-prof.txt' })
        $baselineSet = Read-Descriptors @("$Output/baseline-prof.txt")
        $startupSet = Read-Descriptors @("$Output/startup-prof.txt")
        if (!$baselineSet.Count -or !$startupSet.Count -or
            !$baselineSet.SetEquals((Read-Descriptors $inputs)) -or
            !$startupSet.SetEquals((Read-Descriptors $startupSource))) { throw 'Merged rules differ from selected inputs.' }
        @{baseline=$baselineSet.Count;startup=$startupSet.Count} | ConvertTo-Json | Set-Content -LiteralPath "$Output/profile-counts.json"
        Save-Step merge
        Copy-Item -LiteralPath "$Output/baseline-prof.txt" -Destination 'app/src/release/generated/baselineProfiles/baseline-prof.txt' -Force
        Save-Step build RUNNING
        Run-Gradle '.\gradlew.bat' "$Output/build.log" @(':app:assembleBenchmarkRelease','-PbenchmarkStartupProfile=false')
        Copy-Item -LiteralPath 'app/build/outputs/apk/benchmarkRelease/app-benchmarkRelease.apk' -Destination "$Output/benchmark.apk"
        Save-Step build
    } finally { Pop-Location }
    $artifacts = @{baseline=(Artifact "$Output/baseline-prof.txt");startup=(Artifact "$Output/startup-prof.txt");apk=(Artifact "$Output/benchmark.apk")}
} else {
    $artifacts = $ReuseArtifacts
    Save-Step collection REUSED
    Save-Step merge REUSED
    Save-Step build REUSED
}

Save-Step binaryVerification RUNNING
& $java "$PSScriptRoot/VerifyDexProfile.java" $artifacts.apk.path | Tee-Object -FilePath "$Output/binary-verification.log" | Out-Host
if ($LASTEXITCODE -ne 0) { throw 'APK Baseline Profile is incompatible with its DEX.' }
Save-Step binaryVerification
$feedback = $null
if (!$ReuseArtifacts -or $Smoke) {
    Save-Step compilation RUNNING
    Install-Apk $artifacts.apk.path
    $installed = (& adb @device shell pm path $target | Select-Object -First 1) -replace '^package:'
    $deviceHash = ((& adb @device shell sha256sum $installed) -split ' ')[0]
    if ($deviceHash -ne $artifacts.apk.sha256) { throw 'Installed APK checksum mismatch.' }
    $evidence = @(
        & adb @device shell am broadcast -f 0x20 -a androidx.profileinstaller.action.INSTALL_PROFILE -n "$target/androidx.profileinstaller.ProfileInstallReceiver"
        & adb @device shell cmd package compile -m speed-profile -f $target
        & adb @device shell dumpsys package dexopt | Select-String -SimpleMatch "[$target]" -Context 0,4 | Out-String
    )
    $evidence | Set-Content -LiteralPath "$Output/compilation.log" -Encoding UTF8
    if (!($evidence -match 'result=1\s*$') -or !($evidence -match 'status=speed-profile')) {
        throw 'ProfileInstaller or ART speed-profile verification failed.'
    }
    if (!$ReuseArtifacts) { $artifacts.compilationEvidence = Artifact "$Output/compilation.log" }
    Save-Step compilation
    if ($Smoke) {
        if ($ReuseArtifacts) {
            Push-Location $repo
            try { Run-Gradle $gradle "$Output/smoke-build.log" @(':baselineprofile:assembleNonMinifiedRelease') }
            finally { Pop-Location }
            $collector = (Get-ChildItem "$repo/baselineprofile/build/outputs/apk/nonMinifiedRelease" -Filter '*.apk' -File | Select-Object -First 1).FullName
            Install-Apk $collector
        }
        $remoteFeedback = "/sdcard/Android/data/$target/files/metrics/navigation_v1.csv"
        $oldRows = @(& adb @device shell "if test -f '$remoteFeedback'; then cat '$remoteFeedback'; fi")
        $seen = @{}
        if ($oldRows.Count -gt 1) {
            foreach ($row in $oldRows | ConvertFrom-Csv) { $seen[$row.navigation_id] = $true }
        }
        $methodFilter = (($plan.RunnerArguments[2] -split ',') -replace 'BaselineProfileGenerator#','JourneyUiSmokeTest#') -join ','
        Run-Tests @('-e','class',$methodFilter,'-e','targetAppId',$target) "$Output/ui-smoke.log"
        Save-Step uiSmoke
        $feedbackPath = "$Output/feedback-navigation_v1.csv"
        $feedback = [ordered]@{source='LIVE';fixture='guest/local/P0';eligibleForReplayRanking=$false;newLiveEvents=0}
        $rows = @(& adb @device shell "if test -f '$remoteFeedback'; then cat '$remoteFeedback'; fi")
        if ($rows.Count -gt 1) {
            $fresh = @($rows | ConvertFrom-Csv | Where-Object { $_.source -ceq 'LIVE' -and !$seen.ContainsKey($_.navigation_id) })
            if ($fresh.Count) {
                $fresh | Export-Csv -LiteralPath $feedbackPath -Encoding UTF8 -NoTypeInformation
                $feedback.export = Artifact $feedbackPath
                $feedback.newLiveEvents = $fresh.Count
            }
        }
        if ($feedback.newLiveEvents) { Save-Step feedback EXPORTED_LIVE } else { Save-Step feedback NOT_AVAILABLE }
    }
}
[pscustomobject]@{artifacts=$artifacts;feedback=$feedback}
