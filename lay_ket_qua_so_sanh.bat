@echo off
chcp 65001 > nul
setlocal

set "ADB_BIN=adb"
where adb >nul 2>nul
if errorlevel 1 (
    if exist "%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" (
        set "ADB_BIN=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
    ) else (
        echo [!] Khong tim thay adb.exe. Hay bat may ao trong Android Studio truoc.
        pause
        exit /b 1
    )
)

set "RAW_FILE=%TEMP%\prefetch_comparison_raw.csv"
set "OUT_CSV=%~dp0ket_qua_so_sanh.csv"
set "VENV_PY=d:\AI Project\markov_project\.venv\Scripts\python.exe"

echo Dang lay du lieu tu may ao Android...
del /q "%RAW_FILE%" >nul 2>nul
"%ADB_BIN%" pull /sdcard/Download/prefetch_comparison.csv "%RAW_FILE%" >nul 2>nul
if errorlevel 1 (
    "%ADB_BIN%" pull /sdcard/Android/data/com.socialapp/files/metrics/prefetch_comparison.csv "%RAW_FILE%" >nul 2>nul
)

if not exist "%RAW_FILE%" (
    echo [!] Chua tim thay file tren may ao. Hay bam nut "Xuat CSV" tren thanh Demo trong App truoc!
    pause
    exit /b 1
)

if exist "%VENV_PY%" (
    "%VENV_PY%" "%~dp0markov-lab\sim\format_comparison.py" "%RAW_FILE%" "%OUT_CSV%"
) else (
    copy /y "%RAW_FILE%" "%OUT_CSV%" >nul
)

start "" "%OUT_CSV%"
