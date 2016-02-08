package org.wordpress.android.ui.stats;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.widget.Toast;

import org.wordpress.android.R;
import org.wordpress.android.WordPress;
import org.wordpress.android.ui.ActivityLauncher;
import org.wordpress.android.ui.DualPaneContentActivity;

/**
 * The native stats activity
 * <p>
 * By pressing a spinner on the action bar, the user can select which timeframe they wish to see.
 * </p>
 */
public class StatsActivity extends DualPaneContentActivity {



    @Override
    protected String getContentFragmentTag() {
        return "stats_fragment";
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (WordPress.wpDB == null) {
            Toast.makeText(this, R.string.fatal_db_error, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        setContentView(R.layout.stats_activity);

        if (savedInstanceState == null) {
            Fragment statsFragment = Fragment.instantiate(this, StatsFragment.class.getName(), getIntent().getExtras());
            statsFragment.setInitialSavedState(getFragmentSavedState());

            getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container, statsFragment,
                    getContentFragmentTag()).commit();
        }
    }

    @Override
    public void finish() {
        super.finish();
        ActivityLauncher.slideOutToRight(this);
    }
}
