package org.wordpress.android.ui.themes;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;

import org.wordpress.android.R;
import org.wordpress.android.ui.ActivityLauncher;
import org.wordpress.android.ui.DualPaneContentActivity;

/**
 * The theme browser.
 */
public class ThemeBrowserActivity extends DualPaneContentActivity {

    @Override
    protected String getContentFragmentTag() {
        return "themes_fragment";
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.theme_browser_activity);

        if (savedInstanceState == null) {
            FragmentManager fragmentManager = getSupportFragmentManager();
            Fragment fragment = fragmentManager.findFragmentByTag(getContentFragmentTag());

            if (fragment == null) {
                fragment = Fragment.instantiate(this, ThemeFragment.class.getName(), getIntent().getExtras());
                fragment.setInitialSavedState(getFragmentSavedState());
                fragmentManager.beginTransaction().replace(R.id.fragment_container, fragment, getContentFragmentTag())
                        .commit();
            }
        }
    }

    @Override
    public void finish() {
        super.finish();
        ActivityLauncher.slideOutToRight(this);
    }
}
