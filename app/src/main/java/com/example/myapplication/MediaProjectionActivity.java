package com.example.myapplication;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

public class MediaProjectionActivity extends ComponentActivity {

    private ActivityResultLauncher<Intent> screenCaptureLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        MediaProjectionManager manager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        
        screenCaptureLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Intent serviceIntent = new Intent(this, ScreenCaptureService.class);
                        serviceIntent.setAction("START_CAPTURE");
                        serviceIntent.putExtra("RESULT_CODE", result.getResultCode());
                        serviceIntent.putExtra("DATA", result.getData());
                        startForegroundService(serviceIntent);
                    }
                    finish();
                }
        );

        screenCaptureLauncher.launch(manager.createScreenCaptureIntent());
    }
}
