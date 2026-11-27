$ErrorActionPreference = 'Stop'
$scripts = Split-Path $PSScriptRoot -Parent
$repo = Split-Path $scripts -Parent
$root = Join-Path $repo ('build\step-t4b2-fixture\' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $root -Force | Out-Null
$manifest = Get-Content (Join-Path $scripts 'replay_workload_t3.json') -Raw | ConvertFrom-Json
$asset = Get-Content (Join-Path $repo $manifest.asset) -Raw | ConvertFrom-Json
$navigation = New-Object Collections.Generic.List[object]
$ledger = New-Object Collections.Generic.List[object]

function Metric($kind, $id, $session, $trace, $policy, $hit, $latency, $bytes, $waste, $cancelled, $valid) {
    [pscustomobject][ordered]@{
        schema_version=2; row_type=$kind; run_id=$id; session_id=$session; replay_session_id=$trace
        load_mode=$(if ($kind -eq 'FOREGROUND_LOAD') {'SIMULATED'}); hit_type=$hit; load_latency_ms=$latency
        simulated_foreground_bytes=$bytes; simulated_wasted_prefetch_bytes=$waste; cancelled_prefetch_count=$cancelled
        policy=$policy; network_profile=$(if ($policy) {'LTE'}); payload_scale=$(if ($policy) {1})
        run_valid=$valid; run_status=$(if ($kind -eq 'RUN_END') {'RUN_FINISHED'})
        confirmed_steps=$(if ($kind -eq 'RUN_END') {109}); navigation_events=$(if ($kind -eq 'RUN_END') {109})
        time_scale=$(if ($kind -eq 'RUN_END') {'2.0'})
    }
}

function Outcome($id,$mode,$kind,$key,$selected,$payload,$bytes,$completed,$waste,$initial,$final,$updates) {
    [pscustomobject][ordered]@{
        schema_version=1; run_id=$id; session_id=''; replay_session_id=''; mode=$mode; event_type=$kind
        prefetch_id=$key; selected=$selected; scaled_payload_bytes=$payload; outcome_bytes=$bytes
        observation_index=''; old_lambda=$initial; new_lambda=$final; waste_ratio=''
        miss_rate=''; resolved_count=''; successful_load_count=$(if ($kind -eq 'RUN_SUMMARY') {2})
        resolved_bytes=$completed; wasted_bytes=$waste; completed_bytes=$completed; update_count=$updates; reason=''
    }
}

function Fixture-Run($mode,$waste,$latency,$missingForeground=$false) {
    $id = [guid]::NewGuid().ToString()
    $first = $null
    $metrics = New-Object Collections.Generic.List[object]
    foreach ($traceId in $manifest.evaluation_session_ids) {
        $session = [guid]::NewGuid().ToString()
        if (!$first) { $first = $session }
        $trace = $asset.sessions | Where-Object session_id -eq $traceId
        $previous = ''
        foreach ($step in $trace.steps) {
            $navigation.Add([pscustomobject][ordered]@{
                schema_version=1; navigation_id=[guid]::NewGuid(); source='REPLAY'; run_id=$id
                session_id=$session; replay_session_id=$traceId; sequence=$step.step
                from_screen=$previous; to_screen=$step.screen; timestamp_elapsed_ms=$navigation.Count
            })
            $previous = $step.screen
        }
        $metrics.Add((Metric 'CACHE_WASTE' $id $session $traceId '' '' '' '' $(if ($first -eq $session) {$waste} else {0}) 0 ''))
    }
    $policy = if ($mode -eq 'P0') {'P0'} else {'P5'}
    $traceId = $manifest.evaluation_session_ids[0]
    $firstHit = if ($mode -eq 'P0') {'MISS'} else {'FULL_HIT'}
    $firstBytes = if ($mode -eq 'P0') {100} else {0}
    $metrics.Add((Metric 'FOREGROUND_LOAD' $id $first $traceId $policy $firstHit $latency $firstBytes '' '' ''))
    $metrics.Add((Metric 'FOREGROUND_LOAD' $id $first $traceId $policy 'MISS' (2*$latency) $(if (!$missingForeground) {100}) '' '' ''))
    $metrics.Add((Metric 'RUN_END' $id '' '' '' '' '' '' '' '' 'true'))
    $metrics | Export-Csv (Join-Path $root "metrics_v2_t4_evaluation_attempt_$id.csv") -NoTypeInformation
    if ($mode -ne 'P0') {
        foreach ($kind in @('DECISION','DISPATCHED','COMPLETED','USED')) {
            $ledger.Add((Outcome $id $mode $kind '1' 'true' 100 $(if ($kind -in @('COMPLETED','USED')) {100}) '' '' '' '' ''))
        }
        foreach ($kind in @('DECISION','DISPATCHED','COMPLETED','WASTED')) {
            $ledger.Add((Outcome $id $mode $kind '2' 'true' $waste $(if ($kind -in @('COMPLETED','WASTED')) {$waste}) '' '' '' '' ''))
        }
        $initial = if ($mode -eq 'LEGACY') {'0.000005'} else {'0.000075'}
        $final = if ($mode -eq 'ADAPTIVE_LAMBDA') {'0.00009'} else {$initial}
        $updates = if ($mode -eq 'ADAPTIVE_LAMBDA') {1} else {0}
        $ledger.Add((Outcome $id $mode 'RUN_SUMMARY' '' '' '' '' (100+$waste) $waste $initial $final $updates))
    }
    $id
}

function Assert-Equal($actual,$expected,$label) {
    if ($actual -cne $expected) { throw "$label (expected '$expected', got '$actual')" }
}

$p0=Fixture-Run 'P0' 0 20
$legacy=Fixture-Run 'LEGACY' 100 15
$fixed=Fixture-Run 'FIXED_LAMBDA' 100 10
$adaptive1=Fixture-Run 'ADAPTIVE_LAMBDA' 50 8
$adaptive2=Fixture-Run 'ADAPTIVE_LAMBDA' 75 12 $true
$navigation | Export-Csv (Join-Path $root 'navigation_v1.csv') -NoTypeInformation
$ledger | Export-Csv (Join-Path $root 'p5_v1.csv') -NoTypeInformation
& (Join-Path $scripts 'analyze_metrics.ps1') -InputDirectory $root | Out-Null
$compare = Join-Path $scripts 'compare_t4_evaluation.ps1'
$output = & $compare -InputDirectory $root -E0 $p0 -E1 $legacy -E2 $fixed -E3 @($adaptive1,$adaptive2)
$rows = Import-Csv (Join-Path $output 'evaluation_comparison.csv')
$deltas = Import-Csv (Join-Path $output 'evaluation_deltas.csv')
$variation = Import-Csv (Join-Path $output 'evaluation_variation.csv')
Assert-Equal $rows.Count 5 'Preserve all repeats'
Assert-Equal (@($rows.time_scale | Sort-Object -Unique) -join ',') '2' 'Use the recorded 2x speed'
$baseline = $rows | Where-Object configuration -eq 'E0'
Assert-Equal $baseline.completed_prefetch_bytes '0' 'P0 dispatches no prefetch'
Assert-Equal $baseline.waste_ratio '' 'P0 waste ratio N/A'
Assert-Equal $baseline.lambda_initial '' 'P0 lambda N/A'
$first = $rows | Where-Object run_id -eq $adaptive1
Assert-Equal $first.total_simulated_bytes '250' 'No duplicate cache-hit bytes'
Assert-Equal ($rows | Where-Object run_id -eq $adaptive2).total_simulated_bytes '' 'Missing byte stays blank'
$measurement = $variation | Where-Object { $_.configuration -eq 'E3' -and $_.metric -eq 'total_simulated_bytes' }
Assert-Equal $measurement.measured_runs '1' 'Exclude missing measurement from mean'
Assert-Equal $measurement.runs '2' 'Count both valid repeats'
$wasteDelta = $deltas | Where-Object { $_.baseline -eq 'E2' -and $_.metric -eq 'wasted_bytes' }
Assert-Equal $wasteDelta.absolute_delta '-37.5' 'Compare the mean of all repeats'
Assert-Equal ($deltas | Where-Object { $_.baseline -eq 'E2' -and $_.metric -eq 'p50_load_latency_ms' }).absolute_delta '0' 'P50'

$rejected = $false
try { & $compare -InputDirectory $root -E0 $p0 -E1 $fixed -E2 $legacy -E3 $adaptive1 | Out-Null }
catch { $rejected = $_.Exception.Message -like 'P5 mode or terminal telemetry mismatch*' }
Assert-Equal $rejected $true 'Reject reversed modes'
$adaptiveMetric = Join-Path $root "metrics_v2_t4_evaluation_attempt_$adaptive1.csv"
$mixed = Import-Csv $adaptiveMetric
($mixed | Where-Object row_type -eq 'RUN_END').time_scale = '5.0'
$mixed | Export-Csv $adaptiveMetric -NoTypeInformation
$rejected = $false
try { & $compare -InputDirectory $root -E0 $p0 -E1 $legacy -E2 $fixed -E3 @($adaptive1,$adaptive2) | Out-Null }
catch { $rejected = $_.Exception.Message -like 'Evaluation run speeds differ*' }
Assert-Equal $rejected $true 'Reject mixed 2x/5x speeds even in repeats'

($mixed | Where-Object row_type -eq 'RUN_END').time_scale = ''
$mixed | Export-Csv $adaptiveMetric -NoTypeInformation
$rejected = $false
try { & $compare -InputDirectory $root -E0 $p0 -E1 $legacy -E2 $fixed -E3 $adaptive1 | Out-Null }
catch { $rejected = $_.Exception.Message -like 'Missing or unsupported RUN_END time_scale*' }
Assert-Equal $rejected $true 'Reject missing speed metadata'

foreach ($file in (Get-ChildItem -LiteralPath $root -Filter 'metrics_v2_t4_evaluation_attempt_*.csv')) {
    $speed5 = Import-Csv -LiteralPath $file.FullName
    ($speed5 | Where-Object row_type -eq 'RUN_END').time_scale = '5.0'
    $speed5 | Export-Csv -LiteralPath $file.FullName -NoTypeInformation
}
$output5 = & $compare -InputDirectory $root -OutputDirectory (Join-Path $root 'comparison_5x') -E0 $p0 -E1 $legacy -E2 $fixed -E3 @($adaptive1,$adaptive2)
$rows5 = Import-Csv (Join-Path $output5 'evaluation_comparison.csv')
Assert-Equal (@($rows5.time_scale | Sort-Object -Unique) -join ',') '5' 'Accept an all-5x comparison'

$invalid = Import-Csv $adaptiveMetric
($invalid | Where-Object row_type -eq 'RUN_END').run_valid = 'false'
$invalid | Export-Csv $adaptiveMetric -NoTypeInformation
$rejected = $false
try { & $compare -InputDirectory $root -E0 $p0 -E1 $legacy -E2 $fixed -E3 $adaptive1 | Out-Null }
catch { $rejected = $_.Exception.Message -like 'Invalid raw CSV v2*' }
Assert-Equal $rejected $true 'Reject invalid raw RUN_END even with old analysis'
"Evaluation fixture passed. Results: $output"
