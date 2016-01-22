package org.wordpress.android.ui.stats;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.app.AppCompatActivity;
import android.widget.Toast;

import org.wordpress.android.R;
import org.wordpress.android.WordPress;
import org.wordpress.android.ui.ActivityLauncher;

/**
 * The native stats activity
 * <p>
 * By pressing a spinner on the action bar, the user can select which timeframe they wish to see.
 * </p>
 */
public class StatsActivity extends AppCompatActivity {

    public static final String ARG_LOCAL_TABLE_BLOG_ID = "ARG_LOCAL_TABLE_BLOG_ID";
    public static final String ARG_LAUNCHED_FROM = "ARG_LAUNCHED_FROM";
    public static final String ARG_DESIRED_TIMEFRAME = "ARG_DESIRED_TIMEFRAME";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (WordPress.wpDB == null) {
            Toast.makeText(this, R.string.fatal_db_error, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        setContentView(R.layout.stats_activity);

        Fragment statsFragment = Fragment.instantiate(this, StatsFragment.class.getName(), getIntent().getExtras());
        getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container, statsFragment).commit();
    }

    @Override
    public void finish() {
        super.finish();
        ActivityLauncher.slideOutToRight(this);
    }
}
