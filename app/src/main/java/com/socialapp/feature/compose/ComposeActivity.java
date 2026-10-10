package com.socialapp.feature.compose;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
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
import com.socialapp.core.navigation.Navigator;
import com.socialapp.core.network.dto.CategoryResource;
import com.socialapp.core.network.dto.PostResource;
import com.socialapp.core.ui.ConfirmDialog;
import com.socialapp.databinding.ActivityComposeBinding;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

import java.util.ArrayList;
import java.util.List;

public class ComposeActivity extends AppCompatActivity {

    public static final String EXTRA_POST_ID = "EXTRA_POST_ID";
    public static final String EXTRA_PREFILL_TITLE = "title";
    public static final String EXTRA_PREFILL_IMAGE = "imageUrl";
    public static final String EXTRA_PREFILL_BODY = "body";

    private ActivityComposeBinding binding;
    private ComposeViewModel viewModel;

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

        ComposeRepository repository = new ComposeRepository(MyApplication.getAppContainer(this).getSocialAppApiService());
        viewModel = new ViewModelProvider(this, new ComposeViewModelFactory(repository)).get(ComposeViewModel.class);

        int postId = getIntent().getIntExtra(EXTRA_POST_ID, -1);
        viewModel.setPostId(postId);

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

            // Check prefill params from M9 or load local draft
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
        WebSettings webSettings = binding.composeEditorWebView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setAllowFileAccess(true);
        webSettings.setDomStorageEnabled(true);

        WebAppInterface webAppInterface = new WebAppInterface(
                () -> {
                    isInlineImagePick = true;
                    imagePickerLauncher.launch("image/*");
                },
                html -> viewModel.setContentHtml(html)
        );

        binding.composeEditorWebView.addJavascriptInterface(webAppInterface, "AndroidBridge");
        binding.composeEditorWebView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                isEditorLoaded = true;
                setEditorContent(viewModel.getContentHtml());
            }
        });

        binding.composeEditorWebView.loadUrl("file:///android_asset/editor/editor.html");
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

    private void setEditorContent(String html) {
        if (html == null) html = "";
        String escapedHtml = html.replace("'", "\\'").replace("\n", "\\n").replace("\r", "");
        binding.composeEditorWebView.evaluateJavascript("javascript:setHtml('" + escapedHtml + "');", null);
    }

    private void insertImageToEditor(String imageUrl) {
        binding.composeEditorWebView.evaluateJavascript("javascript:insertImage('" + imageUrl + "');", null);
    }

    private void handleSubmitPost() {
        binding.composeEditorWebView.evaluateJavascript("javascript:getHtml();", htmlValue -> {
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
    public void onBackPressed() {
        handleExitPrompt();
    }
}
