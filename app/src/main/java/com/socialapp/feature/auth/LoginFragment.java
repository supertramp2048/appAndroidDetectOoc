package com.socialapp.feature.auth;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.socialapp.MyApplication;
import com.socialapp.core.network.ApiResult;
import com.socialapp.core.network.AppError;
import com.socialapp.databinding.FragmentLoginBinding;

public class LoginFragment extends Fragment {

    private FragmentLoginBinding binding;
    private AuthViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentLoginBinding.inflate(inflater, container, false);
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
        if (AuthViewModel.ENABLE_OAUTH) {
            binding.oauthContainer.setVisibility(View.VISIBLE);
        } else {
            binding.oauthContainer.setVisibility(View.GONE);
        }

        binding.btnLoginSubmit.setOnClickListener(v -> performLogin());

        binding.btnGoToRegister.setOnClickListener(v -> {
            if (getActivity() instanceof AuthActivity) {
                ((AuthActivity) getActivity()).openFragment(new RegisterFragment());
            }
        });

        binding.btnForgotPassword.setOnClickListener(v -> {
            if (getActivity() instanceof AuthActivity) {
                ((AuthActivity) getActivity()).openFragment(new ForgotPasswordFragment());
            }
        });
    }

    private void performLogin() {
        binding.layoutLoginEmail.setError(null);
        binding.layoutLoginPassword.setError(null);
        binding.loginErrorText.setVisibility(View.GONE);

        String email = binding.inputLoginEmail.getText() != null ? binding.inputLoginEmail.getText().toString().trim() : "";
        String password = binding.inputLoginPassword.getText() != null ? binding.inputLoginPassword.getText().toString().trim() : "";

        String validationError = viewModel.validateLogin(email, password);
        if (validationError != null) {
            binding.loginErrorText.setText(validationError);
            binding.loginErrorText.setVisibility(View.VISIBLE);
            return;
        }

        binding.btnLoginSubmit.setEnabled(false);

        viewModel.login(email, password, true).observe(getViewLifecycleOwner(), result -> {
            if (result == null) return;

            if (result.isLoading()) {
                binding.btnLoginSubmit.setEnabled(false);
            } else if (result.isSuccess()) {
                binding.btnLoginSubmit.setEnabled(true);
                Toast.makeText(requireContext(), "Đăng nhập thành công!", Toast.LENGTH_SHORT).show();
                if (getActivity() != null) {
                    getActivity().finish();
                }
            } else if (result.isError()) {
                binding.btnLoginSubmit.setEnabled(true);
                handleLoginError(result.error);
            }
        });
    }

    private void handleLoginError(@Nullable AppError error) {
        if (error == null) return;

        if (error.getType() == AppError.ErrorType.VALIDATION_ERROR) {
            String emailError = error.getFirstFieldError("email");
            String passwordError = error.getFirstFieldError("password");

            if (emailError != null) {
                binding.layoutLoginEmail.setError(emailError);
            }
            if (passwordError != null) {
                binding.layoutLoginPassword.setError(passwordError);
            }

            if (emailError == null && passwordError == null) {
                binding.loginErrorText.setText(error.getMessage());
                binding.loginErrorText.setVisibility(View.VISIBLE);
            }
        } else {
            binding.loginErrorText.setText(error.getMessage());
            binding.loginErrorText.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
