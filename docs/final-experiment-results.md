# Kết quả thực nghiệm cuối kỳ

Ba loại bằng chứng được trình bày riêng trong báo cáo này. T4.B.2 đo đường tải dữ liệu với mạng mô phỏng trong ứng dụng Android. T6.A.1, T6.B và T6.C.2 lưu số đo Macrobenchmark trên emulator. Độ bao phủ hành vi của T6.C.2 và các trạng thái pipeline ở T6.D–T6.D.1 trả lời lần lượt câu hỏi về hành trình được chọn và khả năng vận hành vòng phản hồi.

## T4.B.2 — Đánh giá Adaptive Lambda

Bốn lượt overlay dùng cùng 21 session đánh giá độc lập, 109 bước xác nhận, 109 NavigationEvent, 88 transition, LTE mô phỏng, payload 1,0× và time scale 5,0×. Mỗi cấu hình có một run hợp lệ. Báo cáo lưu ghi nhận Pixel 9a API 37 theo định danh ADB lúc export và các run trong Logcat. Ghi chú người chạy ghi Pixel 10. Bảng giữ nguyên cohort đã đối soát và không dùng kết quả giữa các emulator để suy luận hiệu năng. [Báo cáo T4 đã lưu](../captures/t4b2-official/20261125_162353_627_98ea8b8c/official_four_runs/Bao-cao-T4.md) · [CSV so sánh chính thức](../captures/t4b2-official/20261125_162353_627_98ea8b8c/official_four_runs/analysis/evaluation_comparison.csv)

| Chỉ số | E0 P0 | E1 P5 Legacy | E2 P5 Fixed | E3 P5 Adaptive |
|---|---:|---:|---:|---:|
| Foreground load thành công | 84 | 84 | 84 | 84 |
| FULL HIT / LATE HIT / MISS | 0 / 0 / 84 | 65 / 0 / 19 | 63 / 0 / 21 | 58 / 0 / 26 |
| Hit rate | 0,00% | 77,38% | 75,00% | 69,05% |
| Mean load latency (ms) | 14,893 | 5,321 | 4,440 | 4,786 |
| P50 load latency (ms) | 15,00 | 1,00 | 1,00 | 0,00 |
| P95 load latency (ms) | 16,85 | 16,00 | 16,00 | 15,00 |
| Completed prefetch (B) | 0 | 16.640.000 | 15.872.000 | 13.967.360 |
| Used prefetch (B) | 0 | 8.355.840 | 8.130.560 | 7.587.840 |
| Wasted prefetch (B) | 0 | 8.284.160 | 7.741.440 | 6.379.520 |
| Waste ratio | N/A | 49,78% | 48,77% | 45,67% |
| Prefetch bị hủy | 0 | 0 | 0 | 0 |
| Foreground mô phỏng (B) | 11.192.320 | 2.283.520 | 2.508.800 | 3.051.520 |
| Tổng byte mô phỏng xác định được | 11.192.320 | 18.923.520 | 18.380.800 | 17.018.880 |
| λ đầu → cuối (ms/B) | N/A | 0,000005 → 0,000005 | 0,000075 → 0,000075 | 0,000075 → 0,0002239488 |
| Cập nhật λ | N/A | 0 | 0 | 6 |

E3 so với E2 giảm **1.361.920 B waste (17,59%)** và **1.361.920 B tổng byte mô phỏng (7,41%)**. Completed prefetch giảm 12,00%. Hit rate giảm 5,95 điểm phần trăm, mean load latency tăng khoảng 0,345 ms và P95 giảm 1 ms. E3 dùng ít byte nhất trong ba cấu hình P5. So với P0, E3 có mean load latency thấp hơn 67,87% và tổng byte mô phỏng cao hơn 52,06%. [Chênh lệch đã lưu](../captures/t4b2-official/20261125_162353_627_98ea8b8c/official_four_runs/analysis/evaluation_deltas.csv)

Sáu lần tăng λ đều có lý do `HIGH_WASTE` trong 10 khoảng quan sát. Đối soát theo `prefetch_id` xác nhận completed bytes bằng used cộng wasted. Tổng byte mô phỏng cộng completed prefetch với foreground bytes, tính mỗi byte đã phục vụ cache hit một lần. Load latency là thời gian elapsed của đường tải trong Android với cache và lịch thực thi của emulator. P50 bằng 0 ms phản ánh độ phân giải của phép đo. TTID, TTFD và frame timing thuộc phép đo khác. [Lịch sử λ](../captures/t4b2-official/20261125_162353_627_98ea8b8c/official_four_runs/analysis/lambda_history.csv) · [Đối soát từng run](../captures/t4b2-official/20261125_162353_627_98ea8b8c/official_four_runs/t4_verification.json)

T4.B.1 là lần hiệu chỉnh riêng với 10 session và 50 bước. Adaptive cập nhật λ 4 lần, giảm waste 12,50% và giảm tổng byte mô phỏng 5,31% so với Fixed. Hai lượt được khởi chạy lần lượt bằng instrumentation và overlay, nên cặp này dùng để xác minh controller và chốt cấu hình trước đánh giá. [Báo cáo hiệu chỉnh và đánh giá](../captures/t4b2-official/20261125_162353_627_98ea8b8c/official_four_runs/Bao-cao-T4.md)

## T6.A.1 — Baseline Profile và ART compilation

Smoke chạy trên Pixel 9a API 37, guest, fixture local và P0. B0 dùng `CompilationMode.None` và kiểm chứng compiler filter `verify` sau reset. B1 dùng `Partial(Require, 0)` và kiểm chứng `speed-profile`. Sáu bài có 3 iteration mỗi bài, đạt **18/18**. Lượt chạy chỉ suppress lỗi `EMULATOR` qua runner argument. [Số đo và trace](../build/t6a1/20261126_003611-pixel9a-smoke/benchmark) · [Bằng chứng compilation](../build/t6a1/20261126_003611-pixel9a-smoke/compilation-evidence.txt)

`StartupTimingMetric` đo TTID tới lần hiển thị đầu tiên và TTFD tới mốc fully drawn thông qua `reportFullyDrawn()`. `FrameTimingMetric` cung cấp thời gian xử lý frame trên CPU và mức vượt deadline của frame. Các bảng dùng millisecond và ghi rõ median hoặc percentile. [Bài test Macrobenchmark](../baselineprofile/src/main/java/com/socialapp/baselineprofile/BaselineProfileBenchmark.java)

| Hành trình và chỉ số | B0 (ms) | B1 (ms) | B1 so với B0 |
|---|---:|---:|---:|
| Cold startup, TTID median | 812,140 | 625,208 | −23,02% |
| Cold startup, TTFD median | 1.152,664 | 873,009 | −24,26% |
| Feed → Post Detail, frame CPU P95 | 85,060 | 82,065 | −3,52% |
| Feed → Post Detail, frame overrun P95 | 105,448 | 89,781 | −14,86% |
| Post Detail → Comments, frame CPU P95 | 60,611 | 51,348 | −15,28% |
| Post Detail → Comments, frame overrun P95 | 94,228 | 84,620 | −10,20% |

TTID và TTFD thấp hơn trong smoke. Frame timing có kết quả khác chiều ở một số percentile. Ví dụ Feed → Post Detail có frame overrun P99 tăng 15,16%. Ba iteration trên emulator giới hạn mức diễn giải ở các lượt đã đo. [Bảng đầy đủ](../build/t6a1/20261126_003611-pixel9a-smoke/comparison.json)

## T6.B — Startup Profile và DEX layout

S0 và S1 dùng cùng Baseline Profile nguồn và `Partial(Require, 0)`. Cả hai cài Baseline Profile tương ứng với DEX của mình và có `speed-profile`. D8 đặt HomeActivity và FeedFragment ở `classes2.dex` với S0 và `classes.dex` với S1. S0 có 2 DEX và APK 8.046.746 B. S1 có 3 DEX và APK 8.243.412 B. Đây là bằng chứng về thay đổi layout khi bật Startup Profile. [DEX layout S0](../build/t6b/20261126_004430-pixel9a-smoke/S0/dex-layout.csv) · [DEX layout S1](../build/t6b/20261126_004430-pixel9a-smoke/S1/dex-layout.csv)

| Cold startup | S0 median (3 mẫu) | S1 median (2 mẫu) | Chênh lệch mô tả |
|---|---:|---:|---:|
| TTID | 582,603 ms | 763,520 ms | +180,917 ms (+31,05%) |
| TTFD | 912,141 ms | 1.045,171 ms | +133,030 ms (+14,58%) |

S1 thiếu giá trị TTID và TTFD ở iteration thứ ba trong dữ liệu benchmark. Perfetto vẫn ghi cold launch của iteration đó. Chênh lệch trên chỉ sử dụng các mẫu framework xuất ra. Dữ liệu hiện có chưa cho thấy Startup Profile cải thiện startup. [So sánh đã lưu](../build/t6b/20261126_004430-pixel9a-smoke/comparison.json) · [Đối chiếu trace S1](../build/t6b/20261126_004430-pixel9a-smoke/S1/trace-startup-diagnosis.txt)

## T6.C.2 — Manual Selection và Telemetry-Driven Selection

Hai phương pháp có cùng startup, fixture guest, P0, ngân sách K=2 và 3 lượt thu trên mỗi hành trình. M0 chọn Feed → Post Detail và Feed → Search theo chức năng đọc và khám phá. M1 chọn Feed → Post Detail và Post Detail → Comments theo tần suất trên tập hiệu chỉnh. Tập 21 session đánh giá được giữ riêng để tính độ bao phủ. [Manifest M0](../baselineprofile/selection-m0.json) · [Manifest M1](../baselineprofile/selection-m1.json)

| Độ bao phủ hành vi | M0 | M1 |
|---|---:|---:|
| Transition thuộc catalog được chọn | 18/32 (56,25%) | 24/32 (75,00%) |
| Transition toàn workload được chọn | 18/88 (20,45%) | 24/88 (27,27%) |
| Quy tắc Baseline Profile | 7.285 | 6.717 |

Catalog mô tả 32/88 transition trong run độc lập. M1 chọn nhiều occurrence hơn trong workload đó. So sánh rule có 6.614 descriptor chung, 671 riêng M0 và 103 riêng M1. Độ bao phủ theo hành trình và số quy tắc đo hai khía cạnh khác nhau. [Độ bao phủ](../build/t6c2/20261126_010754-pixel9a/coverage.json) · [So sánh profile](../build/t6c2/20261126_010754-pixel9a/profile-comparison.json)

| Smoke với Baseline Profile | M0 (ms) | M1 (ms) | M1 so với M0 |
|---|---:|---:|---:|
| Startup, TTID median | 766,782 | 634,131 | −17,30% |
| Startup, TTFD median | 1.028,493 | 885,329 | −13,92% |
| Feed → Post Detail, frame overrun P95 | 261,940 | 106,716 | −59,26% |
| Post Detail → Comments, frame overrun P95 | 82,601 | 87,029 | +5,36% |
| Feed → Search, frame overrun P95 | 23,326 | 29,462 | +26,31% |
| Feed → Fact-Check, frame overrun P95 | 36,372 | 27,533 | −24,30% |

Hai APK dùng ART `speed-profile`, cùng bộ bài đo và có 3 iteration hợp lệ cho mỗi bài. Số đo theo hành trình thay đổi theo cả hai chiều. Bằng chứng trực tiếp cho lựa chọn theo telemetry là độ bao phủ của M1 cao hơn trong tập độc lập. Smoke chỉ mô tả hiệu năng trong điều kiện emulator đã lưu. [Kết quả Macrobenchmark đầy đủ](../build/t6c2/20261126_010754-pixel9a/benchmark-comparison.json)

## T6.D và T6.D.1 — Kiểm chứng Continuous Profile Pipeline

| Lượt | Session | NavigationEvent | Transition | Kết quả kỹ thuật |
|---|---:|---:|---:|---|
| T6.D Cycle 1 | 10 | 50 | 40 | `generation-003`, REUSED artifact M1 đã xác minh |
| T6.D Cycle 2 tích lũy | 18 | 90 | 72 | `generation-005`, REUSED với cùng fingerprint |
| T6.D.1 tích lũy | 53 | 275 | 222 | `generation-011`, GENERATED sau `FORCED_TECHNICAL_CHECK`. Top-K giữ nguyên |
| T6.D.1 feedback độc lập | 1 | 5 | 4 | Replay hợp lệ trên APK `generation-011`, pipeline nhận CSV và quyết định REUSE |

APK `generation-011` áp dụng Baseline Profile và đạt ART `speed-profile`. Build feedback tắt Startup Profile trong DEX layout. Replay xác nhận 5/5 bước và LIVE profile khớp từng byte trước và sau. Feedback session 12 được xử lý riêng vì session này đã nằm trong tập tích lũy. Các kết quả này xác minh việc tái sử dụng artifact, sinh mới có chủ đích và đưa telemetry trở lại pipeline. [Cycle 1](../build/continuous-profile/generation-003/generation-report.json) · [Cycle 2](../build/continuous-profile/generation-005/generation-report.json) · [Generation 011](../build/t6d1/20261126_095722-feedback/generation-011/generation-report.json) · [Replay và quyết định feedback](../build/t6d1/20261126_095722-feedback/generated-feedback/feedback-provenance.json)

Xem [kiến trúc và giới hạn](final-system-overview.md) và [kịch bản demo](demo-guide.md).
