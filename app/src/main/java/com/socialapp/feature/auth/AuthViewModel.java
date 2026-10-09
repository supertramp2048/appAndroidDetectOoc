package com.socialapp.feature.auth;

import android.os.CountDownTimer;
import android.util.Patterns;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.socialapp.core.network.ApiResult;
import com.socialapp.core.network.dto.UserResource;

import java.util.regex.Pattern;

public class AuthViewModel extends ViewModel {

    public static final boolean ENABLE_OAUTH = false; // Feature flag for Google/GitHub OAuth

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@(.+)$");

    private final AuthRepository repository;

    private final MutableLiveData<Integer> countdownLiveData = new MutableLiveData<>(0);
    private CountDownTimer countDownTimer;

    public AuthViewModel(@NonNull AuthRepository repository) {
        this.repository = repository;
    }

    public LiveData<ApiResult<UserResource>> login(@NonNull String email, @NonNull String password, boolean remember) {
        return repository.login(email, password, remember);
    }

    public LiveData<ApiResult<UserResource>> register(@NonNull String name, @NonNull String email,
                                                      @NonNull String password, @NonNull String passwordConfirmation) {
        return repository.register(name, email, password, passwordConfirmation);
    }

    public LiveData<ApiResult<String>> forgotPassword(@NonNull String email) {
        return repository.forgotPassword(email);
    }

    public LiveData<ApiResult<String>> resetPassword(@NonNull String token, @NonNull String email,
                                                     @NonNull String password, @NonNull String passwordConfirmation) {
        return repository.resetPassword(token, email, password, passwordConfirmation);
    }

    public LiveData<ApiResult<String>> sendEmailVerification() {
        startVerificationCountdown();
        return repository.sendEmailVerification();
    }

    public LiveData<ApiResult<UserResource>> refreshUser() {
        return repository.refreshUser();
    }

    public void startVerificationCountdown() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }

        countdownLiveData.setValue(60);

        countDownTimer = new CountDownTimer(60000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                int secondsLeft = (int) (millisUntilFinished / 1000);
                countdownLiveData.setValue(secondsLeft);
            }

            @Override
            public void onFinish() {
                countdownLiveData.setValue(0);
            }
        }.start();
    }

    public static boolean isValidEmail(@Nullable String email) {
        if (email == null || email.trim().isEmpty()) return false;
        try {
            if (Patterns.EMAIL_ADDRESS != null) {
                return Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches();
            }
        } catch (Throwable ignored) {
        }
        return EMAIL_PATTERN.matcher(email.trim()).matches();
    }

    @Nullable
    public String validateLogin(@NonNull String email, @NonNull String password) {
        if (email.trim().isEmpty()) {
            return "Vui lòng nhập địa chỉ email.";
        }
        if (!isValidEmail(email)) {
            return "Địa chỉ email không hợp lệ.";
        }
        if (password.trim().isEmpty()) {
            return "Vui lòng nhập mật khẩu.";
        }
        return null;
    }

    @Nullable
    public String validateRegister(@NonNull String name, @NonNull String email,
                                   @NonNull String password, @NonNull String passwordConfirmation) {
        if (name.trim().isEmpty()) {
            return "Vui lòng nhập họ và tên.";
        }
        if (name.trim().length() > 50) {
            return "Họ và tên không được vượt quá 50 ký tự.";
        }
        if (email.trim().isEmpty()) {
            return "Vui lòng nhập địa chỉ email.";
        }
        if (!isValidEmail(email)) {
            return "Địa chỉ email không hợp lệ.";
        }
        if (password.trim().length() < 8) {
            return "Mật khẩu phải chứa ít nhất 8 ký tự.";
        }
        if (!password.equals(passwordConfirmation)) {
            return "Mật khẩu xác nhận không khớp.";
        }
        return null;
    }

    @NonNull
    public LiveData<Integer> getCountdownLiveData() {
        return countdownLiveData;
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (countDownTimer != null) {
            countDownTimer.cancel();
            countDownTimer = null;
        }
    }
}
