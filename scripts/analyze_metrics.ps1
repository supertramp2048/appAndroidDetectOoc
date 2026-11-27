[CmdletBinding()]
param(
    [Parameter(Mandatory)] [string] $InputDirectory,
    [string] $OutputDirectory
)

$ErrorActionPreference = 'Stop'
$culture = [Globalization.CultureInfo]::InvariantCulture
$inputPath = (Resolve-Path -LiteralPath $InputDirectory).Path
if (!$OutputDirectory) { $OutputDirectory = Join-Path $inputPath 'analysis' }
$files = @(Get-ChildItem -LiteralPath $inputPath -Recurse -File)
if (!($files | Where-Object Name -match '^(metrics_v2_.*|navigation_v1|performance_v1|p5_v1)\.csv$')) {
    throw "No telemetry CSV files in $inputPath"
}
$screens = @('FEED_NEWEST','FEED_HOT','FEED_FOLLOWING','SEARCH','PROFILE','FACT_CHECK','POST_DETAIL','COMMENTS','SETTINGS')

function Read-Telemetry($selected, $version, $required) {
    foreach ($file in $selected) {
        $header = Get-Content -LiteralPath $file.FullName -TotalCount 1
        $columns = @($header -split ',' | ForEach-Object { $_.Trim('"') })
        foreach ($column in (@('schema_version') + $required)) {
            if ($column -cnotin $columns) { throw "Missing column '$column' in $($file.FullName)" }
        }
        foreach ($row in (Import-Csv -LiteralPath $file.FullName)) {
            if ($row.schema_version -cne $version) { throw "Unsupported schema in $($file.FullName)" }
            $row
        }
    }
}

function Numbers($rows, $field) {
    foreach ($row in $rows) {
        $value = $row.$field
        if (![string]::IsNullOrWhiteSpace($value)) {
            $number = [double]::Parse($value, $culture)
            if ([double]::IsNaN($number) -or [double]::IsInfinity($number) -or $number -lt 0) {
                throw "Invalid measurement in '$field'."
            }
            $number
        }
    }
}

function Get-Stats($numbers) {
    if ($numbers.Count -eq 0) { return }
    $sorted = @($numbers | Sort-Object)
    $measured = $sorted | Measure-Object -Sum -Maximum
    # Linear interpolation at index (n - 1) * p.
    $percentiles = foreach ($p in @(0.50, 0.95)) {
        $index = ($sorted.Count - 1) * $p
        $lower = [int][Math]::Floor($index)
        $upper = [int][Math]::Ceiling($index)
        $sorted[$lower] + ($sorted[$upper] - $sorted[$lower]) * ($index - $lower)
    }
    @{
        Count = $sorted.Count; Sum = $measured.Sum; Mean = $measured.Sum / $sorted.Count
        P50 = $percentiles[0]; P95 = $percentiles[1]; Max = $measured.Maximum
    }
}

function Write-Table($rows, [string[]] $columns, $path) {
    if ($rows.Count -eq 0) {
        Set-Content -LiteralPath $path -Value ($columns -join ',') -Encoding UTF8
    } else {
        $rows | ForEach-Object {
            $row = $_
            $record = [ordered]@{}
            foreach ($column in $columns) {
                $value = $row.$column
                $record[$column] = if ($value -is [IFormattable]) { $value.ToString($null, $culture) } else { $value }
            }
            [pscustomobject] $record
        } | Export-Csv -LiteralPath $path -NoTypeInformation -Encoding UTF8
    }
}

$prefetch = @(Read-Telemetry @($files | Where-Object Name -like 'metrics_v2_*.csv') '2' @(
    'row_type','run_id','session_id','replay_session_id','load_mode','hit_type','load_latency_ms',
    'simulated_foreground_bytes','simulated_wasted_prefetch_bytes','cancelled_prefetch_count',
    'policy','network_profile','payload_scale','run_valid','run_status','confirmed_steps','navigation_events'))
$navigation = @(Read-Telemetry @($files | Where-Object Name -eq 'navigation_v1.csv') '1' @(
    'source','run_id','session_id','replay_session_id','navigation_id','sequence','from_screen','to_screen','timestamp_elapsed_ms'))
$performance = @(Read-Telemetry @($files | Where-Object Name -eq 'performance_v1.csv') '1' @(
    'process_id','event_type','run_id','session_id','replay_session_id','event_source','network_mode','screen','outcome'))
$p5 = @(Read-Telemetry @($files | Where-Object Name -eq 'p5_v1.csv') '1' @(
    'run_id','mode','event_type','prefetch_id','selected','outcome_bytes','observation_index',
    'old_lambda','new_lambda','waste_ratio','miss_rate','resolved_count','successful_load_count','resolved_bytes',
    'wasted_bytes','completed_bytes','update_count','reason'))
$p5ByRun = @{}
foreach ($group in ($p5 | Where-Object run_id | Group-Object run_id)) {
    $p5ByRun[$group.Name] = @($group.Group)
}

$ends = @{}
$replayIds = @{}
foreach ($row in $prefetch) {
    if ($row.row_type -eq 'RUN_END') { $ends[$row.run_id] = @($ends[$row.run_id] | Where-Object { $_ }) + $row }
    if (($row.row_type -eq 'RUN_END' -or $row.replay_session_id) -and $row.run_id) { $replayIds[$row.run_id] = $true }
}
foreach ($row in ($navigation + $performance)) {
    if (($row.source -eq 'REPLAY' -or $row.event_source -eq 'REPLAY') -and $row.run_id) { $replayIds[$row.run_id] = $true }
}
foreach ($run in $p5ByRun.Keys) { $replayIds[$run] = $true }

$replayNavigation = @{}
foreach ($group in ($navigation | Where-Object source -eq 'REPLAY' | Group-Object run_id)) {
    $replayNavigation[$group.Name] = @($group.Group)
}

function Test-Navigation($events) {
    $ids = @{}
    if (@($events | Where-Object { $_.sequence -notmatch '^\d{1,18}$' -or
        $_.timestamp_elapsed_ms -notmatch '^\d{1,18}$' }).Count) { return $false }
    if (@($events | Group-Object replay_session_id | Where-Object {
        @($_.Group.session_id | Sort-Object -Unique).Count -ne 1
    }).Count -gt 0) { return $false }
    foreach ($session in ($events | Group-Object session_id)) {
        if (!$session.Name -or @($session.Group.replay_session_id | Sort-Object -Unique).Count -ne 1) { return $false }
        $previous = $null
        $sequence = 0L
        foreach ($row in ($session.Group | Sort-Object { [long] $_.sequence })) {
            $sequence++
            if (!$row.navigation_id -or $ids.ContainsKey($row.navigation_id) -or $row.replay_session_id -notmatch '^\d+$' -or
                $row.sequence -ne [string]$sequence -or $row.timestamp_elapsed_ms -notmatch '^\d+$' -or
                $row.to_screen -cnotin $screens) { return $false }
            if ($previous -and [long] $row.timestamp_elapsed_ms -lt [long] $previous.timestamp_elapsed_ms) { return $false }
            if ($row.from_screen -and (!$previous -or $row.from_screen -cne $previous.to_screen -or
                $row.from_screen -ceq $row.to_screen)) { return $false }
            $ids[$row.navigation_id] = $true
            $previous = $row
        }
    }
    return $true
}

$validation = @{}
$runRows = @(foreach ($run in ($replayIds.Keys | Sort-Object)) {
    $terminal = @($ends[$run] | Where-Object { $_ })
    $observed = @($replayNavigation[$run] | Where-Object { $_ })
    $expected = if ($terminal.Count -eq 1 -and $terminal[0].navigation_events -match '^\d+$') {
        [long] $terminal[0].navigation_events
    }
    $status = if (!$terminal.Count) { 'MISSING_RUN_END' }
        elseif ($terminal.Count -ne 1) { 'DUPLICATE_RUN_END' }
        elseif ($terminal[0].run_valid -ne 'true' -or $terminal[0].run_status -ne 'RUN_FINISHED') { 'INVALID_RUN_END' }
        elseif ($null -eq $expected) { 'MISSING_NAVIGATION_COUNT' }
        elseif ($expected -ne $observed.Count) { 'NAVIGATION_COUNT_MISMATCH' }
        elseif (!(Test-Navigation $observed)) { 'INVALID_NAVIGATION_SEQUENCE' }
        else { 'VALID' }
    $validation[$run] = $status
    [pscustomobject]@{
        row_type='RUN_VALIDATION'; aggregation_level='RUN'; source='REPLAY'; run_id=$run; quality=$status
        run_status=$(if ($terminal.Count -eq 1) { $terminal[0].run_status })
        confirmed_steps=$(if ($terminal.Count -eq 1) { $terminal[0].confirmed_steps })
        expected_navigation_events=$expected; observed_navigation_events=$observed.Count
    }
})

function With-Scope($rows) {
    foreach ($row in $rows) {
        [pscustomobject]@{ Level=$(if ($row.run_id) {'RUN'} else {'PROCESS'})
            Run=$row.run_id; Session=''; ReplaySession=''; Data=$row }
        if ($row.session_id) {
            [pscustomobject]@{ Level='SESSION'; Run=$row.run_id; Session=$row.session_id
                ReplaySession=$row.replay_session_id; Data=$row }
        }
    }
}

function Prefetch-Result($scope, $loads, $waste) {
    $hasLoads = $loads.Count -gt 0
    $first = if ($hasLoads) { $loads[0] }
    $source = if ($replayIds.ContainsKey($scope.Run)) { 'REPLAY' } else { 'LIVE' }
    $hits = @($loads | Where-Object hit_type -in @('FULL_HIT','LATE_HIT')).Count
    $classified = @($loads | Where-Object hit_type -in @('FULL_HIT','LATE_HIT','MISS')).Count
    $latency = Get-Stats @(Numbers $loads 'load_latency_ms')
    $foreground = Get-Stats @(Numbers $loads 'simulated_foreground_bytes')
    $wasted = Get-Stats @(Numbers $waste 'simulated_wasted_prefetch_bytes')
    $cancelled = Get-Stats @(Numbers $waste 'cancelled_prefetch_count')
    [pscustomobject]@{
        row_type=$(if ($hasLoads) {'PREFETCH'} else {'CACHE_WASTE'}); aggregation_level=$scope.Level; source=$source; run_id=$scope.Run
        session_id=$scope.Session; replay_session_id=$scope.ReplaySession; quality=$validation[$scope.Run]
        network_mode=$(if (!$hasLoads) {'SIMULATED'} elseif ($first.load_mode) {$first.load_mode} else {'UNATTRIBUTED'})
        policy=$first.policy; network_profile=$first.network_profile; payload_scale=$first.payload_scale
        p5_mode=$(if ($first.policy -eq 'P5' -and $p5ByRun[$scope.Run]) {
            @($p5ByRun[$scope.Run].mode | Sort-Object -Unique) -join '|'
        })
        foreground_loads=$(if ($hasLoads) { $loads.Count })
        full_hits=$(if ($classified) { @($loads | Where-Object hit_type -eq 'FULL_HIT').Count })
        late_hits=$(if ($classified) { @($loads | Where-Object hit_type -eq 'LATE_HIT').Count })
        misses=$(if ($classified) { @($loads | Where-Object hit_type -eq 'MISS').Count })
        hit_rate=$(if ($classified) { $hits / $classified })
        latency_samples=$latency.Count; mean_load_latency_ms=$latency.Mean
        p50_load_latency_ms=$latency.P50; p95_load_latency_ms=$latency.P95
        foreground_byte_samples=$foreground.Count; simulated_foreground_bytes=$foreground.Sum
        waste_samples=$wasted.Count; simulated_wasted_prefetch_bytes=$wasted.Sum
        cancelled_samples=$cancelled.Count; cancelled_prefetch=$cancelled.Sum
    }
}

$prefetchRows = @(foreach ($group in (With-Scope @($prefetch | Where-Object {
    $_.run_id -and $_.row_type -in @('FOREGROUND_LOAD','CACHE_WASTE')
}) | Group-Object Level,Run,Session,ReplaySession)) {
    $scope = $group.Group[0]
    $data = $group.Group.Data
    $waste = @($data | Where-Object row_type -eq 'CACHE_WASTE')
    $loadGroups = @($data | Where-Object row_type -eq 'FOREGROUND_LOAD' | Group-Object load_mode,policy,network_profile,payload_scale)
    foreach ($loads in $loadGroups) {
        Prefetch-Result $scope @($loads.Group) @()
    }
    if ($waste.Count) { Prefetch-Result $scope @() $waste }
})

function Performance-Result($scope, $first, $metric, $stats) {
    $source = if ($first.event_source) { $first.event_source } else { 'UNATTRIBUTED' }
    [pscustomobject]@{
        aggregation_level=$scope.Level; source=$source; run_id=$scope.Run; session_id=$scope.Session
        replay_session_id=$scope.ReplaySession; process_id=$first.process_id; quality=$(
            if ($scope.Run) { $validation[$scope.Run] })
        network_mode=$first.network_mode; event_type=$first.event_type; screen=$first.screen
        outcome=$first.outcome; metric=$metric; samples=$stats.Count; sum=$stats.Sum
        mean=$stats.Mean; p50=$stats.P50; p95=$stats.P95; max=$stats.Max
    }
}

$performanceRows = @(foreach ($group in (With-Scope $performance | Group-Object Level,Run,Session,ReplaySession,
        { $_.Data.process_id }, { $_.Data.event_source }, { $_.Data.network_mode },
        { $_.Data.event_type }, { $_.Data.screen }, { $_.Data.outcome })) {
    $scope = $group.Group[0]
    $first = $scope.Data
    $data = $group.Group.Data
    Performance-Result $scope $first 'event_count' @{ Count=$data.Count; Sum=$data.Count }
    $metrics = switch ($first.event_type) {
        'NETWORK_CALL' { 'call_duration_ms','dns_ms','connect_ms','tls_ms','request_body_ms','response_body_ms',
            'request_body_bytes','response_body_bytes' }
        'NAVIGATION_CONTENT_READY' { 'navigation_content_ready_ms' }
        'FRAME_SUMMARY' { 'frames_observed','frames_with_deadline','jank_frames',
            'mean_frame_duration_ms','max_frame_duration_ms','frame_reports_dropped' }
        'CHECKPOINT' { 'buffer_rows_dropped','write_rows_failed' }
    }
    foreach ($field in $metrics) {
        $stats = Get-Stats @(Numbers $data $field)
        if (!$stats) { continue }
        if ($first.event_type -eq 'CHECKPOINT') { $stats = @{ Count=$stats.Count; Max=$stats.Max } }
        if ($field -match '^(mean|max)_frame_duration_ms$') {
            $stats.Remove('Sum')
            Performance-Result $scope $first ("window_" + $field) $stats
        } else {
            Performance-Result $scope $first $field $stats
        }
    }
    if ($first.event_type -eq 'FRAME_SUMMARY') {
        $paired = @($data | Where-Object { $_.frames_with_deadline -and $_.jank_frames })
        $deadlines = Get-Stats @(Numbers $paired 'frames_with_deadline')
        $jank = Get-Stats @(Numbers $paired 'jank_frames')
        if ($deadlines.Sum -gt 0 -and $null -ne $jank) {
            Performance-Result $scope $first 'jank_rate' @{Count=$deadlines.Sum; Mean=$jank.Sum / $deadlines.Sum }
        }
    }
})

function Journey-Session($events) {
    $first = $events[0]
    $increments = New-Object 'System.Collections.Generic.List[object]'
    $segment = New-Object 'System.Collections.Generic.List[string]'
    $previous = $null
    $unambiguous = $events | Group-Object sequence | Where-Object Count -eq 1 | ForEach-Object { $_.Group[0] }
    foreach ($row in ($unambiguous | Sort-Object { [long] $_.sequence })) {
        $linked = $previous -and $row.from_screen -and
            ([long] $row.sequence -eq ([long] $previous.sequence + 1)) -and
            ([long] $row.timestamp_elapsed_ms -ge [long] $previous.timestamp_elapsed_ms) -and
            ($row.from_screen -ceq $previous.to_screen) -and ($row.from_screen -cne $row.to_screen)
        if ($linked) {
            $increments.Add([pscustomobject]@{ Kind='TRANSITION'; Key="$($row.from_screen) -> $($row.to_screen)" })
        } elseif ($segment.Count) {
            $increments.Add([pscustomobject]@{ Kind='SEQUENCE'; Key=($segment -join ' -> ') })
            $segment.Clear()
        }
        $increments.Add([pscustomobject]@{ Kind='SCREEN'; Key=$row.to_screen })
        $segment.Add($row.to_screen)
        $previous = $row
    }
    if ($segment.Count) { $increments.Add([pscustomobject]@{ Kind='SEQUENCE'; Key=($segment -join ' -> ') }) }
    foreach ($group in ($increments | Group-Object Kind,Key)) {
        [pscustomobject]@{
            aggregation_level='SESSION'; source=$first.source; run_id=$first.run_id
            session_id=$first.session_id; replay_session_id=$first.replay_session_id
            candidate_eligible=($first.source -eq 'REPLAY'); kind=$group.Group[0].Kind
            journey=$group.Group[0].Key; count=$group.Count
        }
    }
}

$journeySession = @(foreach ($session in ($navigation | Where-Object {
    $_.run_id -and $_.session_id -and $_.to_screen -cin $screens -and $_.sequence -match '^\d{1,18}$' -and
    $_.timestamp_elapsed_ms -match '^\d{1,18}$' -and
    ($_.source -ceq 'LIVE' -or ($_.source -ceq 'REPLAY' -and $validation[$_.run_id] -eq 'VALID'))
} | Group-Object source,run_id,session_id)) {
    Journey-Session @($session.Group)
})
$journeyRun = @(foreach ($group in ($journeySession | Group-Object source,run_id,kind,journey)) {
    $first = $group.Group[0]
    [pscustomobject]@{
        aggregation_level='RUN'; source=$first.source; run_id=$first.run_id
        candidate_eligible=$first.candidate_eligible; kind=$first.kind; journey=$first.journey
        count=($group.Group | Measure-Object count -Sum).Sum
    }
})

New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
Write-Table ($runRows + $prefetchRows) @(
    'row_type','aggregation_level','source','run_id','session_id','replay_session_id','quality','run_status',
    'confirmed_steps','expected_navigation_events','observed_navigation_events','network_mode','policy','network_profile',
    'payload_scale','p5_mode','foreground_loads','full_hits','late_hits','misses','hit_rate','latency_samples','mean_load_latency_ms',
    'p50_load_latency_ms','p95_load_latency_ms','foreground_byte_samples','simulated_foreground_bytes','waste_samples',
    'simulated_wasted_prefetch_bytes','cancelled_samples','cancelled_prefetch') (Join-Path $OutputDirectory 'prefetch_summary.csv')
Write-Table $performanceRows @(
    'aggregation_level','source','run_id','session_id','replay_session_id','process_id','quality','network_mode',
    'event_type','screen','outcome','metric','samples','sum','mean','p50','p95','max') (Join-Path $OutputDirectory 'performance_summary.csv')
Write-Table @(($journeyRun + $journeySession) | Sort-Object source,run_id,aggregation_level,session_id,kind,
    @{Expression='count';Descending=$true},journey) @(
    'aggregation_level','source','run_id','session_id','replay_session_id','candidate_eligible','kind','journey','count'
) (Join-Path $OutputDirectory 'journey_summary.csv')

if ($p5ByRun.Count) {
    $lambdaHistory = @(foreach ($event in ($p5 | Where-Object event_type -eq 'OBSERVATION')) {
        $event | Add-Member -NotePropertyName quality -NotePropertyValue $validation[$event.run_id] -PassThru
    })
    Write-Table @($lambdaHistory | Sort-Object run_id,{ [int] $_.observation_index }) @(
        'run_id','session_id','replay_session_id','mode','quality','observation_index','old_lambda','new_lambda',
        'waste_ratio','miss_rate','resolved_count','successful_load_count','completed_bytes','resolved_bytes',
        'wasted_bytes','update_count','reason') (Join-Path $OutputDirectory 'lambda_history.csv')
    $p5Summary = @(foreach ($run in ($p5ByRun.Keys | Sort-Object)) {
        $rows = $p5ByRun[$run]
        $endsP5 = @($rows | Where-Object event_type -eq 'RUN_SUMMARY')
        $end = if ($endsP5.Count -eq 1) { $endsP5[0] }
        [pscustomobject]@{
            run_id=$run; mode=(@($rows.mode | Sort-Object -Unique) -join '|'); quality=$validation[$run]
            p5_summary_present=($endsP5.Count -eq 1)
            candidates=@($rows | Where-Object event_type -eq 'DECISION').Count
            selected=@($rows | Where-Object { $_.event_type -eq 'DECISION' -and $_.selected -eq 'true' }).Count
            dispatched=@($rows | Where-Object event_type -eq 'DISPATCHED').Count
            completed=@($rows | Where-Object event_type -eq 'COMPLETED').Count
            used=@($rows | Where-Object event_type -eq 'USED').Count
            wasted=@($rows | Where-Object event_type -eq 'WASTED').Count
            cancelled=@($rows | Where-Object event_type -eq 'CANCELLED').Count
            failed=@($rows | Where-Object event_type -eq 'FAILED').Count
            lambda_initial=$end.old_lambda; lambda_final=$end.new_lambda; update_count=$end.update_count
            completed_bytes=$end.completed_bytes; resolved_bytes=$end.resolved_bytes; wasted_bytes=$end.wasted_bytes
            waste_ratio=$end.waste_ratio; miss_rate=$end.miss_rate; last_reason=$end.reason
        }
    })
    Write-Table $p5Summary @(
        'run_id','mode','quality','p5_summary_present','candidates','selected','dispatched','completed','used','wasted',
        'cancelled','failed','lambda_initial','lambda_final','update_count','completed_bytes','resolved_bytes',
        'wasted_bytes','waste_ratio','miss_rate','last_reason') (Join-Path $OutputDirectory 'p5_summary.csv')
}
Get-Item -LiteralPath $OutputDirectory | Select-Object -ExpandProperty FullName
