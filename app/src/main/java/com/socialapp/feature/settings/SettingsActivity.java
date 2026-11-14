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

        binding.itemMockNetworkLab.setOnClickListener(v -> {
            startActivity(new android.content.Intent(this, com.socialapp.core.network.sim.MockNetworkLabActivity.class));
        });

        binding.itemAboutApp.setOnClickListener(v -> Navigator.navigateTo(this, Navigator.ROUTE_ABOUT));

        binding.btnSettingsLogout.setOnClickListener(v -> {
            ConfirmDialog dialog = new ConfirmDialog(
                    this,
                    "ÄÄƒng xuáº¥t",
                    "Báº¡n cÃ³ cháº¯c cháº¯n muá»‘n Ä‘Äƒng xuáº¥t khá»i tÃ i khoáº£n?",
                    () -> {
                        sessionManager.logout();
                        Toast.makeText(this, "ÄÃ£ Ä‘Äƒng xuáº¥t", Toast.LENGTH_SHORT).show();
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
                binding.settingsUserNameText.setText(user.name != null ? user.name : "ThÃ nh viÃªn");

                if (binding.settingsUserAvatar != null && user.getAvatarUrl() != null) {
                    binding.settingsUserAvatar.loadAvatar(user.getAvatarUrl());
                }
                if (binding.settingsUserEmailText != null && user.email != null) {
                    binding.settingsUserEmailText.setText(user.email);
                }
                if (binding.settingsUserRoleChip != null && user.role != null) {
                    binding.settingsUserRoleChip.setText(user.role.toUpperCase());
                }

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
        input.setHint("TÃªn hiá»ƒn thá»‹ (tá»‘i Ä‘a 50 kÃ½ tá»±)");
        input.setText(currentName);
        input.setSelection(currentName.length());

        new AlertDialog.Builder(this)
                .setTitle("Äá»•i tÃªn hiá»ƒn thá»‹")
                .setView(input)
                .setPositiveButton("LÆ°u", (dialog, which) -> {
                    String newName = input.getText().toString().trim();
                    if (newName.isEmpty()) {
                        Toast.makeText(this, "TÃªn khÃ´ng Ä‘Æ°á»£c Ä‘á»ƒ trá»‘ng", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (newName.length() > 50) {
                        Toast.makeText(this, "TÃªn khÃ´ng Ä‘Æ°á»£c vÆ°á»£t quÃ¡ 50 kÃ½ tá»±", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    Toast.makeText(this, "Äang cáº­p nháº­t tÃªn...", Toast.LENGTH_SHORT).show();

                    viewModel.updateName(newName).observe(this, result -> {
                        if (result != null && result.isSuccess()) {
                            Toast.makeText(this, "Äá»•i tÃªn thÃ nh cÃ´ng", Toast.LENGTH_SHORT).show();
                        } else if (result != null && result.isError()) {
                            Toast.makeText(this, result.error != null ? result.error.getMessage() : "Äá»•i tÃªn tháº¥t báº¡i", Toast.LENGTH_SHORT).show();
                        }
                    });
                })
                .setNegativeButton("Há»§y", null)
                .show();
    }

    private void showChangePasswordDialog() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 20, 40, 10);

        EditText inputCurrent = new EditText(this);
        inputCurrent.setHint("Máº­t kháº©u hiá»‡n táº¡i");
        inputCurrent.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        EditText inputNew = new EditText(this);
        inputNew.setHint("Máº­t kháº©u má»›i (tá»‘i thiá»ƒu 8 kÃ½ tá»±)");
        inputNew.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        EditText inputConfirm = new EditText(this);
        inputConfirm.setHint("XÃ¡c nháº­n máº­t kháº©u má»›i");
        inputConfirm.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        layout.addView(inputCurrent);
        layout.addView(inputNew);
        layout.addView(inputConfirm);

        new AlertDialog.Builder(this)
                .setTitle("Äá»•i máº­t kháº©u")
                .setView(layout)
                .setPositiveButton("LÆ°u", (dialog, which) -> {
                    String currentPw = inputCurrent.getText().toString().trim();
                    String newPw = inputNew.getText().toString().trim();
                    String confirmPw = inputConfirm.getText().toString().trim();

                    if (currentPw.isEmpty()) {
                        Toast.makeText(this, "Vui lÃ²ng nháº­p máº­t kháº©u hiá»‡n táº¡i", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (newPw.length() < 8) {
                        Toast.makeText(this, "Máº­t kháº©u má»›i pháº£i tá»« 8 kÃ½ tá»± trá»Ÿ lÃªn", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (!newPw.equals(confirmPw)) {
                        Toast.makeText(this, "XÃ¡c nháº­n máº­t kháº©u má»›i khÃ´ng khá»›p", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    Toast.makeText(this, "Äang Ä‘á»•i máº­t kháº©u...", Toast.LENGTH_SHORT).show();

                    viewModel.updatePassword(currentPw, newPw, confirmPw).observe(this, result -> {
                        if (result != null && result.isSuccess()) {
                            Toast.makeText(this, result.data != null ? result.data : "Äá»•i máº­t kháº©u thÃ nh cÃ´ng", Toast.LENGTH_SHORT).show();
                        } else if (result != null && result.isError()) {
                            AppError err = result.error;
                            String currentPwErr = err != null ? err.getFirstFieldError("current_password") : null;
                            if (currentPwErr != null) {
                                Toast.makeText(this, "Máº­t kháº©u hiá»‡n táº¡i khÃ´ng Ä‘Ãºng: " + currentPwErr, Toast.LENGTH_LONG).show();
                            } else {
                                Toast.makeText(this, err != null ? err.getMessage() : "Äá»•i máº­t kháº©u tháº¥t báº¡i", Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
                })
                .setNegativeButton("Há»§y", null)
                .show();
    }

    private void showDeleteAccountDialog() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 20, 40, 10);

        EditText inputPassword = new EditText(this);
        inputPassword.setHint("Nháº­p máº­t kháº©u tÃ i khoáº£n");
        inputPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        EditText inputConfirmText = new EditText(this);
        inputConfirmText.setHint("GÃµ láº¡i chá»¯ XOA Ä‘á»ƒ xÃ¡c nháº­n");

        layout.addView(inputPassword);
        layout.addView(inputConfirmText);

        new AlertDialog.Builder(this)
                .setTitle("XÃ³a tÃ i khoáº£n vÄ©nh viá»…n")
                .setMessage("HÃ nh Ä‘á»™ng nÃ y sáº½ xÃ³a vÄ©nh viá»…n tÃ i khoáº£n vÃ  toÃ n bá»™ dá»¯ liá»‡u cá»§a báº¡n.")
                .setView(layout)
                .setPositiveButton("XÃ³a vÄ©nh viá»…n", (dialog, which) -> {
                    String password = inputPassword.getText().toString().trim();
                    String confirmText = inputConfirmText.getText().toString().trim();

                    if (password.isEmpty()) {
                        Toast.makeText(this, "Vui lÃ²ng nháº­p máº­t kháº©u", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (!"XOA".equalsIgnoreCase(confirmText)) {
                        Toast.makeText(this, "Báº¡n cáº§n gÃµ Ä‘Ãºng chá»¯ XOA Ä‘á»ƒ xÃ¡c nháº­n xÃ³a", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    Toast.makeText(this, "Äang thá»±c thi xÃ³a tÃ i khoáº£n...", Toast.LENGTH_SHORT).show();

                    viewModel.deleteAccount(password).observe(this, result -> {
                        if (result != null && result.isSuccess()) {
                            Toast.makeText(this, "TÃ i khoáº£n cá»§a báº¡n Ä‘Ã£ Ä‘Æ°á»£c xÃ³a thÃ nh cÃ´ng", Toast.LENGTH_LONG).show();
                            finish();
                        } else if (result != null && result.isError()) {
                            Toast.makeText(this, result.error != null ? result.error.getMessage() : "XÃ³a tÃ i khoáº£n tháº¥t báº¡i", Toast.LENGTH_SHORT).show();
                        }
                    });
                })
                .setNegativeButton("Há»§y", null)
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

            Toast.makeText(this, isAvatarPick ? "Äang táº£i avatar má»›i..." : "Äang táº£i áº£nh bÃ¬a má»›i...", Toast.LENGTH_SHORT).show();

            if (isAvatarPick) {
                viewModel.updateAvatar(cacheFile).observe(this, result -> {
                    if (result != null && result.isSuccess()) {
                        Toast.makeText(this, "Cáº­p nháº­t Avatar thÃ nh cÃ´ng!", Toast.LENGTH_SHORT).show();
                    } else if (result != null && result.isError()) {
                        Toast.makeText(this, result.error != null ? result.error.getMessage() : "Táº£i avatar tháº¥t báº¡i", Toast.LENGTH_SHORT).show();
                    }
                });
            } else {
                viewModel.updateCoverPhoto(cacheFile).observe(this, result -> {
                    if (result != null && result.isSuccess()) {
                        Toast.makeText(this, "Cáº­p nháº­t áº¢nh bÃ¬a thÃ nh cÃ´ng!", Toast.LENGTH_SHORT).show();
                    } else if (result != null && result.isError()) {
                        Toast.makeText(this, result.error != null ? result.error.getMessage() : "Táº£i áº£nh bÃ¬a tháº¥t báº¡i", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        } catch (Exception e) {
            Toast.makeText(this, "Lá»—i Ä‘á»c file áº£nh: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}
