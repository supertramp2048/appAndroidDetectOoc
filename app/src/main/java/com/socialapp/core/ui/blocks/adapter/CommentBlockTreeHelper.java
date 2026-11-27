package com.socialapp.core.ui.blocks.adapter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.socialapp.core.network.dto.CommentResource;
import com.socialapp.core.ui.blocks.model.CommentBlock;
import com.socialapp.core.ui.blocks.model.ContentBlock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure-Java immutable list builder/transformer for flattened comment threads (depth 0..2).
 * Produces brand new immutable lists for AsyncListDiffer so DiffUtil detects every delta cleanly.
 */
public final class CommentBlockTreeHelper {

    private CommentBlockTreeHelper() {
    }

    /**
     * Rebuilds the flattened comment list from updated root comments.
     * If a root comment already had expanded child replies in currentBlocks, preserves those
     * expanded subtrees so pagination loadNext() does not collapse open threads.
     */
    @NonNull
    public static List<ContentBlock> fromRootComments(
            @Nullable List<CommentResource> rootComments,
            @Nullable List<ContentBlock> currentBlocks) {
        if (rootComments == null || rootComments.isEmpty()) {
            return Collections.emptyList();
        }

        // Index currently expanded children under each root commentId
        Map<Integer, List<CommentBlock>> preservedDescendants = new HashMap<>();
        Map<Integer, Boolean> preservedExpandedState = new HashMap<>();
        if (currentBlocks != null && !currentBlocks.isEmpty()) {
            int activeRootId = -1;
            for (ContentBlock b : currentBlocks) {
                if (!(b instanceof CommentBlock)) continue;
                CommentBlock cb = (CommentBlock) b;
                if (cb.getDepth() == 0) {
                    activeRootId = cb.getCommentId();
                    if (cb.isRepliesExpanded()) {
                        preservedExpandedState.put(activeRootId, true);
                        preservedDescendants.put(activeRootId, new ArrayList<>());
                    }
                } else if (activeRootId != -1 && preservedDescendants.containsKey(activeRootId)) {
                    preservedDescendants.get(activeRootId).add(cb);
                }
            }
        }

        List<ContentBlock> result = new ArrayList<>(rootComments.size());
        for (CommentResource root : rootComments) {
            if (root == null) continue;
            boolean expanded = Boolean.TRUE.equals(preservedExpandedState.get(root.id));
            List<CommentBlock> children = preservedDescendants.get(root.id);
            if (expanded && children != null && !children.isEmpty()) {
                result.add(CommentBlock.fromCommentResource(root, 0, true));
                result.addAll(children);
            } else {
                result.add(CommentBlock.fromCommentResource(root, 0, false));
            }
        }
        return Collections.unmodifiableList(result);
    }

    @NonNull
    public static List<ContentBlock> insertRootAtTop(
            @NonNull List<ContentBlock> current,
            @NonNull CommentResource newComment) {
        List<ContentBlock> next = new ArrayList<>(current.size() + 1);
        next.add(CommentBlock.fromCommentResource(newComment, 0, false));
        next.addAll(current);
        return Collections.unmodifiableList(next);
    }

    @NonNull
    public static List<ContentBlock> insertReplyUnderParent(
            @NonNull List<ContentBlock> current,
            int parentId,
            @NonNull CommentResource reply) {
        int parentIdx = findCommentIndex(current, parentId);
        if (parentIdx < 0) {
            return insertRootAtTop(current, reply);
        }

        CommentBlock parent = (CommentBlock) current.get(parentIdx);
        int targetDepth = Math.min(parent.getDepth() + 1, CommentBlock.MAX_DEPTH);
        int insertPos = parentIdx + 1;
        while (insertPos < current.size()) {
            ContentBlock b = current.get(insertPos);
            if (b instanceof CommentBlock && ((CommentBlock) b).getDepth() > parent.getDepth()) {
                insertPos++;
            } else {
                break;
            }
        }

        List<ContentBlock> next = new ArrayList<>(current.size() + 1);
        for (int i = 0; i < current.size(); i++) {
            if (i == insertPos) {
                next.add(CommentBlock.fromCommentResource(reply, targetDepth, false));
            }
            if (i == parentIdx) {
                next.add(parent.withRepliesState(true, parent.getRepliesCount() + 1));
            } else {
                next.add(current.get(i));
            }
        }
        if (insertPos >= current.size()) {
            next.add(CommentBlock.fromCommentResource(reply, targetDepth, false));
        }
        return Collections.unmodifiableList(next);
    }

    @NonNull
    public static List<ContentBlock> insertReplies(
            @NonNull List<ContentBlock> current,
            int parentCommentId,
            @NonNull List<CommentResource> replies) {
        int parentIdx = findCommentIndex(current, parentCommentId);
        if (parentIdx < 0 || replies.isEmpty()) {
            return current;
        }

        CommentBlock parent = (CommentBlock) current.get(parentIdx);
        int childDepth = Math.min(parent.getDepth() + 1, CommentBlock.MAX_DEPTH);

        // Skip/replace any stale children directly under parent to avoid duplicates
        int afterChildrenIdx = parentIdx + 1;
        while (afterChildrenIdx < current.size()) {
            ContentBlock b = current.get(afterChildrenIdx);
            if (b instanceof CommentBlock && ((CommentBlock) b).getDepth() > parent.getDepth()) {
                afterChildrenIdx++;
            } else {
                break;
            }
        }

        List<ContentBlock> next = new ArrayList<>(current.size() + replies.size());
        for (int i = 0; i < parentIdx; i++) {
            next.add(current.get(i));
        }
        next.add(parent.withRepliesState(true, Math.max(parent.getRepliesCount(), replies.size())));
        for (CommentResource reply : replies) {
            if (reply != null) {
                next.add(CommentBlock.fromCommentResource(reply, childDepth, false));
            }
        }
        for (int i = afterChildrenIdx; i < current.size(); i++) {
            next.add(current.get(i));
        }
        return Collections.unmodifiableList(next);
    }

    @NonNull
    public static List<ContentBlock> collapseReplies(
            @NonNull List<ContentBlock> current,
            int parentCommentId) {
        int parentIdx = findCommentIndex(current, parentCommentId);
        if (parentIdx < 0) {
            return current;
        }

        CommentBlock parent = (CommentBlock) current.get(parentIdx);
        int afterChildrenIdx = parentIdx + 1;
        while (afterChildrenIdx < current.size()) {
            ContentBlock b = current.get(afterChildrenIdx);
            if (b instanceof CommentBlock && ((CommentBlock) b).getDepth() > parent.getDepth()) {
                afterChildrenIdx++;
            } else {
                break;
            }
        }

        List<ContentBlock> next = new ArrayList<>(current.size());
        for (int i = 0; i < parentIdx; i++) {
            next.add(current.get(i));
        }
        next.add(parent.withRepliesState(false, parent.getRepliesCount()));
        for (int i = afterChildrenIdx; i < current.size(); i++) {
            next.add(current.get(i));
        }
        return Collections.unmodifiableList(next);
    }

    @NonNull
    public static List<ContentBlock> updateCommentContent(
            @NonNull List<ContentBlock> current,
            int commentId,
            @NonNull String newContent) {
        int idx = findCommentIndex(current, commentId);
        if (idx < 0) {
            return current;
        }
        CommentBlock target = (CommentBlock) current.get(idx);
        String editedStamp = (target.getCreatedAt() != null ? target.getCreatedAt() : "") + "_edited";
        List<ContentBlock> next = new ArrayList<>(current);
        next.set(idx, target.withUpdatedContent(newContent, editedStamp));
        return Collections.unmodifiableList(next);
    }

    @NonNull
    public static List<ContentBlock> removeComment(
            @NonNull List<ContentBlock> current,
            int commentId) {
        int idx = findCommentIndex(current, commentId);
        if (idx < 0) {
            return current;
        }
        CommentBlock target = (CommentBlock) current.get(idx);
        int endIdx = idx + 1;
        while (endIdx < current.size()) {
            ContentBlock b = current.get(endIdx);
            if (b instanceof CommentBlock && ((CommentBlock) b).getDepth() > target.getDepth()) {
                endIdx++;
            } else {
                break;
            }
        }
        List<ContentBlock> next = new ArrayList<>(current.size() - (endIdx - idx));
        for (int i = 0; i < idx; i++) {
            next.add(current.get(i));
        }
        for (int i = endIdx; i < current.size(); i++) {
            next.add(current.get(i));
        }
        return Collections.unmodifiableList(next);
    }

    public static int findCommentIndex(@NonNull List<ContentBlock> blocks, int commentId) {
        for (int i = 0; i < blocks.size(); i++) {
            ContentBlock b = blocks.get(i);
            if (b instanceof CommentBlock && ((CommentBlock) b).getCommentId() == commentId) {
                return i;
            }
        }
        return -1;
    }
}
