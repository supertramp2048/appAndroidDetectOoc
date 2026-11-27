package com.socialapp.feature.compose;

import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.bumptech.glide.Glide;
import com.google.gson.Gson;
import com.socialapp.MyApplication;
import com.socialapp.core.di.AppContainer;
import com.socialapp.core.navigation.Navigator;
import com.socialapp.core.network.dto.CategoryResource;
import com.socialapp.core.network.dto.PostResource;
import com.socialapp.core.ui.ConfirmDialog;
import com.socialapp.core.ui.blocks.editor.EditorWebViewPool;
import com.socialapp.core.ui.blocks.editor.WebAppInterfaceProxy;
import com.socialapp.databinding.ActivityComposeBinding;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Rich-text article composer & editor using EditorWebViewPool + WebAppInterfaceProxy
 * so the pre-warmed editor WebView attaches in ~0ms without leaking ComposeActivity.
 */
public class ComposeActivity extends AppCompatActivity {

    public static final String EXTRA_POST_ID = "EXTRA_POST_ID";
    public static final String EXTRA_PREFILL_TITLE = "title";
    public static final String EXTRA_PREFILL_IMAGE = "imageUrl";
    public static final String EXTRA_PREFILL_BODY = "body";

    private ActivityComposeBinding binding;
    private ComposeViewModel viewModel;
    private EditorWebViewPool editorWebViewPool;
    @Nullable
    private EditorWebViewPool.EditorHandle editorHandle;

    private List<CategoryResource> categoryList = new ArrayList<>();
    private boolean isInlineImagePick = false;
    private boolean isEditorLoaded = false;

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
        binding = ActivityComposeBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        AppContainer container = MyApplication.getAppContainer(this);
        editorWebViewPool = container.getEditorWebViewPool();

        ComposeRepository repository = new ComposeRepository(container.getSocialAppApiService());
        viewModel = new ViewModelProvider(this, new ComposeViewModelFactory(repository)).get(ComposeViewModel.class);

        int postId = getIntent().getIntExtra(EXTRA_POST_ID, -1);
        viewModel.setPostId(postId);

        // Initialize draft / prefill before acquiring pooled WebView so synchronous warm attach gets initial HTML
        if (postId <= 0) {
            String prefillTitle = getIntent().getStringExtra(EXTRA_PREFILL_TITLE);
            String prefillImage = getIntent().getStringExtra(EXTRA_PREFILL_IMAGE);
            String prefillBody = getIntent().getStringExtra(EXTRA_PREFILL_BODY);

            if (prefillTitle != null || prefillImage != null || prefillBody != null) {
                if (prefillTitle != null) viewModel.setTitle(prefillTitle);
                if (prefillImage != null) viewModel.setThumbnailUrl(prefillImage);
                if (prefillBody != null) viewModel.setContentHtml("<p>" + prefillBody + "</p>");
            } else {
                viewModel.loadDraft(this);
            }
        }

        setupUI();
        observeViewModel();

        if (postId > 0) {
            binding.composeToolbar.setTitle("Sửa bài viết");
            binding.btnSubmitPost.setText("Cập nhật");
            binding.btnDeletePost.setVisibility(View.VISIBLE);
            viewModel.loadPostDetail(postId);
        } else {
            binding.composeToolbar.setTitle("Tạo bài viết");
            binding.btnSubmitPost.setText("Đăng bài");
            binding.btnDeletePost.setVisibility(View.GONE);
            if (!viewModel.getTitle().isEmpty()) {
                binding.composeTitleEditText.setText(viewModel.getTitle());
            }
            if (!viewModel.getThumbnailUrl().isEmpty()) {
                updateThumbnailPreview(viewModel.getThumbnailUrl());
            }
        }

        viewModel.loadCategories();
    }

    private void setupUI() {
        binding.composeToolbar.setNavigationOnClickListener(v -> handleExitPrompt());
        binding.composeEmailBanner.setupObserver(this);

        setupWebViewEditor();

        binding.composeTitleEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                viewModel.setTitle(s.toString());
                binding.composeTitleCounter.setText(s.length() + "/255");
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        binding.btnSelectThumbnail.setOnClickListener(v -> {
            isInlineImagePick = false;
            imagePickerLauncher.launch("image/*");
        });

        binding.btnSubmitPost.setOnClickListener(v -> handleSubmitPost());

        binding.btnDeletePost.setOnClickListener(v -> handleDeletePost());
    }

    private void setupWebViewEditor() {
        WebAppInterfaceProxy.EditorBridgeDelegate bridgeDelegate = new WebAppInterfaceProxy.EditorBridgeDelegate() {
            @Override
            public void onPickImageRequested() {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    isInlineImagePick = true;
                    imagePickerLauncher.launch("image/*");
                });
            }

            @Override
            public void onContentChanged(@NonNull String html) {
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) {
                        viewModel.setContentHtml(html);
                    }
                });
            }
        };

        editorHandle = editorWebViewPool.acquire(this, bridgeDelegate, () -> {
            if (isFinishing() || isDestroyed()) {
                return;
            }
            isEditorLoaded = true;
            setEditorContent(viewModel.getContentHtml());
        });

        WebView webView = editorHandle.getWebView();
        if (webView != null) {
            binding.composeEditorContainer.removeAllViews();
            binding.composeEditorContainer.addView(webView, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));
        }
    }

    private void observeViewModel() {
        viewModel.getCategoriesLiveData().observe(this, result -> {
            if (result != null && result.isSuccess() && result.data != null) {
                categoryList = result.data;
                setupCategorySpinner();
            }
        });

        viewModel.getPostDetailLiveData().observe(this, result -> {
            if (result != null && result.isSuccess() && result.data != null) {
                PostResource post = result.data;
                binding.composeTitleEditText.setText(post.title != null ? post.title : "");
                if (isEditorLoaded) {
                    setEditorContent(viewModel.getContentHtml());
                }
                updateThumbnailPreview(viewModel.getThumbnailUrl());
                selectCategoryInSpinner(viewModel.getCategoryId());
            }
        });
    }

    private void setupCategorySpinner() {
        List<String> categoryNames = new ArrayList<>();
        categoryNames.add("-- Chọn chuyên mục --");
        for (CategoryResource cat : categoryList) {
            categoryNames.add(cat.name);
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, categoryNames);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.composeCategorySpinner.setAdapter(adapter);

        binding.composeCategorySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position > 0 && position - 1 < categoryList.size()) {
                    viewModel.setCategoryId(categoryList.get(position - 1).id);
                } else {
                    viewModel.setCategoryId(-1);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        selectCategoryInSpinner(viewModel.getCategoryId());
    }

    private void selectCategoryInSpinner(int categoryId) {
        if (categoryId <= 0 || categoryList.isEmpty()) return;
        for (int i = 0; i < categoryList.size(); i++) {
            if (categoryList.get(i).id == categoryId) {
                binding.composeCategorySpinner.setSelection(i + 1);
                break;
            }
        }
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

            Toast.makeText(this, "Đang tải ảnh lên...", Toast.LENGTH_SHORT).show();

            viewModel.uploadImage(cacheFile).observe(this, result -> {
                if (result != null && result.isSuccess() && result.data != null) {
                    String location = result.data;
                    if (isInlineImagePick) {
                        insertImageToEditor(location);
                    } else {
                        viewModel.setThumbnailUrl(location);
                        updateThumbnailPreview(location);
                    }
                    Toast.makeText(this, "Tải ảnh thành công", Toast.LENGTH_SHORT).show();
                } else if (result != null && result.isError()) {
                    Toast.makeText(this, result.error != null ? result.error.getMessage() : "Tải ảnh thất bại", Toast.LENGTH_SHORT).show();
                }
            });
        } catch (Exception e) {
            Toast.makeText(this, "Lỗi đọc file ảnh: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void updateThumbnailPreview(@Nullable String url) {
        if (url != null && !url.trim().isEmpty()) {
            binding.composeThumbnailPreview.setVisibility(View.VISIBLE);
            Glide.with(this).load(url).centerCrop().into(binding.composeThumbnailPreview);
        } else {
            binding.composeThumbnailPreview.setVisibility(View.GONE);
        }
    }

    private void setEditorContent(@Nullable String html) {
        if (editorWebViewPool != null && editorHandle != null) {
            editorWebViewPool.setHtml(editorHandle, html);
        }
    }

    private void insertImageToEditor(@NonNull String imageUrl) {
        if (editorWebViewPool != null && editorHandle != null) {
            editorWebViewPool.insertImage(editorHandle, imageUrl);
        }
    }

    private void handleSubmitPost() {
        if (editorWebViewPool == null || editorHandle == null) {
            return;
        }
        editorWebViewPool.evaluateHtml(editorHandle, htmlValue -> {
            if (isFinishing() || isDestroyed()) {
                return;
            }
            String html = "";
            if (htmlValue != null) {
                try {
                    html = new Gson().fromJson(htmlValue, String.class);
                } catch (Exception e) {
                    html = htmlValue.replaceAll("^\"|\"$", "").replace("\\\"", "\"");
                }
            }
            if (html == null) html = "";
            viewModel.setContentHtml(html);

            if (viewModel.getTitle().trim().isEmpty()) {
                Toast.makeText(ComposeActivity.this, "Vui lòng nhập tiêu đề bài viết", Toast.LENGTH_SHORT).show();
                return;
            }

            if (viewModel.getCategoryId() <= 0) {
                Toast.makeText(ComposeActivity.this, "Vui lòng chọn chuyên mục", Toast.LENGTH_SHORT).show();
                return;
            }

            if (html.trim().isEmpty()) {
                Toast.makeText(ComposeActivity.this, "Vui lòng nhập nội dung bài viết", Toast.LENGTH_SHORT).show();
                return;
            }

            viewModel.submitPost().observe(ComposeActivity.this, result -> {
                if (result != null && result.isSuccess() && result.data != null) {
                    viewModel.clearDraft(ComposeActivity.this);
                    Toast.makeText(ComposeActivity.this, viewModel.getPostId() > 0 ? "Đã cập nhật bài viết" : "Đã đăng bài viết mới", Toast.LENGTH_SHORT).show();
                    Navigator.navigateToPostDetail(ComposeActivity.this, result.data.id);
                    finish();
                } else if (result != null && result.isError()) {
                    Toast.makeText(ComposeActivity.this, result.error != null ? result.error.getMessage() : "Lỗi đăng bài", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void handleDeletePost() {
        ConfirmDialog confirmDialog = new ConfirmDialog(
                this,
                "Xóa bài viết",
                "Bạn có chắc chắn muốn xóa bài viết này? Hành động này không thể hoàn tác.",
                () -> {
                    viewModel.deletePost().observe(this, result -> {
                        if (result != null && result.isSuccess()) {
                            Toast.makeText(this, "Đã xóa bài viết", Toast.LENGTH_SHORT).show();
                            finish();
                        } else if (result != null && result.isError()) {
                            Toast.makeText(this, result.error != null ? result.error.getMessage() : "Xóa bài viết thất bại", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
        );
        confirmDialog.show();
    }

    private void handleExitPrompt() {
        if (!viewModel.getTitle().trim().isEmpty() || !viewModel.getContentHtml().trim().isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("Thoát màn hình soạn bài")
                    .setMessage("Bạn có muốn lưu bài viết hiện tại vào bản nháp không?")
                    .setPositiveButton("Lưu nháp", (dialog, which) -> {
                        viewModel.saveDraft(this);
                        Toast.makeText(this, "Đã lưu bản nháp", Toast.LENGTH_SHORT).show();
                        finish();
                    })
                    .setNegativeButton("Không lưu", (dialog, which) -> finish())
                    .setNeutralButton("Hủy", null)
                    .show();
        } else {
            finish();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (editorHandle != null && editorHandle.getWebView() != null) {
            editorHandle.getWebView().onResume();
        }
    }

    @Override
    protected void onPause() {
        if (editorHandle != null && editorHandle.getWebView() != null) {
            editorHandle.getWebView().onPause();
        }
        super.onPause();
    }

    @Override
    public void onBackPressed() {
        handleExitPrompt();
    }

    @Override
    protected void onDestroy() {
        isEditorLoaded = false;
        if (binding != null) {
            binding.composeEditorContainer.removeAllViews();
        }
        if (editorWebViewPool != null && editorHandle != null) {
            editorWebViewPool.release(editorHandle);
            editorHandle = null;
        }
        super.onDestroy();
    }
}
