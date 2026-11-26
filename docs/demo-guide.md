# Kịch bản demo cuối kỳ

## T7 — Demo ba cấu hình bằng một APK

Gửi [SocialApp-T7-demo.apk](../build/t7-demo/SocialApp-T7-demo.apk) cho thành viên trình bày. Trên thiết bị Android, mở APK bằng trình quản lý file và cho phép cài từ nguồn đó nếu hệ thống yêu cầu. Có thể kéo thả APK vào cửa sổ emulator đang mở. Sau khi cài, dùng **SocialApp → PREFETCH DEMO**. PowerShell, ADB và các APK profile ở phần T6.E bên dưới chỉ phục vụ kiểm chứng kỹ thuật.

1. Trong **Cấu hình Thử nghiệm**, bật mô phỏng mạng, chọn **LTE**, đặt **Payload mô phỏng 1.0x** và **Tốc độ Tua nhanh 5.0x**.
2. Chọn **P0**, rồi **Replay → T4 — Calibration (10 sessions)**. Đợi hộp thoại báo **Hoàn thành** và **CSV v2 đầy đủ**.
3. Chọn **P5 → Fixed Lambda** và chạy lại đúng preset. Sau đó lặp lại với **P5 → Adaptive Lambda**. Ba lượt dùng cùng workload guest và thiết lập trên.
4. Khi Adaptive đang chạy, xem màn hình, dự đoán Markov, FULL HIT/LATE HIT/MISS, thời gian tải trung bình, waste, λ, số lần cập nhật và lý do. Các con số phụ thuộc lượt chạy thực tế.
5. Mở **Replay → So sánh và xuất CSV demo T4**. Khi đủ **3/3**, chọn **Xuất CSV** rồi lưu hoặc chia sẻ bằng một ứng dụng hỗ trợ nhiều file. Có bốn file gồm một CSV tổng hợp và ba CSV v2 gốc của các run đã chốt.

Mỗi cấu hình giữ lượt hợp lệ gần nhất trong dữ liệu ứng dụng. Đổi policy, chuyển màn hình hoặc mở lại app vẫn thấy các lượt đã chốt. Khi menu hiện dưới 3/3, kiểm tra cấu hình, trạng thái và số bước trong **Kết quả Replay gần nhất**, rồi chạy lại cấu hình còn thiếu. Khi chuẩn bị một bộ kết quả mới cho buổi trình bày, chạy đủ cả ba cấu hình để thay thế các lượt diễn tập. Các file navigation, performance và P5 ledger vẫn phục vụ quy trình export kỹ thuật của T6.E.

Nên chạy thử preset trên thiết bị sẽ dùng để trình bày. AVD kiểm thử T7 từng gián đoạn ở 5× do lỗi lifecycle Android. Khi một lượt bị gián đoạn, các lượt đã chốt vẫn nằm trong bảng và lượt bị gián đoạn cần chạy lại ở cùng cấu hình.

## T6.E — Kịch bản kỹ thuật có artifact và feedback

Các SHA-256 và fingerprint trong phần này là mốc đã lưu tại T6.E. Nếu dùng source hoặc APK debug của T7 để đối chiếu, build mới có SHA-256 mới và pipeline có thể báo `PROFILE_INPUTS_CHANGED`.

**Thời lượng dự kiến khoảng 8–10 phút.** Hai phần chạy trên Android gồm P5 Adaptive từ overlay và một Replay feedback ngắn trên APK đã áp dụng Baseline Profile. Phần Macrobenchmark sử dụng artifact đã lưu. Các lệnh dưới đây dành cho buổi demo. T6.E chỉ chuẩn bị tài liệu và đối chiếu artifact.

## Chuẩn bị

- Mở Windows PowerShell tại thư mục gốc `D:\Tai_lieu_hoc\Truong\Toi_uu\Project\appAndroidDetectOoc`. Cần ADB trong PATH, JDK 17, quyền cài APK và các artifact trong `app/build`, `build` và `captures`.
- Dùng Pixel 9a AVD API 37 đã kiểm chứng cho feedback. Đọc serial từ `adb devices -l`. Giữ dữ liệu ứng dụng khi cài lại. `com.socialapp` và `com.socialapp.profile` có vùng lưu riêng.
- Dùng JDK và fingerprint thiết bị của `generation-011` để tái hiện quyết định `REUSE`. [JDK và fingerprint đã lưu](../build/t6d1/20261126_095722-feedback/generation-011/profile-inputs.json)

```powershell
$serial = 'emulator-5554'
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
$archive = 'build/t6d1/20261126_095722-feedback'
$generation = Join-Path $archive 'generation-011'
$official = 'captures/t4b2-official/20261125_162353_627_98ea8b8c/official_four_runs'
$demo = Join-Path 'build/demo-final' (Get-Date -Format 'yyyyMMdd_HHmmss_fff')
New-Item -ItemType Directory -Path $demo | Out-Null
$debugApk = 'app/build/outputs/apk/debug/app-debug.apk'
$profileApk = Join-Path $generation 'benchmark.apk'
$runnerApk = Join-Path $archive 'feedback-runner.apk'
adb devices -l
& "$env:JAVA_HOME/bin/java.exe" -version
Get-FileHash $debugApk,$profileApk,$runnerApk -Algorithm SHA256
```

| APK đã có | Package | SHA-256 |
|---|---|---|
| `app-debug.apk` | `com.socialapp` | `A222C1029272D9D319AF11579A155C4AF88A6C2491E034ADF7EBF3E0C56E3EE0` |
| `generation-011/benchmark.apk` | `com.socialapp.profile` | `4007497BA69FC3FB8BB9046C1DA1B519D26E6E6573FCF6E2F4B0B51758E02A6A` |
| `feedback-runner.apk` | `com.socialapp.profile.test` | `B5A1ED1BE185A3ACB33D06E38D9707052FC1A87533B2F9760734ECBF3A4ED166` |

```powershell
adb -s $serial install -r $debugApk
adb -s $serial install -r $profileApk
adb -s $serial install -r $runnerApk
$deviceApk = (adb -s $serial shell pm path com.socialapp.profile |
    Select-Object -First 1) -replace '^package:'
adb -s $serial shell sha256sum $deviceApk
adb -s $serial shell am broadcast -f 0x20 `
    -a androidx.profileinstaller.action.INSTALL_PROFILE `
    -n com.socialapp.profile/androidx.profileinstaller.ProfileInstallReceiver
adb -s $serial shell cmd package compile -m speed-profile -f com.socialapp.profile
adb -s $serial shell dumpsys package dexopt |
    Select-String -SimpleMatch '[com.socialapp.profile]' -Context 0,4
```

Kiểm tra SHA trên thiết bị, `result=1` từ ProfileInstaller và `speed-profile` từ ART. Trước buổi demo, mở app profile ở trạng thái guest, mở bài fixture từ Feed Newest rồi bấm Home để lưu checkpoint LIVE. Nếu cần gọi đúng app profile từ ADB, dùng `adb -s $serial shell am start -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n com.socialapp.profile/com.socialapp.feature.home.HomeActivity`. Bản profile có fixture local và P0 mặc định. Overlay nằm trong app debug. [Bằng chứng cài profile](../build/t6d1/20261126_095722-feedback/generation-011/compilation.log) · [Smoke UI](../build/t6d1/20261126_095722-feedback/generated-feedback/ui-smoke-built-apk.log)

## 1. Replay P5 Adaptive trên overlay

1. Mở **SocialApp** bản debug. Chạm header **PREFETCH DEMO** nếu panel đang thu gọn.
2. Mở **Cấu hình Thử nghiệm → Chính sách Prefetch**, chọn **P5**, rồi chọn **Adaptive Lambda** trong **P5 cho Replay**.
3. Chọn hồ sơ **LTE**, bật **Chế độ Mô phỏng mạng**, đặt **Payload mô phỏng 1.0x** và **Tốc độ Tua nhanh 5.0x**.
4. Chọn **Replay → T4 — Calibration (10 sessions)**. Chỉ vào màn hình hiện tại, xác suất Markov, trạng thái FULL HIT/LATE HIT/MISS, hit rate và waste đã chốt. Dòng **λ / Waste / MISS / Cập nhật** thay đổi theo các cửa sổ đủ mẫu.
5. Mở **Replay → Kết quả Replay gần nhất**. Chép run ID mới. Mong đợi **Hoàn thành**, **CSV v2 đầy đủ**, **50 bước xác nhận** và **40 transition**. Dùng chính hộp thoại và CSV của lượt này khi đọc các giá trị λ, hit rate hoặc load latency.

Mốc đã lưu của preset là run `e28c0f10-b3e4-452e-a586-a77c8a0c6742`, với λ từ `0.000075` tới `0.00015552` và 4 cập nhật. Nếu emulator gián đoạn, chuyển sang dữ liệu đã lưu và giữ trạng thái của lượt demo theo terminal thực tế. [CSV hiệu chỉnh](../build/step-t4b1-recovery-export/20261125_151810_827_13680a30) · [Source overlay](../app/src/main/java/com/socialapp/core/prefetch/ui/PrefetchDemoOverlayView.java)

## 2. Export và phân tích telemetry

```powershell
$adaptiveId = [guid](Read-Host 'Run ID trong hộp thoại Replay')
$export = .\scripts\pull_metrics.ps1 -DeviceSerial $serial `
    -ApplicationId com.socialapp -OutputRoot (Join-Path $demo 'adaptive-export') |
    Select-Object -Last 1
$analysis = Join-Path $demo 'adaptive-analysis'
.\scripts\analyze_metrics.ps1 -InputDirectory $export -OutputDirectory $analysis
Import-Csv (Join-Path $analysis 'prefetch_summary.csv') |
    Where-Object { $_.run_id -eq $adaptiveId -and $_.aggregation_level -eq 'RUN' }
Import-Csv (Join-Path $analysis 'lambda_history.csv') |
    Where-Object run_id -eq $adaptiveId
```

Mở `prefetch_summary.csv` để xem hit, load latency và byte mô phỏng. Mở `lambda_history.csv` để xem waste, miss, λ cũ/mới và lý do. `journey_summary.csv` tóm tắt chuỗi trong session và `performance_summary.csv` dùng các trường correlation có sẵn. Đối chiếu `RUN_END`, `run_valid=true` và số NavigationEvent trước khi dùng số đo.

Để trình bày T4.B.2, mở bảng bốn run đã lưu hoặc tổng hợp lại đúng cohort trên máy phát triển.

```powershell
.\scripts\compare_t4_evaluation.ps1 -InputDirectory $official `
    -OutputDirectory (Join-Path $demo 't4-comparison') `
    -E0 d08ab9b6-67a0-4d81-abb1-fac0e8b90a1a `
    -E1 932a0129-37c0-4ade-91e2-48e972b11e23 `
    -E2 fd68aa7a-8c49-41fe-a088-c25da3c6bc21 `
    -E3 fda03ebe-4051-4547-b336-f137954df48f
```

## 3. Journey Ranking, Selection Manifest và Android Profile

```powershell
Get-Content (Join-Path $generation 'ranking.json')
Get-Content (Join-Path $generation 'selection.json')
Get-Content (Join-Path $generation 'profile-counts.json')
Get-Content (Join-Path $generation 'binary-verification.log')
```

Chỉ vào 34 lượt **Post Detail → Comments**, 26 lượt **Feed Newest → Post Detail** và startup bắt buộc. Mở `baseline-prof.txt`, `startup-prof.txt` và `collection-provenance.json`. Dòng `Landroidx/activity/ActivityFlags;` là class rule. Dòng `SPLandroidx/activity/ActivityFlags;-><clinit>()V` là method rule có flags. Android Profile đi theo build APK. Navigation Profile JSON phục vụ Markov trong runtime. [Artifact generation](../build/t6d1/20261126_095722-feedback/generation-011)

## 4. Chiếu Macrobenchmark đã lưu

| Artifact | Điểm trình bày |
|---|---|
| [T6.A.1](../build/t6a1/20261126_003611-pixel9a-smoke/comparison.json) | B0 ở `verify`, B1 ở `speed-profile`. TTID median giảm 23,02% và TTFD median giảm 24,26% trong smoke. |
| [T6.B](../build/t6b/20261126_004430-pixel9a-smoke/comparison.json) | S0/S1 có DEX layout khác nhau. S1 có hai mẫu TTID/TTFD. Dữ liệu chưa cho thấy cải thiện startup. |
| [T6.C.2](../build/t6c2/20261126_010754-pixel9a/benchmark-comparison.json) | M1 bao phủ 24/88 transition, M0 bao phủ 18/88. Frame timing có thay đổi theo cả hai chiều. |

Dùng [bảng tổng hợp](final-experiment-results.md) và JSON hoặc Perfetto trace đã lưu. Kịch bản không cần thu lại profile hay chạy Macrobenchmark.

## 5. Replay feedback trên APK generation-011

Lệnh chạy session 12 trên APK đã cài, với checkpoint LIVE chuẩn bị sẵn. Harness dùng cùng cấu hình đã kiểm chứng gồm P5 Fixed Lambda, LTE mô phỏng, payload 1,0× và tốc độ 5×. [Runner source](../app/src/androidTest/java/com/socialapp/core/prefetch/P5CalibrationTest.java)

```powershell
adb -s $serial shell am instrument -w -r `
    -e class com.socialapp.core.prefetch.P5CalibrationTest#runSelectedSessions `
    -e p5Mode FIXED_LAMBDA -e sessionIds 12 -e verifyLivePersistence true `
    com.socialapp.profile.test/androidx.test.runner.AndroidJUnitRunner
adb -s $serial logcat -d -s P5Calibration:I ReplayConfirmation:I '*:S' |
    Select-String 'LIVE_PERSISTENCE_UNCHANGED|RUN_FINISHED|csvComplete' |
    Select-Object -Last 8
```

Mong đợi `OK (1 test)`, `confirmedSteps=5`, `navigationEvents=5`, `csvComplete=true` và `LIVE_PERSISTENCE_UNCHANGED`. Chép UUID của lượt vừa chạy. UUID `20ad0666-5705-4ab4-b8e7-ac36161fa5e5` dành cho phương án trình bày artifact đã lưu. [Feedback provenance](../build/t6d1/20261126_095722-feedback/generated-feedback/feedback-provenance.json)

## 6. Đưa CSV feedback trở lại pipeline

```powershell
$runId = [guid](Read-Host 'UUID của lượt feedback vừa chạy')
$feedback = .\scripts\pull_metrics.ps1 -DeviceSerial $serial `
    -ApplicationId com.socialapp.profile -OutputRoot (Join-Path $demo 'feedback-export') |
    Select-Object -Last 1
.\scripts\analyze_metrics.ps1 -InputDirectory $feedback `
    -OutputDirectory (Join-Path $demo 'feedback-analysis')
$decision = Join-Path $demo 'feedback-decision'
.\scripts\run_continuous_profile_pipeline.ps1 -TelemetryDirectory $feedback `
    -RunId $runId -DeviceSerial $serial -PreviousGeneration $generation `
    -OutputDirectory $decision -AnalyzeOnly
Get-Content (Join-Path $decision 'ranking.json')
Get-Content (Join-Path $decision 'generation-report.json')
```

Mong đợi `ANALYZED`, 5 NavigationEvent và 4 transition cùng `decision=REUSE` và `decisionReason=VERIFIED_COMPATIBLE_ARTIFACT` khi fingerprint khớp. Nếu fingerprint khác, đối chiếu `decisionReason`, JDK, thiết bị và source theo report. `-AnalyzeOnly` dừng ở quyết định. Session 12 được phân tích như cửa sổ riêng. Chốt demo bằng chuỗi bằng chứng từ run ID tới APK SHA-256, generation và quyết định. [Quyết định REUSE đã lưu](../build/t6d1/20261126_095722-feedback/generated-feedback/feedback-decision/generation-report.json)
