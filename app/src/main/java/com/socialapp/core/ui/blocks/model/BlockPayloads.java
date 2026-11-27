package com.socialapp.core.ui.blocks.model;

import androidx.annotation.Nullable;

import java.util.Objects;

/**
 * Fine-grained immutable payloads returned by DiffUtil.getChangePayload()
 * so RecyclerView can perform partial re-recording / rebinding without image flicker.
 */
public final class BlockPayloads {

    private BlockPayloads() {
    }

    /**
     * Partial payload when only vote metrics or comment count change on a FeedPostBlock.
     * Text layout height and images remain unchanged.
     */
    public static final class FeedMetricsPayload {
        public final int voteScore;
        public final int userVote;
        public final int commentsCount;

        public FeedMetricsPayload(int voteScore, int userVote, int commentsCount) {
            this.voteScore = voteScore;
            this.userVote = userVote;
            this.commentsCount = commentsCount;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof FeedMetricsPayload)) return false;
            FeedMetricsPayload that = (FeedMetricsPayload) o;
            return voteScore == that.voteScore
                    && userVote == that.userVote
                    && commentsCount == that.commentsCount;
        }

        @Override
        public int hashCode() {
            return Objects.hash(voteScore, userVote, commentsCount);
        }
    }

    /**
     * Partial payload when only the replies expand/collapse state or count changes on a CommentBlock.
     */
    public static final class CommentRepliesPayload {
        public final int repliesCount;
        public final boolean repliesExpanded;

        public CommentRepliesPayload(int repliesCount, boolean repliesExpanded) {
            this.repliesCount = repliesCount;
            this.repliesExpanded = repliesExpanded;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof CommentRepliesPayload)) return false;
            CommentRepliesPayload that = (CommentRepliesPayload) o;
            return repliesCount == that.repliesCount && repliesExpanded == that.repliesExpanded;
        }

        @Override
        public int hashCode() {
            return Objects.hash(repliesCount, repliesExpanded);
        }
    }

    /**
     * Partial payload when a comment's text, edited flag, status, or depth changed.
     */
    public static final class CommentContentPayload {
        @Nullable
        public final String content;
        @Nullable
        public final String status;
        public final boolean edited;
        public final int depth;

        public CommentContentPayload(@Nullable String content, @Nullable String status, boolean edited, int depth) {
            this.content = content;
            this.status = status;
            this.edited = edited;
            this.depth = depth;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof CommentContentPayload)) return false;
            CommentContentPayload that = (CommentContentPayload) o;
            return edited == that.edited
                    && depth == that.depth
                    && Objects.equals(content, that.content)
                    && Objects.equals(status, that.status);
        }

        @Override
        public int hashCode() {
            return Objects.hash(content, status, edited, depth);
        }
    }

    /**
     * Partial payload when only follow state changes on DetailHeaderBlock.
     */
    public static final class DetailFollowPayload {
        public final boolean followingAuthor;

        public DetailFollowPayload(boolean followingAuthor) {
            this.followingAuthor = followingAuthor;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof DetailFollowPayload)) return false;
            DetailFollowPayload that = (DetailFollowPayload) o;
            return followingAuthor == that.followingAuthor;
        }

        @Override
        public int hashCode() {
            return Objects.hash(followingAuthor);
        }
    }

    /**
     * Partial payload when vote score, user vote, or comments count changes on DetailActionBlock.
     */
    public static final class DetailActionPayload {
        public final int voteScore;
        public final int userVote;
        public final int commentsCount;

        public DetailActionPayload(int voteScore, int userVote, int commentsCount) {
            this.voteScore = voteScore;
            this.userVote = userVote;
            this.commentsCount = commentsCount;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof DetailActionPayload)) return false;
            DetailActionPayload that = (DetailActionPayload) o;
            return voteScore == that.voteScore
                    && userVote == that.userVote
                    && commentsCount == that.commentsCount;
        }

        @Override
        public int hashCode() {
            return Objects.hash(voteScore, userVote, commentsCount);
        }
    }
}
