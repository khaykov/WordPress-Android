package org.wordpress.android.ui.comments;

import org.wordpress.android.models.Comment;
import org.wordpress.android.models.CommentList;
import org.wordpress.android.models.CommentStatus;

public class CommentEvents {

    public static class BatchCommentsModeratedEvent {
        private CommentList mComments;
        private boolean mIsDeleted;

        public BatchCommentsModeratedEvent(CommentList comments, boolean isDeleted) {
            mComments = comments;
            mIsDeleted = isDeleted;
        }

        public CommentList getComments() {
            return mComments;
        }

        public boolean isDeleted() {
            return mIsDeleted;
        }
    }

    public static class CommentChangedEvent {

        private CommentActions.ChangedFrom mChangedFrom;
        private CommentActions.ChangeType mChangeType;

        public CommentChangedEvent(CommentActions.ChangedFrom changedFrom, CommentActions.ChangeType changeType) {
            mChangedFrom = changedFrom;
            mChangeType = changeType;
        }

        public CommentActions.ChangedFrom getChangedFrom() {
            return mChangedFrom;
        }

        public CommentActions.ChangeType getChangeType() {
            return mChangeType;
        }
    }

    public static class CommentModeratedEvent {

        private int mAccountId;
        private Comment mComment;
        private CommentStatus mNewStatus;

        public CommentModeratedEvent(int accountId, Comment comment, CommentStatus newStatus) {
            mAccountId = accountId;
            mComment = comment;
            mNewStatus = newStatus;
        }

        public int getAccountId() {
            return mAccountId;
        }

        public Comment getComment() {
            return mComment;
        }

        public CommentStatus getNewStatus() {
            return mNewStatus;
        }
    }
}
