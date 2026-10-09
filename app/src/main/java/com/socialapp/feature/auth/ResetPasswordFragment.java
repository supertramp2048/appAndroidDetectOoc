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
import com.socialapp.databinding.FragmentResetPasswordBinding;

public class ResetPasswordFragment extends Fragment {

    private static final String ARG_TOKEN = "ARG_TOKEN";
    private static final String ARG_EMAIL = "ARG_EMAIL";

    private FragmentResetPasswordBinding binding;
    private AuthViewModel viewModel;

    public static ResetPasswordFragment newInstance(@Nullable String token, @Nullable String email) {
        ResetPasswordFragment fragment = new ResetPasswordFragment();
        Bundle args = new Bundle();
        if (token != null) args.putString(ARG_TOKEN, token);
        if (email != null) args.putString(ARG_EMAIL, email);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentResetPasswordBinding.inflate(inflater, container, false);
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

        if (getArguments() != null) {
            String token = getArguments().getString(ARG_TOKEN);
            String email = getArguments().getString(ARG_EMAIL);

            if (token != null) binding.inputResetToken.setText(token);
            if (email != null) binding.inputResetEmail.setText(email);
        }

        setupUI();
    }

    private void setupUI() {
        binding.btnResetSubmit.setOnClickListener(v -> performResetPassword());

        binding.btnResetBackToLogin.setOnClickListener(v -> {
            if (getActivity() instanceof AuthActivity) {
                ((AuthActivity) getActivity()).openFragment(new LoginFragment());
            }
        });
    }

    private void performResetPassword() {
        binding.layoutResetToken.setError(null);
        binding.layoutResetEmail.setError(null);
        binding.layoutResetPassword.setError(null);
        binding.layoutResetPasswordConfirm.setError(null);
        binding.resetStatusText.setVisibility(View.GONE);

        String token = binding.inputResetToken.getText() != null ? binding.inputResetToken.getText().toString().trim() : "";
        String email = binding.inputResetEmail.getText() != null ? binding.inputResetEmail.getText().toString().trim() : "";
        String password = binding.inputResetPassword.getText() != null ? binding.inputResetPassword.getText().toString().trim() : "";
        String confirmPassword = binding.inputResetPasswordConfirm.getText() != null ? binding.inputResetPasswordConfirm.getText().toString().trim() : "";

        if (token.isEmpty()) {
            binding.layoutResetToken.setError("Vui lòng nhập mã Token xác thực.");
            return;
        }
        if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.layoutResetEmail.setError("Vui lòng nhập địa chỉ email hợp lệ.");
            return;
        }
        if (password.length() < 8) {
            binding.layoutResetPassword.setError("Mật khẩu phải chứa ít nhất 8 ký tự.");
            return;
        }
        if (!password.equals(confirmPassword)) {
            binding.layoutResetPasswordConfirm.setError("Mật khẩu xác nhận không khớp.");
            return;
        }

        binding.btnResetSubmit.setEnabled(false);

        viewModel.resetPassword(token, email, password, confirmPassword).observe(getViewLifecycleOwner(), result -> {
            if (result == null) return;

            if (result.isLoading()) {
                binding.btnResetSubmit.setEnabled(false);
            } else if (result.isSuccess()) {
                binding.btnResetSubmit.setEnabled(true);
                Toast.makeText(requireContext(), "Đặt lại mật khẩu thành công!", Toast.LENGTH_SHORT).show();
                if (getActivity() instanceof AuthActivity) {
                    ((AuthActivity) getActivity()).openFragment(new LoginFragment());
                }
            } else if (result.isError()) {
                binding.btnResetSubmit.setEnabled(true);
                binding.resetStatusText.setTextColor(0xFFD32F2F);
                binding.resetStatusText.setText(result.error != null ? result.error.getMessage() : "Đã xảy ra lỗi.");
                binding.resetStatusText.setVisibility(View.VISIBLE);
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
