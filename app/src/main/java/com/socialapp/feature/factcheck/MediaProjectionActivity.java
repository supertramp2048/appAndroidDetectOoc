package com.socialapp.feature.factcheck;

import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class MediaProjectionActivity extends AppCompatActivity {

    private final ActivityResultLauncher<Intent> mediaProjectionLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Intent serviceIntent = new Intent(this, ScreenCaptureService.class);
                    serviceIntent.setAction("START_CAPTURE");
                    serviceIntent.putExtra("RESULT_CODE", result.getResultCode());
                    serviceIntent.putExtra("DATA", result.getData());

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(serviceIntent);
                    } else {
                        startService(serviceIntent);
                    }
                }
                finish();
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        MediaProjectionManager projectionManager =
                (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        if (projectionManager != null) {
            mediaProjectionLauncher.launch(projectionManager.createScreenCaptureIntent());
        } else {
            finish();
        }
    }
}
