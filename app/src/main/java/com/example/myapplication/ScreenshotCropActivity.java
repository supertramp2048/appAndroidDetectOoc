package com.example.myapplication;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.yalantis.ucrop.UCrop;

import java.io.File;

public class ScreenshotCropActivity extends AppCompatActivity {
    public static final String EXTRA_IMAGE_PATH = "IMAGE_PATH";
    private static final String TAG = "ScreenshotCropActivity";
    private String sourcePath;
    private boolean cropStarted;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sourcePath = getIntent().getStringExtra(EXTRA_IMAGE_PATH);
        cropStarted = savedInstanceState != null && savedInstanceState.getBoolean("cropStarted", false);

        if (sourcePath == null || sourcePath.isEmpty()) {
            finish();
            return;
        }

        if (!cropStarted) {
            cropStarted = true;
            startCrop(sourcePath);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("cropStarted", cropStarted);
    }

    private void startCrop(String imagePath) {
        File sourceFile = new File(imagePath);
        if (!sourceFile.exists()) {
            notifyCropCanceled();
            finish();
            return;
        }

        File outputFile = new File(getCacheDir(), "screenshot_crop_" + System.currentTimeMillis() + ".jpg");
        UCrop.of(Uri.fromFile(sourceFile), Uri.fromFile(outputFile))
                .withOptions(CropOptions.create())
                .start(this);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == UCrop.REQUEST_CROP && resultCode == RESULT_OK && data != null) {
            Uri resultUri = UCrop.getOutput(data);
            if (resultUri != null && resultUri.getPath() != null) {
                notifyCropFinished(resultUri.getPath());
                finish();
                return;
            }
        } else if (requestCode == UCrop.REQUEST_CROP && resultCode == UCrop.RESULT_ERROR && data != null) {
            Log.e(TAG, "Crop error", UCrop.getError(data));
        }

        notifyCropCanceled();
        finish();
    }

    private void notifyCropFinished(String imagePath) {
        Log.d(TAG, "Crop finished, requesting caption dialog for: " + imagePath);
        Intent intent = new Intent(this, ScreenCaptureService.class);
        intent.setAction(ScreenCaptureService.ACTION_SHOW_CAPTION_FOR_IMAGE);
        intent.putExtra(ScreenCaptureService.EXTRA_IMAGE_PATH, imagePath);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    private void notifyCropCanceled() {
        Log.d(TAG, "Crop canceled");
        Intent intent = new Intent(this, ScreenCaptureService.class);
        intent.setAction(ScreenCaptureService.ACTION_CROP_CANCELED);
        startService(intent);
    }
}
