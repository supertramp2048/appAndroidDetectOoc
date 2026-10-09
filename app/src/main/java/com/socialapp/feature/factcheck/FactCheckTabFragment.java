package com.socialapp.feature.factcheck;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.socialapp.R;

public class FactCheckTabFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_main, container, false);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() != null) {
            Intent serviceIntent = new Intent(getActivity(), ScreenCaptureService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                getActivity().startForegroundService(serviceIntent);
            } else {
                getActivity().startService(serviceIntent);
            }
        }
    }
}
