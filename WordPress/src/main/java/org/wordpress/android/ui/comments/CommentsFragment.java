package org.wordpress.android.ui.comments;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.wordpress.android.R;
import org.wordpress.android.WordPress;
import org.wordpress.android.models.Comment;
import org.wordpress.android.models.CommentList;
import org.wordpress.android.models.CommentStatus;
import org.wordpress.android.ui.ActivityLauncher;
import org.wordpress.android.util.DualPaneHelper;
import org.wordpress.android.util.ToastUtils;

import de.greenrobot.event.EventBus;

public class CommentsFragment extends Fragment implements CommentsListFragment.OnCommentSelectedListener {
    private final CommentList mTrashedComments = new CommentList();

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        CommentsListFragment fragment = (CommentsListFragment) Fragment.instantiate(getActivity(), CommentsListFragment
                .class.getName(), null);
        getChildFragmentManager().beginTransaction().replace(R.id.fragment_container, fragment, getString(
                R.string.fragment_tag_comment_list))
                .commit();
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {

        return inflater.inflate(R.layout.comments_fragment, container, false);
    }

    private CommentsListFragment getListFragment() {
        Fragment fragment = getChildFragmentManager().findFragmentByTag(getString(
                R.string.fragment_tag_comment_list));
        if (fragment == null) {
            return null;
        }
        return (CommentsListFragment) fragment;
    }

    private boolean hasListFragment() {
        return (getListFragment() != null);
    }

    /*
     * called from comment list when user taps a comment
     */
    @Override
    public void onCommentSelected(long commentId) {
        ActivityLauncher.viewCommentDetails(getActivity(), WordPress.getCurrentLocalTableBlogId(), commentId,
                DualPaneHelper.isInDualPaneMode(this));
    }

    /*
     * reload the comment list from existing data
     */
    private void reloadCommentList() {
        CommentsListFragment listFragment = getListFragment();
        if (listFragment != null)
            listFragment.loadComments();
    }

    /*
     * tell the comment list to get recent comments from server
     */
    void updateCommentList() {
        CommentsListFragment listFragment = getListFragment();
        if (listFragment != null) {
            listFragment.setRefreshing(true);
            listFragment.updateComments(false);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        EventBus.getDefault().registerSticky(this);
    }

    @Override
    public void onStop() {
        EventBus.getDefault().unregister(this);
        super.onStop();
    }

    public void onEventMainThread(CommentDetailFragment.CommentChangedEvent event) {
        EventBus.getDefault().removeStickyEvent(CommentDetailFragment.CommentChangedEvent.class);
        if (event.getChangedFrom() == CommentActions.ChangedFrom.COMMENT_DETAIL) {
            switch (event.getChangeType()) {
                case EDITED:
                    reloadCommentList();
                    break;
                case REPLIED:
                    updateCommentList();
                    break;
            }
        }
    }

    public void onEventMainThread(CommentDetailFragment.CommentModeratedEvent event) {
        if (getListFragment() == null || !getListFragment().isAdded() || getView() == null) return;

        final int accountId = event.getAccountId();
        final Comment comment = event.getComment();
        final CommentStatus newStatus = event.getNewStatus();

        EventBus.getDefault().removeStickyEvent(CommentDetailFragment.CommentModeratedEvent.class);

        if (newStatus == CommentStatus.APPROVED || newStatus == CommentStatus.UNAPPROVED) {
            getListFragment().setCommentIsModerating(comment.commentID, true);
            CommentActions.moderateComment(accountId, comment, newStatus,
                    new CommentActions.CommentActionListener() {
                        @Override
                        public void onActionResult(boolean succeeded) {
                            if (!isAdded() || !hasListFragment()) {
                                return;
                            }

                            getListFragment().setCommentIsModerating(comment.commentID, false);

                            if (succeeded) {
                                reloadCommentList();
                            } else {
                                ToastUtils.showToast(getActivity(),
                                        R.string.error_moderate_comment,
                                        ToastUtils.Duration.LONG
                                );
                            }
                        }
                    });
        } else if (newStatus == CommentStatus.SPAM || newStatus == CommentStatus.TRASH) {
            mTrashedComments.add(comment);
            getListFragment().removeComment(comment);
            getListFragment().setCommentIsModerating(comment.commentID, true);

            String message = (newStatus == CommentStatus.TRASH ? getString(R.string.comment_trashed) : getString(R.string
                    .comment_spammed));
            View.OnClickListener undoListener = new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mTrashedComments.remove(comment);
                    getListFragment().setCommentIsModerating(comment.commentID, false);
                    getListFragment().loadComments();
                }
            };

            Snackbar snackbar = Snackbar.make(getView(), message, Snackbar.LENGTH_LONG)
                    .setAction(R.string.undo, undoListener);

            // do the actual moderation once the undo bar has been hidden
            snackbar.setCallback(new Snackbar.Callback() {
                @Override
                public void onDismissed(Snackbar snackbar, int event) {
                    super.onDismissed(snackbar, event);

                    // comment will no longer exist in moderating list if action was undone
                    if (!mTrashedComments.contains(comment)) {
                        return;
                    }
                    mTrashedComments.remove(comment);

                    CommentActions.moderateComment(accountId, comment, newStatus, new CommentActions.CommentActionListener
                            () {
                        @Override
                        public void onActionResult(boolean succeeded) {
                            if (!isAdded() || !hasListFragment()) {
                                return;
                            }
                            getListFragment().setCommentIsModerating(comment.commentID, false);
                            if (!succeeded) {
                                // show comment again upon error
                                getListFragment().loadComments();
                                ToastUtils.showToast(getActivity(),
                                        R.string.error_moderate_comment,
                                        ToastUtils.Duration.LONG
                                );
                            }
                        }
                    });
                }
            });

            snackbar.show();
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        // https://code.google.com/p/android/issues/detail?id=19917
        if (outState.isEmpty()) {
            outState.putBoolean("bug_19917_fix", true);
        }
    }
}
