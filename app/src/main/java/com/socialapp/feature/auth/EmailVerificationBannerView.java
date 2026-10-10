package com.socialapp.feature.auth;

import android.content.Context;
import android.content.ContextWrapper;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LifecycleOwner;

import com.socialapp.MyApplication;
import com.socialapp.R;
import com.socialapp.core.session.SessionManager;

public class EmailVerificationBannerView extends LinearLayout {

    private Button btnResendEmail;
    private Button btnCheckVerification;

    private SessionManager sessionManager;
    private AuthViewModel viewModel;

    public EmailVerificationBannerView(@NonNull Context context) {
        super(context);
        init(context);
    }

    public EmailVerificationBannerView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public EmailVerificationBannerView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(@NonNull Context context) {
        LayoutInflater.from(context).inflate(R.layout.view_email_verification_banner, this, true);
        btnResendEmail = findViewById(R.id.btn_resend_email);
        btnCheckVerification = findViewById(R.id.btn_check_verification);
        setVisibility(GONE);

        if (!isInEditMode()) {
            sessionManager = MyApplication.getAppContainer(context).getSessionManager();
            AuthRepository repository = new AuthRepository(
                    MyApplication.getAppContainer(context).getSocialAppApiService(),
                    sessionManager
            );
            viewModel = new AuthViewModel(repository);

            btnResendEmail.setOnClickListener(v -> resendEmail());
            btnCheckVerification.setOnClickListener(v -> checkVerification());
        }
    }

    public void setupObserver(@NonNull LifecycleOwner lifecycleOwner) {
        setVisibility(GONE);
    }

    @Nullable
    private LifecycleOwner findLifecycleOwner(@Nullable Context context) {
        if (context instanceof LifecycleOwner) {
            return (LifecycleOwner) context;
        } else if (context instanceof ContextWrapper) {
            return findLifecycleOwner(((ContextWrapper) context).getBaseContext());
        }
        return null;
    }

    private void resendEmail() {
        if (viewModel == null) return;
        LifecycleOwner owner = findLifecycleOwner(getContext());
        if (owner == null) return;

        viewModel.sendEmailVerification().observe(owner, result -> {
            if (result != null && result.isSuccess()) {
                Toast.makeText(getContext(), result.data != null ? result.data : "Đã gửi lại thư!", Toast.LENGTH_SHORT).show();
            } else if (result != null && result.isError()) {
                Toast.makeText(getContext(), result.error != null ? result.error.getMessage() : "Gửi thư thất bại", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void checkVerification() {
        if (viewModel == null) return;
        LifecycleOwner owner = findLifecycleOwner(getContext());
        if (owner == null) return;

        viewModel.refreshUser().observe(owner, result -> {
            if (result != null && result.isSuccess() && result.data != null) {
                if (result.data.isEmailVerified()) {
                    Toast.makeText(getContext(), "Email của bạn đã được xác thực!", Toast.LENGTH_SHORT).show();
                    setVisibility(GONE);
                } else {
                    Toast.makeText(getContext(), "Email chưa được xác thực. Vui lòng kiểm tra lại hòm thư.", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }
}
