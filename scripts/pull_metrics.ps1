# Script tự động kéo file CSV kết quả thực nghiệm từ máy ảo Android về máy tính
$OutputFolder = Join-Path -Path $PSScriptRoot -ChildPath "..\experiment_results"
if (!(Test-Path -Path $OutputFolder)) {
    New-Item -ItemType Directory -Path $OutputFolder -Force | Out-Null
}

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "   PULL EXPERIMENT METRICS CSV FROM ANDROID EMULATOR/DEVICE" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

# Kiểm tra adb
$device = adb devices | Select-String "device$"
if (!$device) {
    Write-Host "[!] Không tìm thấy thiết bị hoặc máy ảo Android nào đang kết nối qua ADB." -ForegroundColor Red
    Write-Host "    Vui lòng bật máy ảo hoặc cắm điện thoại và bật USB Debugging." -ForegroundColor Yellow
    exit 1
}

Write-Host "[+] Thiết bị kết nối: $device" -ForegroundColor Green

# Kéo file từ External Storage của app (thư mục tiêu chuẩn không cần root)
$RemoteDir = "/sdcard/Android/data/com.socialapp/files/metrics"
Write-Host "[+] Đang kéo các file CSV từ $RemoteDir về $OutputFolder..." -ForegroundColor Yellow

adb pull "$RemoteDir/." "$OutputFolder"

if ($LASTEXITCODE -eq 0) {
    Write-Host "[V] Kéo file thành công! Danh sách file CSV:" -ForegroundColor Green
    Get-ChildItem -Path $OutputFolder -Filter "*.csv" | Select-Object Name, Length, LastWriteTime | Format-Table -AutoSize
} else {
    Write-Host "[!] Thử kéo fallback từ Internal Storage..." -ForegroundColor Yellow
    adb exec-out run-as com.socialapp cat files/metrics/metrics_default.csv > (Join-Path $OutputFolder "metrics_default.csv")
}

