[CmdletBinding()]
param(
    [Parameter(Mandatory)] [string] $InputDirectory,
    [Parameter(Mandatory)] [guid[]] $E0,
    [Parameter(Mandatory)] [guid[]] $E1,
    [Parameter(Mandatory)] [guid[]] $E2,
    [Parameter(Mandatory)] [guid[]] $E3,
    [string] $OutputDirectory
)

$ErrorActionPreference = 'Stop'
$culture = [Globalization.CultureInfo]::InvariantCulture
$inputPath = (Resolve-Path -LiteralPath $InputDirectory).Path
$analysis = Join-Path $inputPath 'analysis'
if (!$OutputDirectory) { $OutputDirectory = $analysis }
$manifest = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'replay_workload_t3.json') -Raw | ConvertFrom-Json
$assetPath = Join-Path (Split-Path $PSScriptRoot -Parent) $manifest.asset
if ((Get-FileHash -LiteralPath $assetPath).Hash -ne $manifest.asset_sha256) { throw 'Replay asset hash changed.' }
$asset = Get-Content -LiteralPath $assetPath -Raw | ConvertFrom-Json
$expected = @(foreach ($id in $manifest.evaluation_session_ids) {
    if ($id -in $manifest.calibration_session_ids) { throw "Calibration session in evaluation: $id" }
    $session = $asset.sessions | Where-Object session_id -eq $id
    for ($i=0; $i -lt $session.steps.Count; $i++) {
        "$id/$($i+1)/$($session.steps[$i].screen)"
    }
})
if ($manifest.evaluation_session_ids.Count -ne 21 -or $expected.Count -ne 109) { throw 'Unexpected evaluation workload size.' }

$groups = [ordered]@{ E0=$E0; E1=$E1; E2=$E2; E3=$E3 }
$expectedModes = @{ E1='LEGACY'; E2='FIXED_LAMBDA'; E3='ADAPTIVE_LAMBDA' }
$ids = @($E0 + $E1 + $E2 + $E3 | ForEach-Object { $_.ToString() })
if (@($ids | Sort-Object -Unique).Count -ne $ids.Count) { throw 'Each run ID must be supplied exactly once.' }
$stats = Import-Csv -LiteralPath (Join-Path $analysis 'prefetch_summary.csv')
$p5Stats = Import-Csv -LiteralPath (Join-Path $analysis 'p5_summary.csv')
$files = @(Get-ChildItem -LiteralPath $inputPath -File -Recurse)
$navigation = @(foreach ($file in ($files | Where-Object Name -eq 'navigation_v1.csv')) { Import-Csv -LiteralPath $file.FullName })
$p5 = @(foreach ($file in ($files | Where-Object Name -eq 'p5_v1.csv')) { Import-Csv -LiteralPath $file.FullName })

function Number($value) {
    if (![string]::IsNullOrWhiteSpace($value)) { [double]::Parse($value, $culture) }
}

function Sum-OutcomeBytes($rows, $kind) {
    $sum = 0L
    foreach ($row in ($rows | Where-Object event_type -eq $kind)) {
        if ($row.outcome_bytes -notmatch '^\d+$') { throw "Missing or invalid $kind bytes in run $($row.run_id)." }
        $sum += [long]$row.outcome_bytes
    }
    return $sum
}

$comparison = @(foreach ($configuration in $groups.Keys) {
    foreach ($uuid in $groups[$configuration]) {
        $id = $uuid.ToString()
        $sourceFile = @($files | Where-Object Name -eq "metrics_v2_t4_evaluation_attempt_$id.csv")
        $validation = @($stats | Where-Object { $_.run_id -eq $id -and $_.row_type -eq 'RUN_VALIDATION' })
        if ($sourceFile.Count -ne 1 -or $validation.Count -ne 1 -or $validation[0].quality -ne 'VALID' -or
            $validation[0].run_status -ne 'RUN_FINISHED' -or $validation[0].confirmed_steps -ne '109' -or
            $validation[0].expected_navigation_events -ne '109' -or $validation[0].observed_navigation_events -ne '109') {
            throw "Run $id is not a complete Evaluation run. Refresh the export and analysis."
        }
        $actual = @(foreach ($row in ($navigation | Where-Object run_id -eq $id)) {
            "$($row.replay_session_id)/$($row.sequence)/$($row.to_screen)"
        })
        if (($actual -join '|') -cne ($expected -join '|')) { throw "Evaluation workload mismatch in run $id." }

        $raw = @(Import-Csv -LiteralPath $sourceFile[0].FullName)
        $end = @($raw | Where-Object row_type -eq 'RUN_END')
        if (@($raw | Where-Object run_id -ne $id).Count -or $end.Count -ne 1 -or $end[0].run_valid -ne 'true' -or
            $end[0].run_status -ne 'RUN_FINISHED' -or $end[0].confirmed_steps -ne '109' -or $end[0].navigation_events -ne '109') {
            throw "Invalid raw CSV v2 for run $id."
        }
        $timeScale = Number $end[0].time_scale
        if ($timeScale -notin @(2,5)) {
            throw "Missing or unsupported RUN_END time_scale in run $id. Use a run with recorded speed (2x or 5x)."
        }
        $policy = if ($configuration -eq 'E0') { 'P0' } else { 'P5' }
        $loads = @($raw | Where-Object row_type -eq 'FOREGROUND_LOAD')
        if (@($loads | Where-Object {
            $_.policy -ne $policy -or $_.load_mode -ne 'SIMULATED' -or $_.network_profile -ne 'LTE' -or
            (Number $_.payload_scale) -ne 1 -or $_.hit_type -notin @('FULL_HIT','LATE_HIT','MISS')
        }).Count) { throw "Policy, network, payload or hit classification mismatch in run $id ($configuration)." }
        $loadStats = @($stats | Where-Object { $_.run_id -eq $id -and $_.row_type -eq 'PREFETCH' -and $_.aggregation_level -eq 'RUN' })
        if ($loads.Count -and ($loadStats.Count -ne 1 -or [int]$loadStats[0].foreground_loads -ne $loads.Count)) {
            throw "Foreground load totals do not reconcile in run $id."
        }
        $loadSummary = if ($loadStats.Count) { $loadStats[0] }
        $wasteRows = @($raw | Where-Object row_type -eq 'CACHE_WASTE')
        if (@($wasteRows | Where-Object {
            $_.simulated_wasted_prefetch_bytes -notmatch '^\d+$' -or $_.cancelled_prefetch_count -notmatch '^\d+$'
        }).Count) { throw "Missing cache measurements in run $id." }
        $waste = [long](($wasteRows | ForEach-Object { [long]$_.simulated_wasted_prefetch_bytes } | Measure-Object -Sum).Sum)
        $cancelled = [long](($wasteRows | ForEach-Object { [long]$_.cancelled_prefetch_count } | Measure-Object -Sum).Sum)
        $ledger = @($p5 | Where-Object run_id -eq $id)
        $summary = @($p5Stats | Where-Object run_id -eq $id)
        $completed = 0L
        $used = 0L
        $wasteRatio = $null
        $initial = $null
        $final = $null
        $updates = $null
        if ($policy -eq 'P0') {
            if ($ledger.Count -or $summary.Count -or $waste -or $cancelled -or
                @($loads | Where-Object hit_type -ne 'MISS').Count) { throw "Prefetch data in P0 run $id." }
        } else {
            $mode = $expectedModes[$configuration]
            $footer = @($ledger | Where-Object event_type -eq 'RUN_SUMMARY')
            if ($summary.Count -ne 1 -or $summary[0].quality -ne 'VALID' -or
                $summary[0].p5_summary_present -ne 'True' -or $summary[0].mode -ne $mode -or $footer.Count -ne 1 -or
                @($wasteRows.replay_session_id | Sort-Object -Unique).Count -ne 21 -or
                @($ledger | Where-Object mode -ne $mode).Count -or [int]$footer[0].successful_load_count -ne $loads.Count) {
                throw "P5 mode or terminal telemetry mismatch in run $id ($configuration)."
            }
            foreach ($attempt in ($ledger | Where-Object prefetch_id | Group-Object prefetch_id)) {
                $decision = @($attempt.Group | Where-Object event_type -eq 'DECISION')
                $dispatch = @($attempt.Group | Where-Object event_type -eq 'DISPATCHED')
                $skip = @($attempt.Group | Where-Object event_type -eq 'SKIPPED')
                $done = @($attempt.Group | Where-Object event_type -eq 'COMPLETED')
                $terminal = @($attempt.Group | Where-Object event_type -in @('USED','WASTED','CANCELLED','FAILED'))
                $selected = [int]($decision[0].selected -eq 'true')
                if ($decision.Count -ne 1 -or $dispatch.Count + $skip.Count -ne $selected -or
                    $terminal.Count -ne $dispatch.Count -or $done.Count -ne @($terminal | Where-Object event_type -in @('USED','WASTED')).Count -or
                    ($done.Count -and ($done[0].outcome_bytes -ne $terminal[0].outcome_bytes -or $done[0].outcome_bytes -ne $decision[0].scaled_payload_bytes)) -or
                    (!$done.Count -and $terminal.Count -and $terminal[0].outcome_bytes)) {
                    throw "Invalid prefetch lifecycle for $id/$($attempt.Name)."
                }
            }
            $completed = Sum-OutcomeBytes $ledger 'COMPLETED'
            $used = Sum-OutcomeBytes $ledger 'USED'
            $footer = $footer[0]
            if ($footer.completed_bytes -notmatch '^\d+$' -or $footer.resolved_bytes -notmatch '^\d+$' -or
                $footer.wasted_bytes -notmatch '^\d+$' -or $completed -ne ($used + $waste) -or
                $completed -ne [long]$footer.completed_bytes -or $completed -ne [long]$footer.resolved_bytes -or
                $waste -ne [long]$footer.wasted_bytes -or $waste -ne (Sum-OutcomeBytes $ledger 'WASTED') -or
                $cancelled -ne @($ledger | Where-Object event_type -eq 'CANCELLED').Count) {
                throw "Prefetch accounting mismatch in run $id."
            }
            if ($completed -gt 0) { $wasteRatio = $waste / $completed }
            $initial = Number $footer.old_lambda
            $final = Number $footer.new_lambda
            $updates = Number $footer.update_count
            $requiredInitial = if ($mode -eq 'LEGACY') { 0.000005 } else { 0.000075 }
            if ($initial -ne $requiredInitial -or $null -eq $final -or $null -eq $updates -or
                ($mode -ne 'ADAPTIVE_LAMBDA' -and ($initial -ne $final -or $updates -ne 0)) -or
                ($mode -eq 'ADAPTIVE_LAMBDA' -and ($final -lt 0.000015 -or $final -gt 0.004))) {
                throw "Lambda configuration mismatch in run $id."
            }
        }
        $foreground = if (!$loads.Count) { 0L } else { Number $loadSummary.simulated_foreground_bytes }
        $byteSamples = if ($loads.Count) { Number $loadSummary.foreground_byte_samples } else { 0 }
        $total = if ($null -ne $foreground -and $byteSamples -eq $loads.Count) { $foreground + $completed }
        [pscustomobject][ordered]@{
            configuration=$configuration; run_id=$id; policy=$policy; p5_mode=$expectedModes[$configuration]
            network='LTE'; payload_scale=1; time_scale=$timeScale
            confirmed_steps=109; navigation_events=109; successful_foreground_loads=$loads.Count
            full_hit=@($loads | Where-Object hit_type -eq 'FULL_HIT').Count
            late_hit=@($loads | Where-Object hit_type -eq 'LATE_HIT').Count
            miss=@($loads | Where-Object hit_type -eq 'MISS').Count
            hit_rate=(Number $loadSummary.hit_rate); mean_load_latency_ms=(Number $loadSummary.mean_load_latency_ms)
            p50_load_latency_ms=(Number $loadSummary.p50_load_latency_ms); p95_load_latency_ms=(Number $loadSummary.p95_load_latency_ms)
            completed_prefetch_bytes=$completed; used_prefetch_bytes=$used; wasted_bytes=$waste
            waste_ratio=$wasteRatio; cancelled_prefetch_count=$cancelled; simulated_foreground_byte_samples=$byteSamples
            simulated_foreground_bytes=$foreground; total_simulated_bytes=$total
            lambda_initial=$initial; lambda_final=$final; lambda_update_count=$updates
        }
    }
})

if (@($comparison.time_scale | Sort-Object -Unique).Count -ne 1) {
    $runSpeeds = ($comparison | ForEach-Object { "$($_.configuration)/$($_.run_id)=$($_.time_scale)x" }) -join ', '
    throw "Evaluation run speeds differ. Supply runs at the same time_scale. $runSpeeds"
}

$columns = @($comparison[0].PSObject.Properties.Name)
$fields = $columns[[array]::IndexOf($columns, 'confirmed_steps')..($columns.Count-1)]
$variation = @(foreach ($group in ($comparison | Group-Object configuration)) {
    foreach ($metric in $fields) {
        $sample = @($group.Group | ForEach-Object { $_.$metric } | Where-Object { $null -ne $_ })
        $measures = $sample | Measure-Object -Average -Minimum -Maximum
        [pscustomobject]@{
            configuration=$group.Name; metric=$metric; runs=$group.Count; measured_runs=$sample.Count
            mean=$(if ($sample.Count) { $measures.Average }); min=$(if ($sample.Count) { $measures.Minimum })
            max=$(if ($sample.Count) { $measures.Maximum })
        }
    }
})
$deltas = @(foreach ($reference in @('E2','E0','E1')) {
    foreach ($value in ($variation | Where-Object configuration -eq 'E3')) {
        $base = $variation | Where-Object { $_.configuration -eq $reference -and $_.metric -eq $value.metric }
        $delta = if ($null -ne $base.mean -and $null -ne $value.mean) { $value.mean - $base.mean }
        [pscustomobject]@{
            baseline=$reference; treatment='E3'; metric=$value.metric
            baseline_measured_runs=$base.measured_runs; treatment_measured_runs=$value.measured_runs
            baseline_mean=$base.mean; treatment_mean=$value.mean; absolute_delta=$delta
            relative_delta_percent=$(if ($null -ne $delta -and $base.mean -ne 0) { 100 * $delta / $base.mean })
        }
    }
})

function Export-Table($rows, $path) {
    $rows | ForEach-Object {
        $formatted = [ordered]@{}
        foreach ($property in $_.PSObject.Properties) {
            $value = $property.Value
            $formatted[$property.Name] = if ($value -is [IFormattable]) { $value.ToString($null, $culture) } else { $value }
        }
        [pscustomobject]$formatted
    } | Export-Csv -LiteralPath $path -NoTypeInformation -Encoding UTF8
}
New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
Export-Table $comparison (Join-Path $OutputDirectory 'evaluation_comparison.csv')
Export-Table $variation (Join-Path $OutputDirectory 'evaluation_variation.csv')
Export-Table $deltas (Join-Path $OutputDirectory 'evaluation_deltas.csv')
(Resolve-Path -LiteralPath $OutputDirectory).Path
