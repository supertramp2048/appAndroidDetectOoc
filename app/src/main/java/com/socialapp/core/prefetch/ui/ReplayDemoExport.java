package com.socialapp.core.prefetch.ui;

import android.app.Activity;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.socialapp.core.prefetch.PrefetchEngine;
import com.socialapp.core.prefetch.ReplayResult;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

final class ReplayDemoExport {
    private ReplayDemoExport() {}

    static void share(Activity activity, List<ReplayResult> results, Function<UUID, File> rawCsv) {
        Context context = activity.getApplicationContext();
        new Thread(() -> {
            try {
                ArrayList<Uri> files = prepare(context, results, rawCsv);
                activity.runOnUiThread(() -> {
                    if (activity.isFinishing() || activity.isDestroyed()) return;
                    Intent intent = new Intent(Intent.ACTION_SEND_MULTIPLE)
                            .setType("text/csv")
                            .putParcelableArrayListExtra(Intent.EXTRA_STREAM, files)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    ClipData clip = ClipData.newRawUri("CSV Replay", files.get(0));
                    for (int i = 1; i < files.size(); i++) clip.addItem(new ClipData.Item(files.get(i)));
                    intent.setClipData(clip);
                    activity.startActivity(Intent.createChooser(intent, "Xuất CSV demo T4"));
                });
            } catch (IOException | IllegalArgumentException exception) {
                activity.runOnUiThread(() -> Toast.makeText(context,
                        "Không thể chuẩn bị đủ file CSV.", Toast.LENGTH_LONG).show());
            }
        }, "replay-demo-export").start();
    }

    private static ArrayList<Uri> prepare(Context context, List<ReplayResult> results,
                                          Function<UUID, File> rawCsv) throws IOException {
        File folder = new File(context.getCacheDir(), "replay-demo/" + UUID.randomUUID());
        if (!folder.mkdirs()) throw new IOException("Unable to create export directory");
        File summary = new File(folder, "t4_calibration_comparison.csv");
        try (FileOutputStream output = new FileOutputStream(summary)) {
            output.write(comparisonCsv(results).getBytes(StandardCharsets.UTF_8));
        }
        ArrayList<Uri> files = new ArrayList<>();
        String authority = context.getPackageName() + ".demo-export";
        files.add(FileProvider.getUriForFile(context, authority, summary));
        for (ReplayResult result : results) {
            File source = rawCsv.apply(result.runId);
            File copy = new File(folder, source.getName());
            try (FileInputStream input = new FileInputStream(source);
                 FileOutputStream output = new FileOutputStream(copy)) {
                byte[] buffer = new byte[4096];
                int size;
                while ((size = input.read(buffer)) != -1) output.write(buffer, 0, size);
            }
            files.add(FileProvider.getUriForFile(context, authority, copy));
        }
        return files;
    }

    static String comparisonCsv(List<ReplayResult> results) {
        StringBuilder csv = new StringBuilder(
                "mode,run_id,network,payload_scale,time_scale,simulation,confirmed_steps,transitions,"
                        + "full_hits,late_hits,misses,hit_rate,mean_load_latency_ms,simulated_wasted_bytes,"
                        + "lambda_initial,lambda_final,lambda_updates,lambda_reason\n");
        for (ReplayResult result : results) {
            ReplayResult.Metrics metrics = result.metrics;
            ReplayResult.Configuration config = result.configuration;
            PrefetchEngine.LambdaSnapshot lambda = result.lambda;
            csv.append(String.join(",", lambda == null ? "P0" : "P5_" + lambda.mode.name(),
                    result.runId.toString(), config.network.code, cell(config.payloadScale),
                    cell(config.timeScale), cell(config.simulationMode), cell(result.confirmedSteps),
                    cell(result.transitions), cell(metrics.fullHits), cell(metrics.lateHits),
                    cell(metrics.misses), cell(metrics.hitRate), cell(metrics.averageLoadLatencyMs),
                    cell(metrics.simulatedWastedBytes), cell(lambda == null ? null : lambda.initial),
                    cell(lambda == null ? null : lambda.current), cell(lambda == null ? null : lambda.updates),
                    cell(lambda == null ? null : lambda.reason))).append('\n');
        }
        return csv.toString();
    }

    private static String cell(Object value) {
        return value == null ? "" : value.toString();
    }
}
