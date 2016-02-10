package org.wordpress.android.ui.themes;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.android.volley.AuthFailureError;
import com.android.volley.VolleyError;
import com.wordpress.rest.RestRequest;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.wordpress.android.R;
import org.wordpress.android.WordPress;
import org.wordpress.android.analytics.AnalyticsTracker;
import org.wordpress.android.models.Blog;
import org.wordpress.android.models.Theme;
import org.wordpress.android.ui.ActivityId;
import org.wordpress.android.ui.DualPaneHost;
import org.wordpress.android.ui.main.MySiteFragment;
import org.wordpress.android.util.AnalyticsUtils;
import org.wordpress.android.util.AppLog;
import org.wordpress.android.util.DualPaneHelper;
import org.wordpress.android.util.NetworkUtils;
import org.wordpress.android.widgets.WPAlertDialogFragment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import de.greenrobot.event.EventBus;

public class ThemeFragment extends Fragment implements ThemeBrowserFragment.ThemeBrowserFragmentCallback, MySiteFragment
        .MySiteContentFragment {
    public static final int THEME_FETCH_MAX = 100;
    public static final int ACTIVATE_THEME = 1;
    public static final String THEME_ID = "theme_id";
    private static final String IS_IN_SEARCH_MODE = "is_in_search_mode";
    private static final String ALERT_TAB = "alert";

    private static final String TAG_BROWSE_FRAGMENT = "theme_browser_fragment";
    private static final String TAG_SEARCH_FRAGMENT = "theme_search_fragment";

    private boolean mFetchingThemes = false;
    private boolean mIsRunning;
    private ThemeBrowserFragment mThemeBrowserFragment;
    private ThemeSearchFragment mThemeSearchFragment;
    private Theme mCurrentTheme;
    private boolean mIsInSearchMode;

    public static boolean isAccessible() {
        // themes are only accessible to admin wordpress.com users
        Blog blog = WordPress.getCurrentBlog();
        return (blog != null && blog.isAdmin() && blog.isDotcomFlag());
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (WordPress.wpDB == null) {
            Toast.makeText(getActivity(), R.string.fatal_db_error, Toast.LENGTH_LONG).show();
            finishFragment();
        }

        setCurrentThemeFromDB();
        setHasOptionsMenu(true);
        if (savedInstanceState != null) {
            mIsInSearchMode = savedInstanceState.getBoolean(IS_IN_SEARCH_MODE);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int i = item.getItemId();
        if (i == android.R.id.home) {
            FragmentManager fm = getChildFragmentManager();
            if (fm.getBackStackEntryCount() > 0) {
                fm.popBackStack();
            } else {
                getActivity().onBackPressed();
            }
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void setCurrentThemeFromDB() {
        mCurrentTheme = WordPress.wpDB.getCurrentTheme(getBlogId());
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.theme_fragment, container, false);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        if (savedInstanceState == null) {
            AnalyticsUtils.trackWithCurrentBlogDetails(AnalyticsTracker.Stat.THEMES_ACCESSED_THEMES_BROWSER);
            addBrowserFragment();
        }
    }

    private ThemeBrowserFragment getThemeBrowserFragment() {
        mThemeBrowserFragment = (ThemeBrowserFragment) getChildFragmentManager().findFragmentByTag(TAG_BROWSE_FRAGMENT);
        if (mThemeBrowserFragment == null) {
            mThemeBrowserFragment = new ThemeBrowserFragment();
        }
        return mThemeBrowserFragment;
    }

    private ThemeSearchFragment getThemeSearchFragment() {
        mThemeSearchFragment = (ThemeSearchFragment) getChildFragmentManager().findFragmentByTag(TAG_SEARCH_FRAGMENT);
        if (mThemeSearchFragment == null) {
            mThemeSearchFragment = new ThemeSearchFragment();
        }
        return mThemeSearchFragment;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == ACTIVATE_THEME && resultCode == Activity.RESULT_OK && data != null) {
            String themeId = data.getStringExtra(THEME_ID);
            if (!TextUtils.isEmpty(themeId)) {
                activateTheme(themeId);
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        showCorrectToolbar();
        mIsRunning = true;
        ActivityId.trackLastActivity(ActivityId.THEMES);

        fetchThemesIfNoneAvailable();
    }

    @Override
    public void onPause() {
        super.onPause();
        mIsRunning = false;
    }

    public void setIsInSearchMode(boolean isInSearchMode) {
        mIsInSearchMode = isInSearchMode;
    }

    public void fetchThemes() {
        if (mFetchingThemes) {
            return;
        }
        String siteId = getBlogId();
        mFetchingThemes = true;
        int page = 1;
        if (mThemeBrowserFragment != null) {
            page = mThemeBrowserFragment.getPage();
        }
        WordPress.getRestClientUtilsV1_2().getFreeThemes(siteId, THEME_FETCH_MAX, page, new RestRequest.Listener() {
                    @Override
                    public void onResponse(JSONObject response) {
                        new FetchThemesTask().execute(response);
                    }
                }, new RestRequest.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError response) {
                        if (response.toString().equals(AuthFailureError.class.getName())) {
                            String errorTitle = getString(R.string.theme_auth_error_title);
                            String errorMsg = getString(R.string.theme_auth_error_message);

                            if (mIsRunning) {
                                FragmentTransaction ft = getChildFragmentManager().beginTransaction();
                                WPAlertDialogFragment fragment = WPAlertDialogFragment.newAlertDialog(errorMsg, errorTitle);
                                ft.add(fragment, ALERT_TAB);
                                ft.commitAllowingStateLoss();
                            }
                            AppLog.d(AppLog.T.THEMES, getString(R.string.theme_auth_error_authenticate));
                        } else {
                            Toast.makeText(getActivity(), R.string.theme_fetch_failed, Toast.LENGTH_LONG)
                                    .show();
                            AppLog.d(AppLog.T.THEMES, getString(R.string.theme_fetch_failed) + ": " + response.toString());
                        }
                        mFetchingThemes = false;
                    }
                }
        );
    }

    public void searchThemes(String searchTerm) {
        String siteId = getBlogId();
        mFetchingThemes = true;
        int page = 1;
        if (mThemeSearchFragment != null) {
            page = mThemeSearchFragment.getPage();
        }

        WordPress.getRestClientUtilsV1_2().getFreeSearchThemes(siteId, THEME_FETCH_MAX, page, searchTerm, new RestRequest
                        .Listener() {
                    @Override
                    public void onResponse(JSONObject response) {
                        new FetchThemesTask().execute(response);
                    }
                }, new RestRequest.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError response) {
                        if (response.toString().equals(AuthFailureError.class.getName())) {
                            String errorTitle = getString(R.string.theme_auth_error_title);
                            String errorMsg = getString(R.string.theme_auth_error_message);

                            if (mIsRunning) {
                                FragmentTransaction ft = getChildFragmentManager().beginTransaction();
                                WPAlertDialogFragment fragment = WPAlertDialogFragment.newAlertDialog(errorMsg, errorTitle);
                                ft.add(fragment, ALERT_TAB);
                                ft.commitAllowingStateLoss();
                            }
                            AppLog.d(AppLog.T.THEMES, getString(R.string.theme_auth_error_authenticate));
                        }
                        mFetchingThemes = false;
                    }
                }
        );
    }

    public void fetchCurrentTheme() {
        final String siteId = getBlogId();

        WordPress.getRestClientUtilsV1_1().getCurrentTheme(siteId, new RestRequest.Listener() {
                    @Override
                    public void onResponse(JSONObject response) {
                        try {
                            mCurrentTheme = Theme.fromJSONV1_1(response);
                            if (mCurrentTheme != null) {
                                mCurrentTheme.setIsCurrent(true);
                                mCurrentTheme.save();
                                WordPress.wpDB.setCurrentTheme(siteId, mCurrentTheme.getId());
                                if (mThemeBrowserFragment != null) {
                                    mThemeBrowserFragment.setRefreshing(false);
                                    if (mThemeBrowserFragment.getCurrentThemeTextView() != null) {
                                        mThemeBrowserFragment.getCurrentThemeTextView().setText(mCurrentTheme.getName());
                                        mThemeBrowserFragment.setCurrentThemeId(mCurrentTheme.getId());
                                    }
                                }
                            }
                        } catch (JSONException e) {
                            AppLog.e(AppLog.T.THEMES, e);
                        }
                    }
                }, new RestRequest.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError response) {
                        String themeId = WordPress.wpDB.getCurrentThemeId(siteId);
                        mCurrentTheme = WordPress.wpDB.getTheme(siteId, themeId);
                        if (mCurrentTheme != null && mThemeBrowserFragment != null) {
                            if (mThemeBrowserFragment.getCurrentThemeTextView() != null) {
                                mThemeBrowserFragment.getCurrentThemeTextView().setText(mCurrentTheme.getName());
                                mThemeBrowserFragment.setCurrentThemeId(mCurrentTheme.getId());
                            }
                        }
                    }
                }
        );
    }

    protected Theme getCurrentTheme() {
        return mCurrentTheme;
    }

    protected void setThemeBrowserFragment(ThemeBrowserFragment themeBrowserFragment) {
        mThemeBrowserFragment = themeBrowserFragment;
    }

    protected void setThemeSearchFragment(ThemeSearchFragment themeSearchFragment) {
        mThemeSearchFragment = themeSearchFragment;
    }

    private String getBlogId() {
        if (WordPress.getCurrentBlog() == null)
            return "0";
        return String.valueOf(WordPress.getCurrentBlog().getRemoteBlogId());
    }

    private void fetchThemesIfNoneAvailable() {
        if (NetworkUtils.isNetworkAvailable(getActivity()) && WordPress.getCurrentBlog() != null
                && WordPress.wpDB.getThemeCount(getBlogId()) == 0) {
            fetchThemes();
            mThemeBrowserFragment.setRefreshing(true);
        }
    }

    protected void showToolbar() {
        if (!isAdded() || getView() == null) return;

        if (!DualPaneHelper.isInDualPaneMode(this)) {
            Toolbar toolbar = (Toolbar) getView().findViewById(R.id.toolbar);
            ((AppCompatActivity) getActivity()).setSupportActionBar(toolbar);

            ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
            actionBar.setDisplayShowTitleEnabled(true);
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle(R.string.themes);
            getView().findViewById(R.id.toolbar).setVisibility(View.VISIBLE);
        }

        getView().findViewById(R.id.toolbar_search).setVisibility(View.GONE);
    }

    private void showCorrectToolbar() {
        if (mIsInSearchMode) {
            showSearchToolbar();
        } else {
            showToolbar();
        }
    }

    private void showSearchToolbar() {
        if (!isAdded() || getView() == null) return;

        Toolbar toolbarSearch = (Toolbar) getView().findViewById(R.id.toolbar_search);
        ((AppCompatActivity) getActivity()).setSupportActionBar(toolbarSearch);

        if (DualPaneHelper.isInDualPaneMode(this)) {
            LinearLayout.LayoutParams layoutParams = (LinearLayout.LayoutParams) toolbarSearch.getLayoutParams();
            layoutParams.topMargin = getResources().getDimensionPixelSize(R.dimen.content_margin_normal);
            layoutParams.leftMargin = getResources().getDimensionPixelSize(R.dimen.margin_small);
            layoutParams.rightMargin = getResources().getDimensionPixelSize(R.dimen.margin_small);
        }

        toolbarSearch.setTitle("");
        getView().findViewById(R.id.toolbar).setVisibility(View.GONE);
        getView().findViewById(R.id.toolbar_search).setVisibility(View.VISIBLE);
    }

    private void addBrowserFragment() {
        showToolbar();
        FragmentTransaction fragmentTransaction = getChildFragmentManager().beginTransaction();
        fragmentTransaction.add(R.id.fragment_container, getThemeBrowserFragment(), TAG_BROWSE_FRAGMENT);
        fragmentTransaction.commit();
    }

    private void addSearchFragment() {
        showSearchToolbar();
        FragmentTransaction fragmentTransaction = getChildFragmentManager().beginTransaction();
        fragmentTransaction.replace(R.id.fragment_container, getThemeSearchFragment(), TAG_SEARCH_FRAGMENT);
        fragmentTransaction.addToBackStack(null);
        fragmentTransaction.commit();
    }

    private void activateTheme(final String themeId) {
        final String siteId = getBlogId();
        final String newThemeId = themeId;

        WordPress.getRestClientUtils().setTheme(siteId, themeId, new RestRequest.Listener() {
            @Override
            public void onResponse(JSONObject response) {
                WordPress.wpDB.setCurrentTheme(siteId, newThemeId);
                Theme newTheme = WordPress.wpDB.getTheme(siteId, newThemeId);

                Map<String, Object> themeProperties = new HashMap<>();
                themeProperties.put(THEME_ID, themeId);
                AnalyticsUtils.trackWithCurrentBlogDetails(AnalyticsTracker.Stat.THEMES_CHANGED_THEME, themeProperties);

                if (isAdded()) {
                    showAlertDialogOnNewSettingNewTheme(newTheme);
                    fetchCurrentTheme();
                }
            }
        }, new RestRequest.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Toast.makeText(getActivity(), R.string.theme_activation_error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showAlertDialogOnNewSettingNewTheme(Theme newTheme) {
        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(getActivity());

        String thanksMessage = String.format(getString(R.string.theme_prompt), newTheme.getName());
        if (!newTheme.getAuthor().isEmpty()) {
            thanksMessage = thanksMessage + String.format(getString(R.string.theme_by_author_prompt_append), newTheme
                    .getAuthor());
        }

        dialogBuilder.setMessage(thanksMessage);
        dialogBuilder.setNegativeButton(R.string.theme_done, null);

        if (!DualPaneHelper.isInDualPaneMode(this)) {
            dialogBuilder.setPositiveButton(R.string.theme_manage_site, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    getActivity().finish();
                }
            });
        }

        AlertDialog alertDialog = dialogBuilder.create();
        alertDialog.show();
    }

    private void startWebActivity(String themeId, ThemeWebActivity.ThemeWebActivityType type) {
        String toastText = getString(R.string.no_network_message);

        if (NetworkUtils.isNetworkAvailable(getActivity())) {
            if (mCurrentTheme != null && !TextUtils.isEmpty(themeId)) {
                boolean isCurrentTheme = mCurrentTheme.getId().equals(themeId);
                Map<String, Object> themeProperties = new HashMap<>();
                themeProperties.put(THEME_ID, themeId);

                switch (type) {
                    case PREVIEW:
                        AnalyticsUtils.trackWithCurrentBlogDetails(AnalyticsTracker.Stat.THEMES_PREVIEWED_SITE,
                                themeProperties);
                        break;
                    case DEMO:
                        AnalyticsUtils.trackWithCurrentBlogDetails(AnalyticsTracker.Stat.THEMES_DEMO_ACCESSED,
                                themeProperties);
                        break;
                    case DETAILS:
                        AnalyticsUtils.trackWithCurrentBlogDetails(AnalyticsTracker.Stat.THEMES_DETAILS_ACCESSED,
                                themeProperties);
                        break;
                    case SUPPORT:
                        AnalyticsUtils.trackWithCurrentBlogDetails(AnalyticsTracker.Stat.THEMES_SUPPORT_ACCESSED,
                                themeProperties);
                        break;
                }

                if (DualPaneHelper.isInDualPaneMode(this)) {
                    ThemeWebActivity.openTheme(getParentFragment(), themeId, type, isCurrentTheme);
                } else {
                    ThemeWebActivity.openTheme(this, themeId, type, isCurrentTheme);
                }

                return;
            } else {
                toastText = getString(R.string.could_not_load_theme);
            }
        }

        Toast.makeText(getActivity(), toastText, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onActivateSelected(String themeId) {
        activateTheme(themeId);
    }

    @Override
    public void onTryAndCustomizeSelected(String themeId) {
        startWebActivity(themeId, ThemeWebActivity.ThemeWebActivityType.PREVIEW);
    }

    @Override
    public void onViewSelected(String themeId) {
        startWebActivity(themeId, ThemeWebActivity.ThemeWebActivityType.DEMO);
    }

    @Override
    public void onDetailsSelected(String themeId) {
        startWebActivity(themeId, ThemeWebActivity.ThemeWebActivityType.DETAILS);
    }

    @Override
    public void onSupportSelected(String themeId) {
        startWebActivity(themeId, ThemeWebActivity.ThemeWebActivityType.SUPPORT);
    }

    @Override
    public void onSearchClicked() {
        mIsInSearchMode = true;
        AnalyticsUtils.trackWithCurrentBlogDetails(AnalyticsTracker.Stat.THEMES_ACCESSED_SEARCH);
        addSearchFragment();
    }

    @Override
    public int getMatchingRowViewId() {
        return R.id.row_themes;
    }

    public class FetchThemesTask extends AsyncTask<JSONObject, Void, ArrayList<Theme>> {
        @Override
        protected ArrayList<Theme> doInBackground(JSONObject... args) {
            JSONObject response = args[0];
            final ArrayList<Theme> themes = new ArrayList<>();

            if (response != null) {
                JSONArray array;
                try {
                    array = response.getJSONArray("themes");

                    if (array != null) {
                        int count = array.length();
                        for (int i = 0; i < count; i++) {
                            JSONObject object = array.getJSONObject(i);
                            Theme theme = Theme.fromJSONV1_2(object);
                            if (theme != null) {
                                theme.save();
                                themes.add(theme);
                            }
                        }
                    }
                } catch (JSONException e) {
                    AppLog.e(AppLog.T.THEMES, e);
                }
            }

            fetchCurrentTheme();

            if (themes.size() > 0) {
                return themes;
            }

            return null;
        }

        @Override
        protected void onPostExecute(final ArrayList<Theme> result) {
            if (!isAdded()) return;
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mFetchingThemes = false;
                    if (mThemeBrowserFragment != null && mThemeBrowserFragment.isVisible()) {
                        mThemeBrowserFragment.getEmptyTextView().setText(R.string.theme_no_search_result_found);
                        mThemeBrowserFragment.setRefreshing(false);
                    } else if (mThemeSearchFragment != null && mThemeSearchFragment.isVisible()) {
                        mThemeSearchFragment.getEmptyTextView().setText(R.string.theme_no_search_result_found);
                        mThemeSearchFragment.setRefreshing(false);
                    }
                }
            });
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(IS_IN_SEARCH_MODE, mIsInSearchMode);
    }

    private void finishFragment() {
        if (!isAdded()) {
            return;
        }

        if (DualPaneHelper.isInDualPaneMode(this)) {

            DualPaneHost dualPaneHost = DualPaneHelper.getDualPaneHost(this);
            if (dualPaneHost != null) {
                //we are telling MySite fragment that this fragment will be killed after encountering error
                //we have to use sticky event because if error occurs in onActivityResult
                //listener in MySite fragment might not be ready yet
                EventBus.getDefault().postSticky(new MySiteFragment.ContentFragmentFinishedOnError());
                dualPaneHost.resetContentPane();
            }
        } else {
            //if fragment is not part of dual pane host finish activity it belongs to.
            getActivity().finish();
        }
    }
}
