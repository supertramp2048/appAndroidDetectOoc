package com.socialapp.feature.compose;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.MutableLiveData;

import com.socialapp.core.network.ApiResult;
import com.socialapp.core.network.dto.PostResource;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class ComposeViewModelTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private ComposeViewModel viewModel;
    private PostResource samplePost;

    @Before
    public void setUp() {
        samplePost = new PostResource();
        samplePost.id = 55;
        samplePost.title = "Bài viết mới tạo";
        samplePost.contentHtml = "<p>Nội dung bài viết mới</p>";

        ComposeRepository mockRepository = new ComposeRepository(null) {
            @Override
            public MutableLiveData<ApiResult<PostResource>> createPost(@NonNull String title,
                                                                        @NonNull String contentHtml,
                                                                        int categoryId,
                                                                        @Nullable String thumbnailUrl) {
                MutableLiveData<ApiResult<PostResource>> liveData = new MutableLiveData<>();
                liveData.setValue(ApiResult.success(samplePost));
                return liveData;
            }

            @Override
            public MutableLiveData<ApiResult<Void>> deletePost(int postId) {
                MutableLiveData<ApiResult<Void>> liveData = new MutableLiveData<>();
                liveData.setValue(ApiResult.success(null));
                return liveData;
            }
        };

        viewModel = new ComposeViewModel(mockRepository);
    }

    @Test
    public void testSetAndGetTitleAndContent() {
        viewModel.setTitle("Tiêu đề thử nghiệm");
        viewModel.setContentHtml("<p>Nội dung</p>");

        assertEquals("Tiêu đề thử nghiệm", viewModel.getTitle());
        assertEquals("<p>Nội dung</p>", viewModel.getContentHtml());
    }

    @Test
    public void testSubmitCreatePost() {
        viewModel.setTitle("Bài viết mới tạo");
        viewModel.setContentHtml("<p>Nội dung bài viết mới</p>");
        viewModel.setCategoryId(1);

        ApiResult<PostResource> result = viewModel.submitPost().getValue();
        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertNotNull(result.data);
        assertEquals(55, result.data.id);
        assertEquals("Bài viết mới tạo", result.data.title);
    }
}
