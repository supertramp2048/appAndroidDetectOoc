package com.socialapp.feature.settings;

import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.socialapp.MyApplication;
import com.socialapp.core.navigation.Navigator;
import com.socialapp.core.network.AppError;
import com.socialapp.core.network.dto.UserResource;
import com.socialapp.core.session.SessionManager;
import com.socialapp.core.ui.ConfirmDialog;
import com.socialapp.databinding.ActivitySettingsBinding;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class SettingsActivity extends AppCompatActivity {

    private ActivitySettingsBinding binding;
    private SettingsViewModel viewModel;
    private SessionManager sessionManager;

    private boolean isAvatarPick = true;

    private final ActivityResultLauncher<String> imagePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    processPickedImage(uri);
                }
            }
    );

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        sessionManager = MyApplication.getAppContainer(this).getSessionManager();
        SettingsRepository repository = new SettingsRepository(
                MyApplication.getAppContainer(this).getSocialAppApiService(),
                sessionManager
        );
        viewModel = new ViewModelProvider(this, new SettingsViewModelFactory(repository)).get(SettingsViewModel.class);

        setupUI();
        observeSession();
    }

    private void setupUI() {
        binding.settingsToolbar.setNavigationOnClickListener(v -> finish());

        binding.itemEditName.setOnClickListener(v -> showEditNameDialog());

        binding.itemEditAvatar.setOnClickListener(v -> {
            isAvatarPick = true;
            imagePickerLauncher.launch("image/*");
        });

        binding.itemEditCover.setOnClickListener(v -> {
            isAvatarPick = false;
            imagePickerLauncher.launch("image/*");
        });

        binding.itemChangePassword.setOnClickListener(v -> showChangePasswordDialog());

        binding.itemAboutApp.setOnClickListener(v -> Navigator.navigateTo(this, Navigator.ROUTE_ABOUT));

        binding.btnSettingsLogout.setOnClickListener(v -> {
            ConfirmDialog dialog = new ConfirmDialog(
                    this,
                    "Đăng xuất",
                    "Bạn có chắc chắn muốn đăng xuất khỏi tài khoản?",
                    () -> {
                        sessionManager.logout();
                        Toast.makeText(this, "Đã đăng xuất", Toast.LENGTH_SHORT).show();
                        finish();
                    }
            );
            dialog.show();
        });

        binding.btnSettingsDeleteAccount.setOnClickListener(v -> showDeleteAccountDialog());
    }

    private void observeSession() {
        sessionManager.getSessionLiveData().observe(this, state -> {
            if (state != null && state.isLoggedIn && state.user != null) {
                UserResource user = state.user;
                binding.settingsUserNameText.setText(user.name != null ? user.name : "Thành viên");

                if (user.role != null && ("admin".equalsIgnoreCase(user.role)
                        || "moderator".equalsIgnoreCase(user.role)
                        || "superadmin".equalsIgnoreCase(user.role))) {
                    binding.dividerAdmin.setVisibility(View.VISIBLE);
                    binding.itemAdminPanel.setVisibility(View.VISIBLE);
                    binding.itemAdminPanel.setOnClickListener(v -> Navigator.navigateTo(this, Navigator.ROUTE_ADMIN));
                } else {
                    binding.dividerAdmin.setVisibility(View.GONE);
                    binding.itemAdminPanel.setVisibility(View.GONE);
                }
            } else {
                finish();
            }
        });
    }

    private void showEditNameDialog() {
        UserResource user = sessionManager.getCurrentUser();
        String currentName = user != null && user.name != null ? user.name : "";

        EditText input = new EditText(this);
        input.setHint("Tên hiển thị (tối đa 50 ký tự)");
        input.setText(currentName);
        input.setSelection(currentName.length());

        new AlertDialog.Builder(this)
                .setTitle("Đổi tên hiển thị")
                .setView(input)
                .setPositiveButton("Lưu", (dialog, which) -> {
                    String newName = input.getText().toString().trim();
                    if (newName.isEmpty()) {
                        Toast.makeText(this, "Tên không được để trống", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (newName.length() > 50) {
                        Toast.makeText(this, "Tên không được vượt quá 50 ký tự", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    Toast.makeText(this, "Đang cập nhật tên...", Toast.LENGTH_SHORT).show();

                    viewModel.updateName(newName).observe(this, result -> {
                        if (result != null && result.isSuccess()) {
                            Toast.makeText(this, "Đổi tên thành công", Toast.LENGTH_SHORT).show();
                        } else if (result != null && result.isError()) {
                            Toast.makeText(this, result.error != null ? result.error.getMessage() : "Đổi tên thất bại", Toast.LENGTH_SHORT).show();
                        }
                    });
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void showChangePasswordDialog() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 20, 40, 10);

        EditText inputCurrent = new EditText(this);
        inputCurrent.setHint("Mật khẩu hiện tại");
        inputCurrent.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        EditText inputNew = new EditText(this);
        inputNew.setHint("Mật khẩu mới (tối thiểu 8 ký tự)");
        inputNew.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        EditText inputConfirm = new EditText(this);
        inputConfirm.setHint("Xác nhận mật khẩu mới");
        inputConfirm.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        layout.addView(inputCurrent);
        layout.addView(inputNew);
        layout.addView(inputConfirm);

        new AlertDialog.Builder(this)
                .setTitle("Đổi mật khẩu")
                .setView(layout)
                .setPositiveButton("Lưu", (dialog, which) -> {
                    String currentPw = inputCurrent.getText().toString().trim();
                    String newPw = inputNew.getText().toString().trim();
                    String confirmPw = inputConfirm.getText().toString().trim();

                    if (currentPw.isEmpty()) {
                        Toast.makeText(this, "Vui lòng nhập mật khẩu hiện tại", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (newPw.length() < 8) {
                        Toast.makeText(this, "Mật khẩu mới phải từ 8 ký tự trở lên", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (!newPw.equals(confirmPw)) {
                        Toast.makeText(this, "Xác nhận mật khẩu mới không khớp", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    Toast.makeText(this, "Đang đổi mật khẩu...", Toast.LENGTH_SHORT).show();

                    viewModel.updatePassword(currentPw, newPw, confirmPw).observe(this, result -> {
                        if (result != null && result.isSuccess()) {
                            Toast.makeText(this, result.data != null ? result.data : "Đổi mật khẩu thành công", Toast.LENGTH_SHORT).show();
                        } else if (result != null && result.isError()) {
                            AppError err = result.error;
                            String currentPwErr = err != null ? err.getFirstFieldError("current_password") : null;
                            if (currentPwErr != null) {
                                Toast.makeText(this, "Mật khẩu hiện tại không đúng: " + currentPwErr, Toast.LENGTH_LONG).show();
                            } else {
                                Toast.makeText(this, err != null ? err.getMessage() : "Đổi mật khẩu thất bại", Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void showDeleteAccountDialog() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 20, 40, 10);

        EditText inputPassword = new EditText(this);
        inputPassword.setHint("Nhập mật khẩu tài khoản");
        inputPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        EditText inputConfirmText = new EditText(this);
        inputConfirmText.setHint("Gõ lại chữ XOA để xác nhận");

        layout.addView(inputPassword);
        layout.addView(inputConfirmText);

        new AlertDialog.Builder(this)
                .setTitle("Xóa tài khoản vĩnh viễn")
                .setMessage("Hành động này sẽ xóa vĩnh viễn tài khoản và toàn bộ dữ liệu của bạn.")
                .setView(layout)
                .setPositiveButton("Xóa vĩnh viễn", (dialog, which) -> {
                    String password = inputPassword.getText().toString().trim();
                    String confirmText = inputConfirmText.getText().toString().trim();

                    if (password.isEmpty()) {
                        Toast.makeText(this, "Vui lòng nhập mật khẩu", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (!"XOA".equalsIgnoreCase(confirmText)) {
                        Toast.makeText(this, "Bạn cần gõ đúng chữ XOA để xác nhận xóa", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    Toast.makeText(this, "Đang thực thi xóa tài khoản...", Toast.LENGTH_SHORT).show();

                    viewModel.deleteAccount(password).observe(this, result -> {
                        if (result != null && result.isSuccess()) {
                            Toast.makeText(this, "Tài khoản của bạn đã được xóa thành công", Toast.LENGTH_LONG).show();
                            finish();
                        } else if (result != null && result.isError()) {
                            Toast.makeText(this, result.error != null ? result.error.getMessage() : "Xóa tài khoản thất bại", Toast.LENGTH_SHORT).show();
                        }
                    });
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void processPickedImage(@NonNull Uri uri) {
        try {
            File cacheFile = new File(getCacheDir(), "upload_" + System.currentTimeMillis() + ".jpg");
            InputStream inputStream = getContentResolver().openInputStream(uri);
            FileOutputStream outputStream = new FileOutputStream(cacheFile);
            byte[] buffer = new byte[1024];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
            outputStream.flush();
            outputStream.close();
            inputStream.close();

            Toast.makeText(this, isAvatarPick ? "Đang tải avatar mới..." : "Đang tải ảnh bìa mới...", Toast.LENGTH_SHORT).show();

            if (isAvatarPick) {
                viewModel.updateAvatar(cacheFile).observe(this, result -> {
                    if (result != null && result.isSuccess()) {
                        Toast.makeText(this, "Cập nhật Avatar thành công!", Toast.LENGTH_SHORT).show();
                    } else if (result != null && result.isError()) {
                        Toast.makeText(this, result.error != null ? result.error.getMessage() : "Tải avatar thất bại", Toast.LENGTH_SHORT).show();
                    }
                });
            } else {
                viewModel.updateCoverPhoto(cacheFile).observe(this, result -> {
                    if (result != null && result.isSuccess()) {
                        Toast.makeText(this, "Cập nhật Ảnh bìa thành công!", Toast.LENGTH_SHORT).show();
                    } else if (result != null && result.isError()) {
                        Toast.makeText(this, result.error != null ? result.error.getMessage() : "Tải ảnh bìa thất bại", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        } catch (Exception e) {
            Toast.makeText(this, "Lỗi đọc file ảnh: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}
