package com.socialapp.feature.post;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.MutableLiveData;

import com.socialapp.core.mock.FakeApi;
import com.socialapp.core.network.ApiResult;
import com.socialapp.core.network.dto.PostResource;
import com.socialapp.core.network.dto.VoteResponse;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class PostDetailViewModelTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private PostDetailViewModel viewModel;
    private PostResource samplePost;

    @Before
    public void setUp() {
        samplePost = FakeApi.getSamplePosts().get(0);
        samplePost.voteScore = 10;
        samplePost.userVote = 0;

        PostDetailRepository mockRepository = new PostDetailRepository(null) {
            @Override
            public MutableLiveData<ApiResult<PostResource>> getPostDetail(int postId) {
                MutableLiveData<ApiResult<PostResource>> liveData = new MutableLiveData<>();
                liveData.setValue(ApiResult.success(samplePost));
                return liveData;
            }

            @Override
            public MutableLiveData<ApiResult<VoteResponse>> upvotePost(int postId) {
                MutableLiveData<ApiResult<VoteResponse>> liveData = new MutableLiveData<>();
                VoteResponse response = new VoteResponse();
                response.voteScore = 11;
                response.userVote = 1;
                liveData.setValue(ApiResult.success(response));
                return liveData;
            }

            @Override
            public MutableLiveData<ApiResult<VoteResponse>> downvotePost(int postId) {
                MutableLiveData<ApiResult<VoteResponse>> liveData = new MutableLiveData<>();
                VoteResponse response = new VoteResponse();
                response.voteScore = 9;
                response.userVote = -1;
                liveData.setValue(ApiResult.success(response));
                return liveData;
            }
        };

        viewModel = new PostDetailViewModel(mockRepository);
    }

    @Test
    public void testLoadPostDetail() {
        viewModel.loadPostDetail(1);
        assertNotNull(viewModel.getPostDetailLiveData().getValue());
        assertTrue(viewModel.getPostDetailLiveData().getValue().isSuccess());
        assertEquals(samplePost.title, viewModel.getCurrentPost().title);
    }

    @Test
    public void testUpvoteOptimisticAndUpdate() {
        viewModel.loadPostDetail(1);
        viewModel.upvote();

        assertEquals(1, viewModel.getCurrentPost().userVote);
        assertEquals(11, viewModel.getCurrentPost().voteScore);
    }

    @Test
    public void testDownvoteOptimisticAndUpdate() {
        viewModel.loadPostDetail(1);
        viewModel.downvote();

        assertEquals(-1, viewModel.getCurrentPost().userVote);
        assertEquals(9, viewModel.getCurrentPost().voteScore);
    }
}
