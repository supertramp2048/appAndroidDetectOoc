package com.socialapp.feature.comments;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;
import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.MutableLiveData;

import com.socialapp.core.network.ApiResult;
import com.socialapp.core.network.dto.CommentResource;
import com.socialapp.core.network.dto.UserResource;
import com.socialapp.core.pagination.PageLoader;

import java.util.Collections;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class CommentsViewModelTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private CommentsViewModel viewModel;
    private CommentResource sampleComment;

    @Before
    public void setUp() {
        sampleComment = new CommentResource();
        sampleComment.id = 101;
        sampleComment.content = "Bình luận thử nghiệm";
        sampleComment.author = new UserResource();
        sampleComment.author.id = 1;
        sampleComment.author.name = "Test User";

        CommentsRepository mockRepository = new CommentsRepository(null) {
            @Override
            public PageLoader.PageFetcher<CommentResource> createRootCommentsPageFetcher(int postId) {
                return (page, callback) -> callback.onSuccess(new PageLoader.PageResult<>(Collections.emptyList(), 1, false));
            }

            @Override
            public MutableLiveData<ApiResult<CommentResource>> createComment(int postId, Integer parentId, @NonNull String content) {
                MutableLiveData<ApiResult<CommentResource>> liveData = new MutableLiveData<>();
                CommentResource newComment = new CommentResource();
                newComment.id = 102;
                newComment.content = content;
                newComment.parentId = parentId;
                liveData.setValue(ApiResult.success(newComment));
                return liveData;
            }

            @Override
            public MutableLiveData<ApiResult<Void>> deleteComment(int commentId) {
                MutableLiveData<ApiResult<Void>> liveData = new MutableLiveData<>();
                liveData.setValue(ApiResult.success(null));
                return liveData;
            }
        };

        viewModel = new CommentsViewModel(mockRepository);
    }

    @Test
    public void testReplyingToState() {
        assertNull(viewModel.getReplyingToLiveData().getValue());

        viewModel.setReplyingTo(sampleComment);
        assertNotNull(viewModel.getReplyingToLiveData().getValue());
        assertEquals(sampleComment.id, viewModel.getReplyingToLiveData().getValue().id);

        viewModel.clearReplyingTo();
        assertNull(viewModel.getReplyingToLiveData().getValue());
    }

    @Test
    public void testCreateComment() {
        viewModel.init(10);
        viewModel.setReplyingTo(sampleComment);

        ApiResult<CommentResource> result = viewModel.createComment("Nội dung mới").getValue();
        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertNotNull(result.data);
        assertEquals(102, result.data.id);
        assertEquals((Integer) 101, result.data.parentId);
    }
}
