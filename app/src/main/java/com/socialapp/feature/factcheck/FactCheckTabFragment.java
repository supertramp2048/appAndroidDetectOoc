package com.socialapp.feature.factcheck;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

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
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        View btnGoToDetection = view.findViewById(R.id.btnGoToDetection);
        if (btnGoToDetection != null) {
            btnGoToDetection.setOnClickListener(v -> {
                if (!Settings.canDrawOverlays(requireContext())) {
                    Toast.makeText(requireContext(), "Vui lòng cấp quyền hiển thị trên ứng dụng khác để bật bong bóng quét tin giả", Toast.LENGTH_LONG).show();
                    Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:" + requireContext().getPackageName()));
                    startActivity(intent);
                    return;
                }

                if (ScreenCaptureService.isProjectionReady()) {
                    Toast.makeText(requireContext(), "Bong bóng kiểm tra tin giả đã sẵn sàng! Chạm vào biểu tượng máy ảnh nổi để khoanh vùng bài viết.", Toast.LENGTH_LONG).show();
                    if (getActivity() != null) {
                        getActivity().moveTaskToBack(true);
                    }
                } else {
                    Intent intent = new Intent(requireContext(), MediaProjectionActivity.class);
                    startActivity(intent);
                }
            });
        }

        View btnGoToHistory = view.findViewById(R.id.btnGoToHistory);
        if (btnGoToHistory != null) {
            btnGoToHistory.setOnClickListener(v -> {
                Intent intent = new Intent(requireContext(), HistoryActivity.class);
                startActivity(intent);
            });
        }
    }
}
