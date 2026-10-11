package com.socialapp.feature.factcheck;

import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class MediaProjectionActivity extends AppCompatActivity {

    public static final String EXTRA_CROP_LEFT = "CROP_LEFT";
    public static final String EXTRA_CROP_TOP = "CROP_TOP";
    public static final String EXTRA_CROP_RIGHT = "CROP_RIGHT";
    public static final String EXTRA_CROP_BOTTOM = "CROP_BOTTOM";

    private int cropLeft = -1, cropTop = -1, cropRight = -1, cropBottom = -1;

    private final ActivityResultLauncher<Intent> mediaProjectionLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Intent serviceIntent = new Intent(this, ScreenCaptureService.class);
                    if (cropLeft >= 0 && cropTop >= 0 && cropRight > cropLeft && cropBottom > cropTop) {
                        serviceIntent.setAction(ScreenCaptureService.ACTION_EXECUTE_CAPTURE);
                        serviceIntent.putExtra(EXTRA_CROP_LEFT, cropLeft);
                        serviceIntent.putExtra(EXTRA_CROP_TOP, cropTop);
                        serviceIntent.putExtra(EXTRA_CROP_RIGHT, cropRight);
                        serviceIntent.putExtra(EXTRA_CROP_BOTTOM, cropBottom);
                    } else {
                        serviceIntent.setAction(ScreenCaptureService.ACTION_INIT_MEDIA_PROJECTION);
                    }
                    serviceIntent.putExtra("RESULT_CODE", result.getResultCode());
                    serviceIntent.putExtra("DATA", result.getData());

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(serviceIntent);
                    } else {
                        startService(serviceIntent);
                    }
                    finish();
                } else {
                    Toast.makeText(this, "Đã hủy cấp quyền chụp màn hình", Toast.LENGTH_SHORT).show();
                    Intent cancelIntent = new Intent(this, ScreenCaptureService.class);
                    cancelIntent.setAction(ScreenCaptureService.ACTION_CROP_CANCELED);
                    startService(cancelIntent);
                    finish();
                }
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        cropLeft = getIntent().getIntExtra(EXTRA_CROP_LEFT, -1);
        cropTop = getIntent().getIntExtra(EXTRA_CROP_TOP, -1);
        cropRight = getIntent().getIntExtra(EXTRA_CROP_RIGHT, -1);
        cropBottom = getIntent().getIntExtra(EXTRA_CROP_BOTTOM, -1);

        MediaProjectionManager projectionManager =
                (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        if (projectionManager != null) {
            mediaProjectionLauncher.launch(projectionManager.createScreenCaptureIntent());
        } else {
            finish();
        }
    }
}
