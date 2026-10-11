package com.socialapp.feature.comments;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.socialapp.R;
import com.socialapp.core.navigation.Navigator;
import com.socialapp.core.network.dto.CommentResource;
import com.socialapp.core.ui.UserAvatarView;
import com.socialapp.core.util.DateTimeUtils;

import java.util.ArrayList;
import java.util.List;

public class CommentsAdapter extends RecyclerView.Adapter<CommentsAdapter.ViewHolder> {

    public interface OnCommentActionListener {
        void onReplyClick(@NonNull CommentResource comment);
        void onLoadRepliesClick(@NonNull CommentResource comment, int position);
        void onMenuClick(@NonNull CommentResource comment, @NonNull View anchor);
    }

    public static class CommentItemWrapper {
        public final CommentResource comment;
        public final int depth; // 0 = root, 1 = level 1, 2 = level 2+
        public boolean isRepliesExpanded = false;

        public CommentItemWrapper(CommentResource comment, int depth) {
            this.comment = comment;
            this.depth = Math.min(depth, 2);
        }
    }

    private final List<CommentItemWrapper> items = new ArrayList<>();
    private final OnCommentActionListener listener;

    public CommentsAdapter(@Nullable OnCommentActionListener listener) {
        this.listener = listener;
    }

    public void setComments(@Nullable List<CommentResource> rootComments) {
        items.clear();
        if (rootComments != null) {
            for (CommentResource c : rootComments) {
                items.add(new CommentItemWrapper(c, 0));
            }
        }
        notifyDataSetChanged();
    }

    public void insertCommentAtTop(@NonNull CommentResource comment) {
        items.add(0, new CommentItemWrapper(comment, 0));
        notifyItemInserted(0);
    }

    public boolean insertReplyUnderParent(int parentId, @NonNull CommentResource reply) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).comment.id == parentId) {
                CommentItemWrapper parent = items.get(i);
                parent.isRepliesExpanded = true;
                parent.comment.repliesCount += 1;

                int targetDepth = parent.depth + 1;
                int insertPos = i + 1;
                while (insertPos < items.size() && items.get(insertPos).depth > parent.depth) {
                    insertPos++;
                }

                items.add(insertPos, new CommentItemWrapper(reply, targetDepth));
                notifyItemInserted(insertPos);
                notifyItemChanged(i);
                return true;
            }
        }
        insertCommentAtTop(reply);
        return false;
    }

    public void insertReplies(int parentPosition, @NonNull List<CommentResource> replies) {
        if (parentPosition < 0 || parentPosition >= items.size()) return;

        CommentItemWrapper parent = items.get(parentPosition);
        parent.isRepliesExpanded = true;
        int nextDepth = parent.depth + 1;

        int insertIndex = parentPosition + 1;
        for (CommentResource reply : replies) {
            items.add(insertIndex++, new CommentItemWrapper(reply, nextDepth));
        }
        notifyItemRangeInserted(parentPosition + 1, replies.size());
        notifyItemChanged(parentPosition);
    }

    public void collapseReplies(int parentPosition) {
        if (parentPosition < 0 || parentPosition >= items.size()) return;

        CommentItemWrapper parent = items.get(parentPosition);
        parent.isRepliesExpanded = false;

        int removeIndex = parentPosition + 1;
        int removeCount = 0;
        while (removeIndex < items.size() && items.get(removeIndex).depth > parent.depth) {
            items.remove(removeIndex);
            removeCount++;
        }

        if (removeCount > 0) {
            notifyItemRangeRemoved(parentPosition + 1, removeCount);
            notifyItemChanged(parentPosition);
        }
    }

    public void updateCommentContent(int commentId, @NonNull String newContent) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).comment.id == commentId) {
                items.get(i).comment.content = newContent;
                items.get(i).comment.updatedAt = items.get(i).comment.createdAt + "_edited";
                notifyItemChanged(i);
                break;
            }
        }
    }

    public void removeComment(int commentId) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).comment.id == commentId) {
                items.remove(i);
                notifyItemRemoved(i);
                break;
            }
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_comment, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        CommentItemWrapper wrapper = items.get(position);
        CommentResource comment = wrapper.comment;

        // Indentation by depth
        int indentPixels = (int) (wrapper.depth * 24 * holder.itemView.getContext().getResources().getDisplayMetrics().density);
        holder.container.setPadding(indentPixels + (int) (12 * holder.itemView.getContext().getResources().getDisplayMetrics().density),
                holder.container.getPaddingTop(),
                holder.container.getPaddingRight(),
                holder.container.getPaddingBottom());

        // Author
        if (comment.author != null) {
            holder.authorNameText.setText(comment.author.name != null ? comment.author.name : "Thành viên");
            holder.avatarView.loadAvatar(comment.author.getAvatarUrl());

            View.OnClickListener authorClickListener = v -> {
                if (comment.author != null && comment.author.id > 0) {
                    Navigator.navigateToUserProfile(v.getContext(), comment.author.id);
                }
            };
            holder.avatarView.setOnClickListener(authorClickListener);
            holder.authorNameText.setOnClickListener(authorClickListener);
        } else {
            holder.authorNameText.setText("Thành viên");
            holder.avatarView.loadAvatar(null);
            holder.avatarView.setOnClickListener(null);
            holder.authorNameText.setOnClickListener(null);
        }

        // Time & Edited status
        holder.timeText.setText(DateTimeUtils.formatRelativeTime(comment.createdAt));
        if (comment.createdAt != null && comment.updatedAt != null && !comment.createdAt.equals(comment.updatedAt)) {
            holder.editedTag.setVisibility(View.VISIBLE);
        } else {
            holder.editedTag.setVisibility(View.GONE);
        }

        // Content / Status Check
        if (comment.status != null && !"published".equalsIgnoreCase(comment.status.trim())) {
            holder.contentText.setVisibility(View.GONE);
            holder.removedText.setVisibility(View.VISIBLE);
        } else {
            holder.contentText.setVisibility(View.VISIBLE);
            holder.removedText.setVisibility(View.GONE);
            holder.contentText.setText(comment.content != null ? comment.content : "");
        }

        // Reply button
        holder.btnReply.setOnClickListener(v -> {
            if (listener != null) {
                listener.onReplyClick(comment);
            }
        });

        // Load / Collapse replies button
        if (comment.repliesCount > 0) {
            if (!wrapper.isRepliesExpanded) {
                holder.btnLoadReplies.setText("Xem " + comment.repliesCount + " phản hồi");
                holder.btnLoadReplies.setVisibility(View.VISIBLE);
                holder.btnLoadReplies.setOnClickListener(v -> {
                    if (listener != null) {
                        listener.onLoadRepliesClick(comment, holder.getAdapterPosition());
                    }
                });
            } else {
                holder.btnLoadReplies.setText("Ẩn phản hồi");
                holder.btnLoadReplies.setVisibility(View.VISIBLE);
                holder.btnLoadReplies.setOnClickListener(v -> collapseReplies(holder.getAdapterPosition()));
            }
        } else {
            holder.btnLoadReplies.setVisibility(View.GONE);
        }

        // Menu
        holder.btnMenu.setOnClickListener(v -> {
            if (listener != null) {
                listener.onMenuClick(comment, v);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        View container;
        UserAvatarView avatarView;
        TextView authorNameText;
        TextView timeText;
        TextView editedTag;
        TextView contentText;
        TextView removedText;
        TextView btnReply;
        TextView btnLoadReplies;
        ImageButton btnMenu;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            container = itemView.findViewById(R.id.comment_item_container);
            avatarView = itemView.findViewById(R.id.comment_author_avatar);
            authorNameText = itemView.findViewById(R.id.comment_author_name);
            timeText = itemView.findViewById(R.id.comment_time_text);
            editedTag = itemView.findViewById(R.id.comment_edited_tag);
            contentText = itemView.findViewById(R.id.comment_content_text);
            removedText = itemView.findViewById(R.id.comment_removed_text);
            btnReply = itemView.findViewById(R.id.btn_reply_comment);
            btnLoadReplies = itemView.findViewById(R.id.btn_load_replies);
            btnMenu = itemView.findViewById(R.id.btn_comment_menu);
        }
    }
}
