[CmdletBinding()]
param([string] $FixtureRoot)
$ErrorActionPreference = 'Stop'
if (!$FixtureRoot) { $FixtureRoot = Join-Path $PSScriptRoot '..\..\build\step-t3-fixture' }
$work = Join-Path $FixtureRoot ([Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $work -Force | Out-Null

@'
schema_version,row_type,run_id,session_id,replay_session_id,load_mode,hit_type,load_latency_ms,simulated_foreground_bytes,simulated_wasted_prefetch_bytes,cancelled_prefetch_count,policy,network_profile,payload_scale,run_valid,run_status,confirmed_steps,navigation_events
2,FOREGROUND_LOAD,R1,S1,4,SIMULATED,FULL_HIT,10,0,,,P5,WIFI,1,,,,
2,FOREGROUND_LOAD,R1,S1,4,SIMULATED,LATE_HIT,30,,,,P5,WIFI,1,,,,
2,FOREGROUND_LOAD,R1,S2,5,SIMULATED,MISS,50,300,,,P5,WIFI,1,,,,
2,FOREGROUND_LOAD,R1,S2,5,REAL,MISS,20,,,,P5,WIFI,1,,,,
2,CACHE_WASTE,R1,S1,4,,,,,20,1,,,,,,,
2,RUN_END,R1,,,,,,,,,,, ,true,RUN_FINISHED,4,4
2,RUN_END,R2,,,,,,,,,,, ,false,STOPPED,1,1
2,RUN_END,R3,,,,,,,,,,, ,true,RUN_FINISHED,2,2
2,RUN_END,R4,,,,,,,,,,, ,true,RUN_FINISHED,2,2
2,RUN_END,R5,,,,,,,,,,, ,true,RUN_FINISHED,3,3
'@ | Set-Content -LiteralPath (Join-Path $work 'metrics_v2_fixture.csv') -Encoding ASCII

@'
schema_version,navigation_id,source,run_id,session_id,replay_session_id,sequence,from_screen,to_screen,timestamp_elapsed_ms
1,N2,REPLAY,R1,S1,4,2,FEED_NEWEST,POST_DETAIL,20
1,N1,REPLAY,R1,S1,4,1,,FEED_NEWEST,10
1,N3,REPLAY,R1,S2,5,1,,COMMENTS,30
1,N4,REPLAY,R1,S2,5,2,COMMENTS,FEED_HOT,40
1,N5,REPLAY,R2,S3,6,1,,SEARCH,10
1,N6,REPLAY,R3,S4,7,1,,PROFILE,10
1,N7,REPLAY,R4,S5,8,1,,FEED_NEWEST,10
1,N8,REPLAY,R4,S5,8,3,FEED_NEWEST,POST_DETAIL,20
1,N9,REPLAY,R5,S6,9,1,,FEED_NEWEST,10
1,N10,REPLAY,R5,S6,9,2,,SEARCH,20
1,N11,REPLAY,R5,S6,9,3,SEARCH,PROFILE,30
1,N12,REPLAY,R6,S7,10,1,,PROFILE,10
1,L1,LIVE,LIVE1,LS1,,1,,FEED_NEWEST,10
1,L2,LIVE,LIVE1,LS1,,3,FEED_NEWEST,POST_DETAIL,30
1,L3,LIVE,LIVE1,LS1,,4,POST_DETAIL,COMMENTS,31
'@ | Set-Content -LiteralPath (Join-Path $work 'navigation_v1.csv') -Encoding ASCII

@'
schema_version,process_id,event_type,run_id,session_id,replay_session_id,event_source,network_mode,screen,outcome,call_duration_ms,response_body_bytes,frames_observed,frames_with_deadline,jank_frames,mean_frame_duration_ms
1,P1,NETWORK_CALL,R1,S1,4,REPLAY,REAL,FEED_NEWEST,SUCCESS,8,500,,,,
1,P1,NETWORK_CALL,R1,S1,4,REPLAY,SIMULATED,FEED_NEWEST,SUCCESS,28,400,,,,
1,P1,FRAME_SUMMARY,,,,,,HOME,,,,2,2,1,12
1,P1,FRAME_SUMMARY,,,,,,HOME,,,,6,6,2,20
1,P1,FRAME_SUMMARY,,,,,,HOME,,,,9,9,,22
1,P1,HOME_FULLY_DRAWN,,,,,,FEED_NEWEST,,,,,,,
'@ | Set-Content -LiteralPath (Join-Path $work 'performance_v1.csv') -Encoding ASCII

function Assert-Equal($actual, $expected, $message) {
    if ($actual -cne $expected) { throw "$message (expected '$expected', got '$actual')" }
}

$analysis = & (Join-Path $PSScriptRoot '..\analyze_metrics.ps1') -InputDirectory $work
$prefetch = Import-Csv -LiteralPath (Join-Path $analysis 'prefetch_summary.csv')
$performance = Import-Csv -LiteralPath (Join-Path $analysis 'performance_summary.csv')
$journey = Import-Csv -LiteralPath (Join-Path $analysis 'journey_summary.csv')
$validation = @{}
$prefetch | Where-Object row_type -eq 'RUN_VALIDATION' | ForEach-Object { $validation[$_.run_id] = $_.quality }
Assert-Equal $validation.R1 'VALID' 'Valid Replay'
Assert-Equal $validation.R2 'INVALID_RUN_END' 'Stopped Replay'
Assert-Equal $validation.R3 'NAVIGATION_COUNT_MISMATCH' 'Missing navigation event'
Assert-Equal $validation.R4 'INVALID_NAVIGATION_SEQUENCE' 'Missing sequence despite matching event count'
Assert-Equal $validation.R5 'VALID' 'Explicit chain break within session is valid'
Assert-Equal $validation.R6 'MISSING_RUN_END' 'Missing terminal'

$load = $prefetch | Where-Object { $_.run_id -eq 'R1' -and $_.row_type -eq 'PREFETCH' -and
    $_.aggregation_level -eq 'RUN' -and $_.network_mode -eq 'SIMULATED' }
Assert-Equal $load.foreground_loads '3' 'Load count'
Assert-Equal ([Math]::Round([double] $load.hit_rate, 6)) 0.666667 'Hit rate'
Assert-Equal $load.mean_load_latency_ms '30' 'Mean latency'
Assert-Equal $load.p50_load_latency_ms '30' 'P50'
Assert-Equal $load.p95_load_latency_ms '48' 'P95'
Assert-Equal $load.simulated_foreground_bytes '300' 'Foreground bytes'
Assert-Equal $load.foreground_byte_samples '2' 'Explicit zero is known, blank is missing'
$real = $prefetch | Where-Object { $_.aggregation_level -eq 'RUN' -and $_.network_mode -eq 'REAL' }
Assert-Equal $real.foreground_loads '1' 'Separate REAL load'
Assert-Equal $real.simulated_foreground_bytes '' 'REAL bytes remain unmeasured'
$waste = $prefetch | Where-Object { $_.row_type -eq 'CACHE_WASTE' -and $_.aggregation_level -eq 'SESSION' }
Assert-Equal $waste.simulated_wasted_prefetch_bytes '20' 'Waste'
Assert-Equal $waste.cancelled_prefetch '1' 'Cancelled prefetch'

$frame = $performance | Where-Object metric -eq 'jank_rate'
Assert-Equal $frame.mean '0.375' 'Weighted jank rate and blank jank excluded'
Assert-Equal $frame.source 'UNATTRIBUTED' 'Do not infer source for window-only data'
Assert-Equal @($performance | Where-Object { $_.metric -eq 'call_duration_ms' -and $_.aggregation_level -eq 'RUN' }).Count 2 'Separate network modes'
Assert-Equal @($performance | Where-Object metric -match 'ttid|ttfd').Count 0 'No inferred startup latency'

$transitions = @($journey | Where-Object { $_.aggregation_level -eq 'RUN' -and $_.kind -eq 'TRANSITION' })
Assert-Equal (($transitions | Where-Object run_id -eq 'R1').journey -join '|') 'COMMENTS -> FEED_HOT|FEED_NEWEST -> POST_DETAIL' 'No transition across sessions'
Assert-Equal ($transitions | Where-Object run_id -eq 'LIVE1').journey 'POST_DETAIL -> COMMENTS' 'Do not bridge LIVE gap'
Assert-Equal ($transitions | Where-Object run_id -eq 'R5').journey 'SEARCH -> PROFILE' 'Do not join explicit chain break'
Assert-Equal @($journey | Where-Object run_id -in @('R2','R3','R4','R6')).Count 0 'Exclude invalid Replay journeys'
Assert-Equal @($journey | Where-Object { $_.source -eq 'LIVE' -and $_.candidate_eligible -ne 'False' }).Count 0 'LIVE outside primary ranking'

@'
schema_version,run_id,session_id,replay_session_id,mode,event_type,prefetch_id,selected,outcome_bytes,observation_index,old_lambda,new_lambda,waste_ratio,miss_rate,resolved_count,successful_load_count,resolved_bytes,wasted_bytes,completed_bytes,update_count,reason
1,R1,S1,4,ADAPTIVE_LAMBDA,DECISION,1,true,,,,,,,,,,,,,
1,R1,S1,4,ADAPTIVE_LAMBDA,DISPATCHED,1,,,,,,,,,,,,,,
1,R1,S1,4,ADAPTIVE_LAMBDA,COMPLETED,1,,100,,,,,,,,,,,,
1,R1,S1,4,ADAPTIVE_LAMBDA,WASTED,1,,100,,,,,,,,,,,,
1,R1,S1,4,ADAPTIVE_LAMBDA,OBSERVATION,,,,1,0.000075,0.00009,1,0.75,1,4,100,100,100,1,HIGH_WASTE
1,R1,,,ADAPTIVE_LAMBDA,RUN_SUMMARY,,,,,0.000075,0.00009,1,0.75,,4,100,100,100,1,HIGH_WASTE
'@ | Set-Content -LiteralPath (Join-Path $work 'p5_v1.csv') -Encoding ASCII

$analysis = & (Join-Path $PSScriptRoot '..\analyze_metrics.ps1') -InputDirectory $work
$p5 = Import-Csv -LiteralPath (Join-Path $analysis 'p5_summary.csv')
$history = Import-Csv -LiteralPath (Join-Path $analysis 'lambda_history.csv')
Assert-Equal $p5.quality 'VALID' 'P5 joined to existing run validity'
Assert-Equal $p5.mode 'ADAPTIVE_LAMBDA' 'P5 mode'
Assert-Equal $p5.candidates '1' 'Candidate count'
Assert-Equal $p5.used '0' 'Unused completion'
Assert-Equal $p5.completed_bytes '100' 'Completed bytes'
Assert-Equal $p5.wasted_bytes '100' 'Wasted bytes'
Assert-Equal $p5.lambda_final '0.00009' 'Final lambda'
Assert-Equal $history.observation_index '1' 'Observation index'
Assert-Equal $history.reason 'HIGH_WASTE' 'Observation reason'
"Fixture passed. Results: $analysis"
