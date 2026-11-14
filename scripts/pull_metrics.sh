#!/bin/bash
OUTPUT_FOLDER="$(dirname "$0")/../experiment_results"
mkdir -p "$OUTPUT_FOLDER"

echo "=========================================================="
echo "   PULL EXPERIMENT METRICS CSV FROM ANDROID DEVICE"
echo "=========================================================="

REMOTE_DIR="/sdcard/Android/data/com.socialapp/files/metrics"
echo "[+] Pulling CSV files from $REMOTE_DIR to $OUTPUT_FOLDER..."
adb pull "$REMOTE_DIR/." "$OUTPUT_FOLDER"

if [ $? -eq 0 ]; then
    echo "[V] Successfully pulled metrics to $OUTPUT_FOLDER"
    ls -lh "$OUTPUT_FOLDER"/*.csv 2>/dev/null
else
    echo "[!] Fallback: pulling from internal app directory..."
    adb exec-out run-as com.socialapp cat files/metrics/metrics_default.csv > "$OUTPUT_FOLDER/metrics_default.csv"
fi

