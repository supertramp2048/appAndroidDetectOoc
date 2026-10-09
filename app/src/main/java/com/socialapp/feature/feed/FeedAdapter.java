package com.socialapp.feature.feed;

import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.socialapp.core.network.dto.PostResource;
import com.socialapp.core.ui.AdSlotView;
import com.socialapp.core.ui.PostCardView;

import java.util.ArrayList;
import java.util.List;

public class FeedAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_POST = 0;
    private static final int VIEW_TYPE_AD = 1;
    private static final int AD_FREQUENCY = 5; // Insert ad after every 5 posts

    public interface OnPostClickListener {
        void onPostClick(@NonNull PostResource post);
    }

    private final List<PostResource> rawPosts = new ArrayList<>();
    private final OnPostClickListener listener;

    public FeedAdapter(@Nullable OnPostClickListener listener) {
        this.listener = listener;
    }

    public void setPosts(@Nullable List<PostResource> posts) {
        rawPosts.clear();
        if (posts != null) {
            rawPosts.addAll(posts);
        }
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        if (isAdPosition(position)) {
            return VIEW_TYPE_AD;
        }
        return VIEW_TYPE_POST;
    }

    private boolean isAdPosition(int position) {
        if (position == 0) return false;
        return (position + 1) % (AD_FREQUENCY + 1) == 0;
    }

    private int getPostIndex(int position) {
        return position - (position / (AD_FREQUENCY + 1));
    }

    @Override
    public int getItemCount() {
        if (rawPosts.isEmpty()) return 0;
        int adsCount = rawPosts.size() / AD_FREQUENCY;
        return rawPosts.size() + adsCount;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_AD) {
            AdSlotView adSlotView = new AdSlotView(parent.getContext());
            adSlotView.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            return new AdViewHolder(adSlotView);
        } else {
            PostCardView postCardView = new PostCardView(parent.getContext());
            postCardView.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            return new PostViewHolder(postCardView);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (getItemViewType(position) == VIEW_TYPE_POST) {
            int postIndex = getPostIndex(position);
            if (postIndex >= 0 && postIndex < rawPosts.size()) {
                PostResource post = rawPosts.get(postIndex);
                PostCardView cardView = (PostCardView) holder.itemView;
                cardView.bind(post);
                cardView.setCardClickListener(v -> {
                    if (listener != null) {
                        listener.onPostClick(post);
                    }
                });
            }
        } else if (getItemViewType(position) == VIEW_TYPE_AD) {
            AdSlotView adSlotView = (AdSlotView) holder.itemView;
            adSlotView.setAdvertisement(null);
        }
    }

    public static class PostViewHolder extends RecyclerView.ViewHolder {
        public PostViewHolder(@NonNull PostCardView itemView) {
            super(itemView);
        }
    }

    public static class AdViewHolder extends RecyclerView.ViewHolder {
        public AdViewHolder(@NonNull AdSlotView itemView) {
            super(itemView);
        }
    }
}
