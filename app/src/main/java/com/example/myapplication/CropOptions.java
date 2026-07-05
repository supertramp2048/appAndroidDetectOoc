package com.example.myapplication;

import android.graphics.Bitmap;

import com.yalantis.ucrop.UCrop;
import com.yalantis.ucrop.UCropActivity;

public final class CropOptions {
    private CropOptions() {
    }

    public static UCrop.Options create() {
        UCrop.Options options = new UCrop.Options();
        options.setFreeStyleCropEnabled(true);
        options.setHideBottomControls(false);
        options.setAllowedGestures(
                UCropActivity.SCALE,
                UCropActivity.ROTATE,
                UCropActivity.ALL
        );
        options.setCompressionFormat(Bitmap.CompressFormat.JPEG);
        options.setCompressionQuality(95);
        options.setToolbarTitle("Cắt / xoay ảnh");
        return options;
    }
}
