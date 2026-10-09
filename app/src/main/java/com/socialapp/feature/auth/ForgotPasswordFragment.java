package com.socialapp.feature.auth;

import android.os.Bundle;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.socialapp.MyApplication;
import com.socialapp.databinding.FragmentForgotPasswordBinding;

public class ForgotPasswordFragment extends Fragment {

    private FragmentForgotPasswordBinding binding;
    private AuthViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentForgotPasswordBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        AuthRepository repository = new AuthRepository(
                MyApplication.getAppContainer(requireContext()).getSocialAppApiService(),
                MyApplication.getAppContainer(requireContext()).getSessionManager()
        );
        viewModel = new AuthViewModel(repository);

        setupUI();
    }

    private void setupUI() {
        binding.btnForgotSubmit.setOnClickListener(v -> performForgotPassword());

        binding.btnForgotBackToLogin.setOnClickListener(v -> {
            if (getActivity() instanceof AuthActivity) {
                ((AuthActivity) getActivity()).openFragment(new LoginFragment());
            }
        });
    }

    private void performForgotPassword() {
        binding.layoutForgotEmail.setError(null);
        binding.forgotStatusText.setVisibility(View.GONE);

        String email = binding.inputForgotEmail.getText() != null ? binding.inputForgotEmail.getText().toString().trim() : "";
        if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.layoutForgotEmail.setError("Vui lòng nhập địa chỉ email hợp lệ.");
            return;
        }

        binding.btnForgotSubmit.setEnabled(false);

        viewModel.forgotPassword(email).observe(getViewLifecycleOwner(), result -> {
            if (result == null) return;

            if (result.isLoading()) {
                binding.btnForgotSubmit.setEnabled(false);
            } else if (result.isSuccess()) {
                binding.btnForgotSubmit.setEnabled(true);
                binding.forgotStatusText.setTextColor(0xFF12805C);
                binding.forgotStatusText.setText(result.data != null ? result.data : "Đã gửi yêu cầu thành công.");
                binding.forgotStatusText.setVisibility(View.VISIBLE);
            } else if (result.isError()) {
                binding.btnForgotSubmit.setEnabled(true);
                binding.forgotStatusText.setTextColor(0xFFD32F2F);
                binding.forgotStatusText.setText(result.error != null ? result.error.getMessage() : "Đã xảy ra lỗi.");
                binding.forgotStatusText.setVisibility(View.VISIBLE);
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
