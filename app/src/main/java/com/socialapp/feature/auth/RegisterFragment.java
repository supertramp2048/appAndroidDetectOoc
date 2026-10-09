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
import com.socialapp.core.network.AppError;
import com.socialapp.databinding.FragmentRegisterBinding;

public class RegisterFragment extends Fragment {

    private FragmentRegisterBinding binding;
    private AuthViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentRegisterBinding.inflate(inflater, container, false);
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
        binding.btnRegisterSubmit.setOnClickListener(v -> performRegister());

        binding.btnGoToLogin.setOnClickListener(v -> {
            if (getActivity() instanceof AuthActivity) {
                ((AuthActivity) getActivity()).openFragment(new LoginFragment());
            }
        });
    }

    private void performRegister() {
        binding.layoutRegisterName.setError(null);
        binding.layoutRegisterEmail.setError(null);
        binding.layoutRegisterPassword.setError(null);
        binding.layoutRegisterPasswordConfirm.setError(null);
        binding.registerErrorText.setVisibility(View.GONE);

        String name = binding.inputRegisterName.getText() != null ? binding.inputRegisterName.getText().toString().trim() : "";
        String email = binding.inputRegisterEmail.getText() != null ? binding.inputRegisterEmail.getText().toString().trim() : "";
        String password = binding.inputRegisterPassword.getText() != null ? binding.inputRegisterPassword.getText().toString().trim() : "";
        String confirmPassword = binding.inputRegisterPasswordConfirm.getText() != null ? binding.inputRegisterPasswordConfirm.getText().toString().trim() : "";

        String validationError = viewModel.validateRegister(name, email, password, confirmPassword);
        if (validationError != null) {
            binding.registerErrorText.setText(validationError);
            binding.registerErrorText.setVisibility(View.VISIBLE);
            return;
        }

        binding.btnRegisterSubmit.setEnabled(false);

        viewModel.register(name, email, password, confirmPassword).observe(getViewLifecycleOwner(), result -> {
            if (result == null) return;

            if (result.isLoading()) {
                binding.btnRegisterSubmit.setEnabled(false);
            } else if (result.isSuccess()) {
                binding.btnRegisterSubmit.setEnabled(true);
                Toast.makeText(requireContext(), "Đăng ký thành công!", Toast.LENGTH_SHORT).show();
                if (getActivity() != null) {
                    getActivity().finish();
                }
            } else if (result.isError()) {
                binding.btnRegisterSubmit.setEnabled(true);
                handleRegisterError(result.error);
            }
        });
    }

    private void handleRegisterError(@Nullable AppError error) {
        if (error == null) return;

        if (error.getType() == AppError.ErrorType.VALIDATION_ERROR) {
            String nameError = error.getFirstFieldError("name");
            String emailError = error.getFirstFieldError("email");
            String passwordError = error.getFirstFieldError("password");

            if (nameError != null) {
                binding.layoutRegisterName.setError(nameError);
            }
            if (emailError != null) {
                binding.layoutRegisterEmail.setError(emailError);
            }
            if (passwordError != null) {
                binding.layoutRegisterPassword.setError(passwordError);
            }

            if (nameError == null && emailError == null && passwordError == null) {
                binding.registerErrorText.setText(error.getMessage());
                binding.registerErrorText.setVisibility(View.VISIBLE);
            }
        } else {
            binding.registerErrorText.setText(error.getMessage());
            binding.registerErrorText.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
