package org.wordpress.android.ui.comments;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;

import com.simperium.client.BucketObjectMissingException;

import org.wordpress.android.R;
import org.wordpress.android.models.Note;
import org.wordpress.android.ui.ActivityId;
import org.wordpress.android.ui.ActivityLauncher;
import org.wordpress.android.ui.notifications.utils.SimperiumUtils;
import org.wordpress.android.util.AppLog;
import org.wordpress.android.util.ToastUtils;

/**
 * Host for {@link CommentDetailFragment}. Currently only called from  {@link CommentsListFragment}.
 */
public class CommentDetailActivity extends AppCompatActivity {

    public static final String KEY_COMMENT_DETAIL_LOCAL_TABLE_BLOG_ID = "local_table_blog_id";
    public static final String KEY_COMMENT_DETAIL_COMMENT_ID = "comment_detail_comment_id";
    public static final String KEY_COMMENT_DETAIL_NOTE_ID = "comment_detail_note_id";
    public static final String KEY_COMMENT_DETAIL_IS_REMOTE = "comment_detail_is_remote";

    private static final String TAG_COMMENT_DETAIL_FRAGMENT = "tag_comment_detail_fragment";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.comment_detail_activity);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayShowTitleEnabled(true);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle(getString(R.string.tab_comments)); //use same title as CommentsActivity

        if (savedInstanceState == null) {
            Intent intent = getIntent();
            CommentDetailFragment commentDetailFragment = null;

            //this case is not used in current implementation, leave the code just in case
            if (intent.getStringExtra(KEY_COMMENT_DETAIL_NOTE_ID) != null && SimperiumUtils.getNotesBucket() != null) {
                try {
                    Note note = SimperiumUtils.getNotesBucket().get(
                            intent.getStringExtra(KEY_COMMENT_DETAIL_NOTE_ID)
                    );

                    if (intent.hasExtra(KEY_COMMENT_DETAIL_IS_REMOTE)) {
                        commentDetailFragment = CommentDetailFragment.newInstanceForRemoteNoteComment(note.getId());
                    } else {
                        commentDetailFragment = CommentDetailFragment.newInstance(note.getId());
                    }
                } catch (BucketObjectMissingException e) {
                    AppLog.e(AppLog.T.NOTIFS, "CommentDetailActivity was passed an invalid note id.");
                }
            } else if (intent.getIntExtra(KEY_COMMENT_DETAIL_LOCAL_TABLE_BLOG_ID, 0) > 0
                    && intent.getLongExtra(KEY_COMMENT_DETAIL_COMMENT_ID, 0) > 0) {
                commentDetailFragment =
                        CommentDetailFragment.newInstance(intent.getIntExtra(KEY_COMMENT_DETAIL_LOCAL_TABLE_BLOG_ID, 0),
                                intent.getLongExtra(KEY_COMMENT_DETAIL_COMMENT_ID, 0)
                        );
            }

            if (commentDetailFragment != null) {
                getSupportFragmentManager().beginTransaction()
                        .add(R.id.fragment_container, commentDetailFragment, TAG_COMMENT_DETAIL_FRAGMENT)
                        .commit();
            } else {
                ToastUtils.showToast(this, R.string.error_load_comment);
                finish();
            }
        }
    }

    @Override
    public void finish() {
        super.finish();
        if (!ActivityLauncher.slideOutToRight(this)) {
            overridePendingTransition(0, 0);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        ActivityId.trackLastActivity(ActivityId.COMMENT_DETAIL);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
