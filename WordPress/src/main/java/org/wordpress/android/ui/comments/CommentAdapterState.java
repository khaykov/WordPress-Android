package org.wordpress.android.ui.comments;

import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;

import org.apache.commons.lang.ArrayUtils;

import java.util.Arrays;
import java.util.HashSet;

/**
 * Used to restore state of {@link CommentAdapter}
 */
public class CommentAdapterState implements Parcelable {
    public static final String TAG = "comments_adapter_state";

    private long[] mTrashedCommentId;
    private long[] mSelectedComments;
    private long[] mModeratedCommentsId;

    public CommentAdapterState(@NonNull HashSet<Long> trashedCommentId,
                               @NonNull HashSet<Long> selectedComments,
                               @NonNull HashSet<Long> moderatedCommentsId) {

        // I had tons of problems with passing HashSet as serializable
        // so instead they are converting  to primitive array's before going into Parcel
        mTrashedCommentId = ArrayUtils.toPrimitive(trashedCommentId.toArray(new Long[trashedCommentId.size()]));
        mSelectedComments = ArrayUtils.toPrimitive(selectedComments.toArray(new Long[selectedComments.size()]));
        mModeratedCommentsId = ArrayUtils.toPrimitive(moderatedCommentsId.toArray(new Long[moderatedCommentsId.size()]));
    }

    public HashSet<Long> getTrashedCommentId() {
        return new HashSet<>(Arrays.asList(ArrayUtils.toObject(mTrashedCommentId)));
    }

    public HashSet<Long> getSelectedComments() {
        return new HashSet<>(Arrays.asList(ArrayUtils.toObject(mSelectedComments)));
    }

    public HashSet<Long> getModeratedCommentsId() {
        return new HashSet<>(Arrays.asList(ArrayUtils.toObject(mModeratedCommentsId)));
    }

    public boolean hasSelectedComments() {
        return mSelectedComments != null && mSelectedComments.length > 0;
    }

    public boolean hasModeratingComments() {
        return mModeratedCommentsId != null && mModeratedCommentsId.length > 0;
    }

    public boolean hasTrashedComments() {
        return mTrashedCommentId != null && mTrashedCommentId.length > 0;
    }

    @SuppressWarnings("unchecked")
    protected CommentAdapterState(Parcel in) {
        mTrashedCommentId = in.createLongArray();
        mSelectedComments = in.createLongArray();
        mModeratedCommentsId = in.createLongArray();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLongArray(mTrashedCommentId);
        dest.writeLongArray(mSelectedComments);
        dest.writeLongArray(mModeratedCommentsId);
    }

    @SuppressWarnings("unused")
    public static final Parcelable.Creator<CommentAdapterState> CREATOR = new Parcelable.Creator<CommentAdapterState>() {
        @Override
        public CommentAdapterState createFromParcel(Parcel in) {
            return new CommentAdapterState(in);
        }

        @Override
        public CommentAdapterState[] newArray(int size) {
            return new CommentAdapterState[size];
        }
    };
}