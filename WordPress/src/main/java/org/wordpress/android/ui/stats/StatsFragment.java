package org.wordpress.android.ui.stats;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.wordpress.android.R;
import org.wordpress.android.WordPress;
import org.wordpress.android.analytics.AnalyticsTracker;
import org.wordpress.android.models.AccountHelper;
import org.wordpress.android.models.Blog;
import org.wordpress.android.ui.ActivityId;
import org.wordpress.android.ui.DualPaneHost;
import org.wordpress.android.ui.WPWebViewActivity;
import org.wordpress.android.ui.accounts.SignInActivity;
import org.wordpress.android.ui.main.MySiteFragment;
import org.wordpress.android.ui.prefs.AppPrefs;
import org.wordpress.android.util.AnalyticsUtils;
import org.wordpress.android.util.AppLog;
import org.wordpress.android.util.DualPaneHelper;
import org.wordpress.android.util.NetworkUtils;
import org.wordpress.android.util.RateLimitedTask;
import org.wordpress.android.util.ToastUtils;
import org.wordpress.android.util.helpers.SwipeToRefreshHelper;
import org.wordpress.android.util.widgets.CustomSwipeRefreshLayout;
import org.xmlrpc.android.ApiHelper;
import org.xmlrpc.android.XMLRPCCallback;
import org.xmlrpc.android.XMLRPCClientInterface;
import org.xmlrpc.android.XMLRPCFactory;

import java.util.HashMap;
import java.util.Map;

import de.greenrobot.event.EventBus;

/**
 * The native stats fragment
 * <p>
 * By pressing a spinner on the toolbar, the user can select which timeframe they wish to see.
 * </p>
 */
public class StatsFragment extends Fragment implements ScrollViewExt.ScrollViewListener,
        StatsVisitorsAndViewsFragment.OnDateChangeListener,
        StatsVisitorsAndViewsFragment.OnOverviewItemChangeListener,
        StatsInsightsTodayFragment.OnInsightsTodayClickListener,
        MySiteFragment.MySiteContentFragment {

    private static final String SAVED_WP_LOGIN_STATE = "SAVED_WP_LOGIN_STATE";
    private static final String SAVED_STATS_TIMEFRAME = "SAVED_STATS_TIMEFRAME";
    private static final String SAVED_STATS_REQUESTED_DATE = "SAVED_STATS_REQUESTED_DATE";
    private static final String SAVED_STATS_SCROLL_POSITION = "SAVED_STATS_SCROLL_POSITION";

    public static final String ARG_LOCAL_TABLE_BLOG_ID = "ARG_LOCAL_TABLE_BLOG_ID";
    public static final String ARG_LAUNCHED_FROM = "ARG_LAUNCHED_FROM";
    public static final String ARG_DESIRED_TIMEFRAME = "ARG_DESIRED_TIMEFRAME";

    private Spinner mSpinner;
    private ScrollViewExt mOuterScrollView;

    private static final int REQUEST_JETPACK = 7000;

    public enum StatsLaunchedFrom {
        STATS_WIDGET,
        NOTIFICATIONS
    }

    private int mResultCode = -1;
    private boolean mIsInFront;
    private int mLocalBlogID = -1;
    private StatsTimeframe mCurrentTimeframe = StatsTimeframe.INSIGHTS;
    private String mRequestedDate;
    private boolean mIsUpdatingStats;
    private SwipeToRefreshHelper mSwipeToRefreshHelper;
    private TimeframeSpinnerAdapter mTimeframeSpinnerAdapter;
    private final StatsTimeframe[] timeframes = {StatsTimeframe.INSIGHTS, StatsTimeframe.DAY, StatsTimeframe.WEEK,
            StatsTimeframe.MONTH, StatsTimeframe.YEAR};
    private StatsVisitorsAndViewsFragment.OverviewLabel mTabToSelectOnGraph = StatsVisitorsAndViewsFragment.OverviewLabel
            .VIEWS;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (WordPress.wpDB == null) {
            Toast.makeText(getActivity(), R.string.fatal_db_error, Toast.LENGTH_LONG).show();
            finishFragment();
        }

        setHasOptionsMenu(true);

        if (savedInstanceState != null) {
            mResultCode = savedInstanceState.getInt(SAVED_WP_LOGIN_STATE);
            mLocalBlogID = savedInstanceState.getInt(ARG_LOCAL_TABLE_BLOG_ID);
            mCurrentTimeframe = (StatsTimeframe) savedInstanceState.getSerializable(SAVED_STATS_TIMEFRAME);
            mRequestedDate = savedInstanceState.getString(SAVED_STATS_REQUESTED_DATE);
        } else if (getArguments() != null) {
            mLocalBlogID = getArguments().getInt(ARG_LOCAL_TABLE_BLOG_ID, -1);

            if (getArguments().containsKey(SAVED_STATS_TIMEFRAME)) {
                mCurrentTimeframe = (StatsTimeframe) getArguments().getSerializable(SAVED_STATS_TIMEFRAME);
            } else if (getArguments().containsKey(ARG_DESIRED_TIMEFRAME)) {
                mCurrentTimeframe = (StatsTimeframe) getArguments().getSerializable(ARG_DESIRED_TIMEFRAME);
            } else {
                // Read the value from app preferences here. Default to 0 - Insights
                mCurrentTimeframe = AppPrefs.getStatsTimeframe();
            }
            mRequestedDate = StatsUtils.getCurrentDateTZ(mLocalBlogID);

            if (getArguments().containsKey(ARG_LAUNCHED_FROM)) {
                StatsLaunchedFrom from = (StatsLaunchedFrom) getArguments().getSerializable(ARG_LAUNCHED_FROM);
                if (from == StatsLaunchedFrom.STATS_WIDGET) {
                    AnalyticsUtils.trackWithBlogDetails(AnalyticsTracker.Stat.STATS_WIDGET_TAPPED, WordPress.getBlog
                            (mLocalBlogID));
                }
            }
        }

        final Blog currentBlog = WordPress.getBlog(mLocalBlogID);

        if (currentBlog == null) {
            AppLog.e(AppLog.T.STATS, "The blog with local_blog_id " + mLocalBlogID + " cannot be loaded from the DB.");
            Toast.makeText(getActivity(), R.string.stats_no_blog, Toast.LENGTH_LONG).show();
            finishFragment();
        }

        // Track usage here
        if (savedInstanceState == null) {
            AnalyticsUtils.trackWithBlogDetails(AnalyticsTracker.Stat.STATS_ACCESSED, currentBlog);
            trackStatsAnalytics();
        }
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (savedInstanceState != null) {
            final int yScrollPosition = savedInstanceState.getInt(SAVED_STATS_SCROLL_POSITION);
            if (yScrollPosition != 0) {
                mOuterScrollView.postDelayed(new Runnable() {
                    public void run() {
                        if (isAdded()) {
                            mOuterScrollView.scrollTo(0, yScrollPosition);
                        }
                    }
                }, StatsConstants.STATS_SCROLL_TO_DELAY);
            }
        }
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.stats_fragment, container, false);

        Toolbar toolbar = (Toolbar) view.findViewById(R.id.toolbar);

        //modify UI to make it look good in dual pane
        if (DualPaneHelper.isInDualPaneMode(this)) {
            toolbar.setVisibility(View.GONE);

            ScrollView.LayoutParams lp = (ScrollView.LayoutParams) view.findViewById(R.id.content_container)
                    .getLayoutParams();
            lp.leftMargin = getResources().getDimensionPixelSize(R.dimen.content_margin_normal);
            lp.rightMargin = getResources().getDimensionPixelSize(R.dimen.content_margin_normal);
        } else {
            ((AppCompatActivity) getActivity()).setSupportActionBar(toolbar);
            ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
            actionBar.setDisplayShowTitleEnabled(false);
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        mSwipeToRefreshHelper = new SwipeToRefreshHelper(getActivity(), (CustomSwipeRefreshLayout) view.findViewById(R.id
                .ptr_layout),
                new SwipeToRefreshHelper.RefreshListener() {
                    @Override
                    public void onRefreshStarted() {

                        if (!NetworkUtils.checkConnection(getActivity())) {
                            mSwipeToRefreshHelper.setRefreshing(false);
                            return;
                        }

                        if (mIsUpdatingStats) {
                            AppLog.w(AppLog.T.STATS, "stats are already updating, refresh cancelled");
                            return;
                        }

                        mRequestedDate = StatsUtils.getCurrentDateTZ(mLocalBlogID);
                        if (checkCredentials()) {
                            updateTimeframeAndDateAndStartRefreshOfFragments(true);
                        }
                    }
                });

        mOuterScrollView = (ScrollViewExt) view.findViewById(R.id.scroll_view_stats);
        mOuterScrollView.setScrollViewListener(this);

        //Make sure the blog_id passed to this activity is valid and the blog is available within the app

        // create the fragments without forcing the re-creation. If the activity is restarted fragments can already
        // be there, and ready to be displayed without making any network connections. A fragment calls the stats service
        // if its internal datamodel is empty.
        createNestedFragments(false, view);

        View spinner;

        // In dual pane mode Spinner should look like the one used in Reader
        if (DualPaneHelper.isInDualPaneMode(this)) {
            spinner = inflater.inflate(R.layout.stats_time_frame_spinner_dual_pane,
                    (ViewGroup) view.findViewById(R.id.content_container), false);

            mSpinner = (Spinner) spinner.findViewById(R.id.time_frame_spinner_dual_pane);

            //changing spinner arrow color to mach custom spinner at ReaderPostListFragment
            Drawable spinnerBackground = mSpinner.getBackground();
            spinnerBackground.setColorFilter(ContextCompat.getColor(getActivity(), R.color.grey), PorterDuff.Mode.SRC_ATOP);
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
                mSpinner.setBackgroundDrawable(spinnerBackground);
            } else {
                mSpinner.setBackground(spinnerBackground);
            }

            //adding spinner at top of content container
            ((ViewGroup) view.findViewById(R.id.content_container)).addView(spinner, 0);
        } else {
            spinner = inflater.inflate(R.layout.toolbar_spinner, toolbar, true);
            mSpinner = (Spinner) spinner.findViewById(R.id.action_bar_spinner);
        }

        mTimeframeSpinnerAdapter = new TimeframeSpinnerAdapter(getActivity(), timeframes);

        mSpinner.setAdapter(mTimeframeSpinnerAdapter);
        mSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!isAdded()) {
                    return;
                }

                final StatsTimeframe selectedTimeframe = (StatsTimeframe) mTimeframeSpinnerAdapter.getItem(position);

                if (mCurrentTimeframe == selectedTimeframe) {
                    AppLog.d(AppLog.T.STATS, "The selected TIME FRAME is already active: " + selectedTimeframe
                            .getLabel());
                    return;
                }

                AppLog.d(AppLog.T.STATS, "NEW TIME FRAME : " + selectedTimeframe.getLabel());
                mCurrentTimeframe = selectedTimeframe;
                AppPrefs.setStatsTimeframe(mCurrentTimeframe);
                mRequestedDate = StatsUtils.getCurrentDateTZ(mLocalBlogID);
                createNestedFragments(true, getView()); // Need to recreate fragment here, since a new timeline was selected.
                mSpinner.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (isAdded()) {
                            scrollToTop();
                        }
                    }
                }, StatsConstants.STATS_SCROLL_TO_DELAY);

                trackStatsAnalytics();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // nop
            }
        });

        selectCurrentTimeframeInActionBar();

        TextView otherRecentStatsMovedLabel = (TextView) view.findViewById(R.id.stats_other_recent_stats_moved);
        otherRecentStatsMovedLabel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                for (int i = 0; i < timeframes.length; i++) {
                    if (timeframes[i] == StatsTimeframe.INSIGHTS) {
                        mSpinner.setSelection(i);
                        break;
                    }
                }

                mSpinner.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (isAdded()) {
                            scrollToTop();
                        }
                    }
                }, StatsConstants.STATS_SCROLL_TO_DELAY);
            }
        });

        return view;
    }

    private void trackStatsAnalytics() {
        // Track usage here
        Blog currentBlog = WordPress.getBlog(mLocalBlogID);
        switch (mCurrentTimeframe) {
            case INSIGHTS:
                AnalyticsUtils.trackWithBlogDetails(AnalyticsTracker.Stat.STATS_INSIGHTS_ACCESSED, currentBlog);
                break;
            case DAY:
                AnalyticsUtils.trackWithBlogDetails(AnalyticsTracker.Stat.STATS_PERIOD_DAYS_ACCESSED, currentBlog);
                break;
            case WEEK:
                AnalyticsUtils.trackWithBlogDetails(AnalyticsTracker.Stat.STATS_PERIOD_WEEKS_ACCESSED, currentBlog);
                break;
            case MONTH:
                AnalyticsUtils.trackWithBlogDetails(AnalyticsTracker.Stat.STATS_PERIOD_MONTHS_ACCESSED, currentBlog);
                break;
            case YEAR:
                AnalyticsUtils.trackWithBlogDetails(AnalyticsTracker.Stat.STATS_PERIOD_YEARS_ACCESSED, currentBlog);
                break;
        }
    }

    @Override
    public void onStop() {
        EventBus.getDefault().unregister(this);
        super.onStop();
    }

    @Override
    public void onStart() {
        super.onStart();
        EventBus.getDefault().register(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        mIsInFront = true;
        if (NetworkUtils.checkConnection(getActivity())) {
            checkCredentials();
        } else {
            mSwipeToRefreshHelper.setRefreshing(false);
        }
        ActivityId.trackLastActivity(ActivityId.STATS);
    }

    @Override
    public void onPause() {
        super.onPause();
        mIsInFront = false;
        mIsUpdatingStats = false;
        mSwipeToRefreshHelper.setRefreshing(false);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putInt(SAVED_WP_LOGIN_STATE, mResultCode);
        outState.putInt(StatsFragment.ARG_LOCAL_TABLE_BLOG_ID, mLocalBlogID);
        outState.putSerializable(SAVED_STATS_TIMEFRAME, mCurrentTimeframe);
        outState.putString(SAVED_STATS_REQUESTED_DATE, mRequestedDate);
        if (mOuterScrollView.getScrollY() != 0) {
            outState.putInt(SAVED_STATS_SCROLL_POSITION, mOuterScrollView.getScrollY());
        }
        super.onSaveInstanceState(outState);
    }

    private void createNestedFragments(boolean forceRecreationOfFragments, final View rootView) {
        if (!isAdded() || rootView == null) {
            return;
        }

        // Make the labels invisible see: https://github.com/wordpress-mobile/WordPress-Android/issues/3279
        rootView.findViewById(R.id.stats_other_recent_stats_label_insights).setVisibility(View.INVISIBLE);
        rootView.findViewById(R.id.stats_other_recent_stats_label_timeline).setVisibility(View.INVISIBLE);
        rootView.findViewById(R.id.stats_other_recent_stats_moved).setVisibility(View.INVISIBLE);

        FragmentManager fm = getChildFragmentManager();
        FragmentTransaction ft = fm.beginTransaction();

        StatsAbstractFragment fragment;

        if (mCurrentTimeframe != StatsTimeframe.INSIGHTS) {
            rootView.findViewById(R.id.stats_timeline_fragments_container).setVisibility(View.VISIBLE);
            rootView.findViewById(R.id.stats_insights_fragments_container).setVisibility(View.GONE);

            if (fm.findFragmentByTag(StatsVisitorsAndViewsFragment.TAG) == null || forceRecreationOfFragments) {
                fragment = StatsAbstractFragment.newVisitorsAndViewsInstance(StatsViewType.GRAPH_AND_SUMMARY,
                        mLocalBlogID, mCurrentTimeframe, mRequestedDate,
                        mTabToSelectOnGraph);
                ft.replace(R.id.stats_visitors_and_views_container, fragment, StatsVisitorsAndViewsFragment.TAG);
            }

            if (fm.findFragmentByTag(StatsTopPostsAndPagesFragment.TAG) == null || forceRecreationOfFragments) {
                fragment = StatsAbstractFragment.newInstance(StatsViewType.TOP_POSTS_AND_PAGES, mLocalBlogID,
                        mCurrentTimeframe, mRequestedDate);
                ft.replace(R.id.stats_top_posts_container, fragment, StatsTopPostsAndPagesFragment.TAG);
            }

            if (fm.findFragmentByTag(StatsReferrersFragment.TAG) == null || forceRecreationOfFragments) {
                fragment = StatsAbstractFragment.newInstance(StatsViewType.REFERRERS, mLocalBlogID, mCurrentTimeframe,
                        mRequestedDate);
                ft.replace(R.id.stats_referrers_container, fragment, StatsReferrersFragment.TAG);
            }

            if (fm.findFragmentByTag(StatsClicksFragment.TAG) == null || forceRecreationOfFragments) {
                fragment = StatsAbstractFragment.newInstance(StatsViewType.CLICKS, mLocalBlogID, mCurrentTimeframe,
                        mRequestedDate);
                ft.replace(R.id.stats_clicks_container, fragment, StatsClicksFragment.TAG);
            }

            if (fm.findFragmentByTag(StatsGeoviewsFragment.TAG) == null || forceRecreationOfFragments) {
                fragment = StatsAbstractFragment.newInstance(StatsViewType.GEOVIEWS, mLocalBlogID, mCurrentTimeframe,
                        mRequestedDate);
                ft.replace(R.id.stats_geoviews_container, fragment, StatsGeoviewsFragment.TAG);
            }

            if (fm.findFragmentByTag(StatsAuthorsFragment.TAG) == null || forceRecreationOfFragments) {
                fragment = StatsAbstractFragment.newInstance(StatsViewType.AUTHORS, mLocalBlogID, mCurrentTimeframe,
                        mRequestedDate);
                ft.replace(R.id.stats_top_authors_container, fragment, StatsAuthorsFragment.TAG);
            }

            if (fm.findFragmentByTag(StatsVideoplaysFragment.TAG) == null || forceRecreationOfFragments) {
                fragment = StatsAbstractFragment.newInstance(StatsViewType.VIDEO_PLAYS, mLocalBlogID, mCurrentTimeframe,
                        mRequestedDate);
                ft.replace(R.id.stats_video_container, fragment, StatsVideoplaysFragment.TAG);
            }

            if (fm.findFragmentByTag(StatsSearchTermsFragment.TAG) == null || forceRecreationOfFragments) {
                fragment = StatsAbstractFragment.newInstance(StatsViewType.SEARCH_TERMS, mLocalBlogID, mCurrentTimeframe,
                        mRequestedDate);
                ft.replace(R.id.stats_search_terms_container, fragment, StatsSearchTermsFragment.TAG);
            }
        } else {
            rootView.findViewById(R.id.stats_timeline_fragments_container).setVisibility(View.GONE);
            rootView.findViewById(R.id.stats_insights_fragments_container).setVisibility(View.VISIBLE);

            if (fm.findFragmentByTag(StatsInsightsMostPopularFragment.TAG) == null || forceRecreationOfFragments) {
                fragment = StatsAbstractFragment.newInstance(StatsViewType.INSIGHTS_MOST_POPULAR, mLocalBlogID,
                        mCurrentTimeframe, mRequestedDate);
                ft.replace(R.id.stats_insights_most_popular_container, fragment, StatsInsightsMostPopularFragment.TAG);
            }

            if (fm.findFragmentByTag(StatsInsightsAllTimeFragment.TAG) == null || forceRecreationOfFragments) {
                fragment = StatsAbstractFragment.newInstance(StatsViewType.INSIGHTS_ALL_TIME, mLocalBlogID,
                        mCurrentTimeframe, mRequestedDate);
                ft.replace(R.id.stats_insights_all_time_container, fragment, StatsInsightsAllTimeFragment.TAG);
            }

            if (fm.findFragmentByTag(StatsInsightsTodayFragment.TAG) == null || forceRecreationOfFragments) {
                fragment = StatsAbstractFragment.newInstance(StatsViewType.INSIGHTS_TODAY, mLocalBlogID, StatsTimeframe
                        .DAY, mRequestedDate);
                ft.replace(R.id.stats_insights_today_container, fragment, StatsInsightsTodayFragment.TAG);
            }

            if (fm.findFragmentByTag(StatsInsightsLatestPostSummaryFragment.TAG) == null || forceRecreationOfFragments) {
                fragment = StatsAbstractFragment.newInstance(StatsViewType.INSIGHTS_LATEST_POST_SUMMARY, mLocalBlogID,
                        mCurrentTimeframe, mRequestedDate);
                ft.replace(R.id.stats_insights_latest_post_summary_container, fragment,
                        StatsInsightsLatestPostSummaryFragment.TAG);
            }

            if (fm.findFragmentByTag(StatsCommentsFragment.TAG) == null || forceRecreationOfFragments) {
                fragment = StatsAbstractFragment.newInstance(StatsViewType.COMMENTS, mLocalBlogID, mCurrentTimeframe,
                        mRequestedDate);
                ft.replace(R.id.stats_comments_container, fragment, StatsCommentsFragment.TAG);
            }

            if (fm.findFragmentByTag(StatsTagsAndCategoriesFragment.TAG) == null || forceRecreationOfFragments) {
                fragment = StatsAbstractFragment.newInstance(StatsViewType.TAGS_AND_CATEGORIES, mLocalBlogID,
                        mCurrentTimeframe, mRequestedDate);
                ft.replace(R.id.stats_tags_and_categories_container, fragment, StatsTagsAndCategoriesFragment.TAG);
            }

            if (fm.findFragmentByTag(StatsPublicizeFragment.TAG) == null || forceRecreationOfFragments) {
                fragment = StatsAbstractFragment.newInstance(StatsViewType.PUBLICIZE, mLocalBlogID, mCurrentTimeframe,
                        mRequestedDate);
                ft.replace(R.id.stats_publicize_container, fragment, StatsPublicizeFragment.TAG);
            }

            if (fm.findFragmentByTag(StatsFollowersFragment.TAG) == null || forceRecreationOfFragments) {
                fragment = StatsAbstractFragment.newInstance(StatsViewType.FOLLOWERS, mLocalBlogID, mCurrentTimeframe,
                        mRequestedDate);
                ft.replace(R.id.stats_followers_container, fragment, StatsFollowersFragment.TAG);
            }
        }

        ft.commitAllowingStateLoss();

        // Slightly delayed labels setup: see https://github.com/wordpress-mobile/WordPress-Android/issues/3279
        mOuterScrollView.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!isAdded()) {
                    return;
                }
                boolean isInsights = mCurrentTimeframe == StatsTimeframe.INSIGHTS;
                rootView.findViewById(R.id.stats_other_recent_stats_label_insights)
                        .setVisibility(isInsights ? View.VISIBLE : View.GONE);
                rootView.findViewById(R.id.stats_other_recent_stats_label_timeline)
                        .setVisibility(isInsights ? View.GONE : View.VISIBLE);
                rootView.findViewById(R.id.stats_other_recent_stats_moved)
                        .setVisibility(isInsights ? View.GONE : View.VISIBLE);
            }
        }, StatsConstants.STATS_LABELS_SETUP_DELAY);
    }

    private void updateTimeframeAndDateAndStartRefreshOfFragments(boolean includeGraph) {
        if (!isAdded()) {
            return;
        }
        FragmentManager fm = getFragmentManager();

        if (mCurrentTimeframe != StatsTimeframe.INSIGHTS) {
            updateTimeframeAndDateAndStartRefreshInFragment(fm, StatsTopPostsAndPagesFragment.TAG);
            updateTimeframeAndDateAndStartRefreshInFragment(fm, StatsReferrersFragment.TAG);
            updateTimeframeAndDateAndStartRefreshInFragment(fm, StatsClicksFragment.TAG);
            updateTimeframeAndDateAndStartRefreshInFragment(fm, StatsGeoviewsFragment.TAG);
            updateTimeframeAndDateAndStartRefreshInFragment(fm, StatsAuthorsFragment.TAG);
            updateTimeframeAndDateAndStartRefreshInFragment(fm, StatsVideoplaysFragment.TAG);
            updateTimeframeAndDateAndStartRefreshInFragment(fm, StatsSearchTermsFragment.TAG);
            if (includeGraph) {
                updateTimeframeAndDateAndStartRefreshInFragment(fm, StatsVisitorsAndViewsFragment.TAG);
            }
        } else {
            updateTimeframeAndDateAndStartRefreshInFragment(fm, StatsInsightsTodayFragment.TAG);
            updateTimeframeAndDateAndStartRefreshInFragment(fm, StatsInsightsAllTimeFragment.TAG);
            updateTimeframeAndDateAndStartRefreshInFragment(fm, StatsInsightsMostPopularFragment.TAG);
            updateTimeframeAndDateAndStartRefreshInFragment(fm, StatsInsightsLatestPostSummaryFragment.TAG);
            updateTimeframeAndDateAndStartRefreshInFragment(fm, StatsCommentsFragment.TAG);
            updateTimeframeAndDateAndStartRefreshInFragment(fm, StatsTagsAndCategoriesFragment.TAG);
            updateTimeframeAndDateAndStartRefreshInFragment(fm, StatsPublicizeFragment.TAG);
            updateTimeframeAndDateAndStartRefreshInFragment(fm, StatsFollowersFragment.TAG);
        }
    }

    private boolean updateTimeframeAndDateAndStartRefreshInFragment(FragmentManager fm, String fragmentTAG) {
        StatsAbstractFragment fragment = (StatsAbstractFragment) fm.findFragmentByTag(fragmentTAG);
        if (fragment != null) {
            fragment.setDate(mRequestedDate);
            fragment.setTimeframe(mCurrentTimeframe);
            fragment.refreshStats();
            return true;
        }
        return false;
    }

    private void startWPComLoginActivity() {
        mResultCode = Activity.RESULT_CANCELED;
        Intent signInIntent = new Intent(getActivity(), SignInActivity.class);
        signInIntent.putExtra(SignInActivity.ARG_JETPACK_SITE_AUTH, mLocalBlogID);
        signInIntent.putExtra(SignInActivity.ARG_JETPACK_MESSAGE_AUTH,
                getString(R.string.stats_sign_in_jetpack_different_com_account));

        if (DualPaneHelper.isInDualPaneMode(StatsFragment.this)) {
            getParentFragment().startActivityForResult(signInIntent, SignInActivity.REQUEST_CODE);
        } else {
            startActivityForResult(signInIntent, SignInActivity.REQUEST_CODE);
        }
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == SignInActivity.REQUEST_CODE) {
            if (resultCode == Activity.RESULT_CANCELED) {
                finishFragment();
            }
            mResultCode = resultCode;
            final Blog currentBlog = WordPress.getBlog(mLocalBlogID);
            if (resultCode == Activity.RESULT_OK && currentBlog != null && !currentBlog.isDotcomFlag()) {
                if (currentBlog.getDotComBlogId() == null) {
                    final Handler handler = new Handler();
                    // Attempt to get the Jetpack blog ID
                    XMLRPCClientInterface xmlrpcClient = XMLRPCFactory.instantiate(currentBlog.getUri(), "", "");
                    Map<String, String> args = ApiHelper.blogOptionsXMLRPCParameters;
                    Object[] params = {
                            currentBlog.getRemoteBlogId(), currentBlog.getUsername(), currentBlog.getPassword(), args
                    };
                    xmlrpcClient.callAsync(new XMLRPCCallback() {
                        @Override
                        public void onSuccess(long id, Object result) {
                            if (result != null && (result instanceof HashMap)) {
                                Map<?, ?> blogOptions = (HashMap<?, ?>) result;
                                ApiHelper.updateBlogOptions(currentBlog, blogOptions);
                                AnalyticsUtils.refreshMetadata();
                                AnalyticsUtils.trackWithBlogDetails(AnalyticsTracker.Stat.SIGNED_INTO_JETPACK, currentBlog);
                                AnalyticsUtils.trackWithBlogDetails(
                                        AnalyticsTracker.Stat.PERFORMED_JETPACK_SIGN_IN_FROM_STATS_SCREEN, currentBlog);
                                if (!isAdded()) {
                                    return;
                                }
                                // We have the blogID now, but we need to re-check if the network connection is available
                                if (NetworkUtils.checkConnection(getActivity())) {
                                    handler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            mSwipeToRefreshHelper.setRefreshing(true);
                                            mRequestedDate = StatsUtils.getCurrentDateTZ(mLocalBlogID);
                                            createNestedFragments(true, getView());
                                            // Recreate the fragment and start a refresh of Stats
                                        }
                                    });
                                }
                            }
                        }

                        @Override
                        public void onFailure(long id, Exception error) {
                            AppLog.e(AppLog.T.STATS,
                                    "Cannot load blog options (wp.getOptions failed) "
                                            + "and no jetpack_client_id is then available",
                                    error);
                            handler.post(new Runnable() {
                                @Override
                                public void run() {
                                    if (!isAdded()) return;

                                    mSwipeToRefreshHelper.setRefreshing(false);
                                    ToastUtils.showToast(getActivity(),
                                            getString(R.string.error_refresh_stats),
                                            ToastUtils.Duration.LONG);
                                }
                            });
                        }
                    }, ApiHelper.Method.GET_OPTIONS, params);
                } else {
                    mRequestedDate = StatsUtils.getCurrentDateTZ(mLocalBlogID);
                    createNestedFragments(true, getView()); // Recreate the fragment and start a refresh of Stats
                }
                mSwipeToRefreshHelper.setRefreshing(true);
            }
        }
    }

    private class VerifyJetpackSettingsCallback implements ApiHelper.GenericCallback {
        // AsyncTasks are bound to the Activity that launched it. If the user rotate the device StatsActivity is restarted.
        // Use the event bus to fix this issue.

        @Override
        public void onSuccess() {
            EventBus.getDefault().post(new StatsEvents.JetpackSettingsCompleted(false));
        }

        @Override
        public void onFailure(ApiHelper.ErrorType errorType, String errorMessage, Throwable throwable) {
            EventBus.getDefault().post(new StatsEvents.JetpackSettingsCompleted(true));
        }
    }

    private void showJetpackMissingAlert() {
        if (!isAdded()) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        final Blog currentBlog = WordPress.getBlog(mLocalBlogID);
        if (currentBlog == null) {
            AppLog.e(AppLog.T.STATS, "The blog with local_blog_id " + mLocalBlogID + " cannot be loaded from the DB.");
            Toast.makeText(getActivity(), R.string.stats_no_blog, Toast.LENGTH_LONG).show();
            return;
        }
        if (currentBlog.isAdmin()) {
            builder.setMessage(getString(R.string.jetpack_message))
                    .setTitle(getString(R.string.jetpack_not_found));
            builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    String stringToLoad = currentBlog.getAdminUrl()
                            + "plugin-install.php?tab=search&s=jetpack+by+wordpress.com"
                            + "&plugin-search-input=Search+Plugins";
                    String authURL = WPWebViewActivity.getBlogLoginUrl(currentBlog);
                    Intent jetpackIntent = new Intent(getActivity(), WPWebViewActivity.class);
                    jetpackIntent.putExtra(WPWebViewActivity.AUTHENTICATION_USER, currentBlog.getUsername());
                    jetpackIntent.putExtra(WPWebViewActivity.AUTHENTICATION_PASSWD, currentBlog.getPassword());
                    jetpackIntent.putExtra(WPWebViewActivity.URL_TO_LOAD, stringToLoad);
                    jetpackIntent.putExtra(WPWebViewActivity.AUTHENTICATION_URL, authURL);

                    if (DualPaneHelper.isInDualPaneMode(StatsFragment.this)) {
                        getParentFragment().startActivityForResult(jetpackIntent, REQUEST_JETPACK);
                    } else {
                        startActivityForResult(jetpackIntent, REQUEST_JETPACK);
                    }

                    AnalyticsTracker.track(AnalyticsTracker.Stat.STATS_SELECTED_INSTALL_JETPACK);
                }
            });
            builder.setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    // User cancelled the dialog. Hide Stats.
                    finishFragment();
                }
            });
        } else {
            builder.setMessage(getString(R.string.jetpack_message_not_admin))
                    .setTitle(getString(R.string.jetpack_not_found));
            builder.setPositiveButton(R.string.yes, null);
        }
        builder.setCancelable(false);
        builder.create().show();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int i = item.getItemId();
        if (i == android.R.id.home) { //we only have UP button in single pane mode
            getActivity().onBackPressed();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void scrollToTop() {
        mOuterScrollView.fullScroll(ScrollView.FOCUS_UP);
    }

    // StatsInsightsTodayFragment calls this when the user taps on a item in Today's Stats
    @Override
    public void onInsightsTodayClicked(final StatsVisitorsAndViewsFragment.OverviewLabel item) {
        if (!isAdded()) {
            return;
        }

        mTabToSelectOnGraph = item;
        for (int i = 0; i < timeframes.length; i++) {
            if (timeframes[i] == StatsTimeframe.DAY) {
                mSpinner.setSelection(i);
                break;
            }
        }
    }

    // StatsVisitorsAndViewsFragment calls this when the user taps on a bar in the graph
    @Override
    public void onDateChanged(String blogID, StatsTimeframe timeframe, String date) {
        if (!isAdded()) {
            return;
        }
        mRequestedDate = date;
        updateTimeframeAndDateAndStartRefreshOfFragments(false);
        if (NetworkUtils.checkConnection(getActivity())) {
            mSwipeToRefreshHelper.setRefreshing(true);
        } else {
            mSwipeToRefreshHelper.setRefreshing(false);
        }
    }

    // StatsVisitorsAndViewsFragment calls this when the user taps on the tab bar to change the type of the graph
    @Override
    public void onOverviewItemChanged(StatsVisitorsAndViewsFragment.OverviewLabel newItem) {
        mTabToSelectOnGraph = newItem;
    }

    private boolean checkCredentials() {
        if (!isAdded()) {
            return false;
        }

        if (!NetworkUtils.isNetworkAvailable(getActivity())) {
            AppLog.w(AppLog.T.STATS, "StatsActivity > cannot check credentials since no internet connection available");
            return false;
        }

        final Blog currentBlog = WordPress.getBlog(mLocalBlogID);
        if (currentBlog == null) {
            AppLog.e(AppLog.T.STATS, "The blog with local_blog_id " + mLocalBlogID + " cannot be loaded from the DB.");
            return false;
        }

        final String blogId = currentBlog.getDotComBlogId();

        // blogId is always available for dotcom blogs. It could be null on Jetpack blogs...
        if (blogId != null) {
            // for self-hosted sites; launch the user into an activity where they can provide their credentials
            if (!currentBlog.isDotcomFlag() && !currentBlog.hasValidJetpackCredentials() &&
                    mResultCode != Activity.RESULT_CANCELED) {
                if (AccountHelper.isSignedInWordPressDotCom()) {
                    // Let's try the global wpcom credentials them first
                    String username = AccountHelper.getDefaultAccount().getUserName();
                    currentBlog.setDotcom_username(username);
                    WordPress.wpDB.saveBlog(currentBlog);
                    createNestedFragments(true, getView());
                } else {
                    startWPComLoginActivity();
                    return false;
                }
            }
        } else {
            // blogId is null at this point.
            if (!currentBlog.isDotcomFlag()) {
                // Refresh blog settings/options that includes 'jetpack_client_id' needed here
                mSwipeToRefreshHelper.setRefreshing(true);
                new ApiHelper.RefreshBlogContentTask(currentBlog,
                        new VerifyJetpackSettingsCallback()).execute(false);
                return false;
            } else {
                // blodID cannot be null on dotcom blogs.
                Toast.makeText(getActivity(), R.string.error_refresh_stats, Toast.LENGTH_LONG).show();
                AppLog.e(AppLog.T.STATS, "blogID is null for a wpcom blog!! " + currentBlog.getHomeURL());
                finishFragment();
            }
        }

        // check again that we've valid credentials for a Jetpack site
        if (!currentBlog.isDotcomFlag() && !currentBlog.hasValidJetpackCredentials() &&
                !AccountHelper.isSignedInWordPressDotCom()) {
            mSwipeToRefreshHelper.setRefreshing(false);
            AppLog.w(AppLog.T.STATS, "Jetpack blog with no wpcom credentials");
            return false;
        }

        return true;
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

    @SuppressWarnings("unused")
    public void onEventMainThread(StatsEvents.UpdateStatusChanged event) {
        if (!isAdded() || !mIsInFront) {
            return;
        }
        mSwipeToRefreshHelper.setRefreshing(event.mUpdating);
        mIsUpdatingStats = event.mUpdating;
    }

    @SuppressWarnings("unused")
    public void onEventMainThread(StatsEvents.JetpackSettingsCompleted event) {
        if (!isAdded() || !mIsInFront) {
            return;
        }
        mSwipeToRefreshHelper.setRefreshing(false);

        if (!event.isError) {
            final Blog currentBlog = WordPress.getBlog(mLocalBlogID);
            if (currentBlog == null) {
                return;
            }
            if (currentBlog.getDotComBlogId() == null) {
                // Blog has not returned a jetpack_client_id
                showJetpackMissingAlert();
            } else {
                checkCredentials();
            }
        } else {
            Toast.makeText(getActivity(), R.string.error_refresh_stats, Toast.LENGTH_LONG).show();
        }
    }

    @SuppressWarnings("unused")
    public void onEventMainThread(StatsEvents.JetpackAuthError event) {
        if (!isAdded() || !mIsInFront) {
            return;
        }

        if (event.mLocalBlogId != mLocalBlogID) {
            // The user has changed blog
            return;
        }
        mSwipeToRefreshHelper.setRefreshing(false);
        startWPComLoginActivity();
    }

    /*
    * make sure the passed timeframe is the one selected in the actionbar
    */
    private void selectCurrentTimeframeInActionBar() {
        if (!isAdded()) {
            return;
        }

        if (mTimeframeSpinnerAdapter == null || mSpinner == null) {
            return;
        }

        int position = mTimeframeSpinnerAdapter.getIndexOfTimeframe(mCurrentTimeframe);

        if (position > -1 && position != mSpinner.getSelectedItemPosition()) {
            mSpinner.setSelection(position);
        }
    }

    /*
      * adapter used by the timeframe spinner
      */
    private class TimeframeSpinnerAdapter extends BaseAdapter {
        private final StatsTimeframe[] mTimeframes;
        private final LayoutInflater mInflater;

        TimeframeSpinnerAdapter(Context context, StatsTimeframe[] timeframeNames) {
            super();
            mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            mTimeframes = timeframeNames;
        }

        @Override
        public int getCount() {
            return (mTimeframes != null ? mTimeframes.length : 0);
        }

        @Override
        public Object getItem(int position) {
            if (position < 0 || position >= getCount())
                return "";
            return mTimeframes[position];
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final View view;
            if (convertView == null) {
                if (DualPaneHelper.isInDualPaneMode(StatsFragment.this)) {
                    view = mInflater.inflate(R.layout.reader_tag_toolbar_menu_item, parent, false);
                } else {
                    view = mInflater.inflate(R.layout.toolbar_spinner_item, parent, false);
                }
            } else {
                view = convertView;
            }

            final TextView text = (TextView) view.findViewById(R.id.text);

            StatsTimeframe selectedTimeframe = (StatsTimeframe) getItem(position);
            text.setText(selectedTimeframe.getLabel());
            return view;
        }

        @Override
        public View getDropDownView(int position, View convertView, ViewGroup parent) {
            StatsTimeframe selectedTimeframe = (StatsTimeframe) getItem(position);
            final TagViewHolder holder;

            if (convertView == null) {
                convertView = mInflater.inflate(R.layout.toolbar_spinner_dropdown_item, parent, false);
                holder = new TagViewHolder(convertView);
                convertView.setTag(holder);
            } else {
                holder = (TagViewHolder) convertView.getTag();
            }

            holder.textView.setText(selectedTimeframe.getLabel());
            return convertView;
        }

        private class TagViewHolder {
            private final TextView textView;

            TagViewHolder(View view) {
                textView = (TextView) view.findViewById(R.id.text);
            }
        }

        public int getIndexOfTimeframe(StatsTimeframe tm) {
            int pos = 0;
            for (int i = 0; i < mTimeframes.length; i++) {
                if (mTimeframes[i] == tm) {
                    pos = i;
                    return pos;
                }
            }
            return pos;
        }
    }

    @Override
    public void onScrollChanged(ScrollViewExt scrollView, int x, int y, int oldx, int oldy) {
        // We take the last son in the scrollview
        View view = scrollView.getChildAt(scrollView.getChildCount() - 1);
        if (view == null) {
            return;
        }
        int diff = (view.getBottom() - (scrollView.getHeight() + scrollView.getScrollY() + view.getTop()));

        // if diff is zero, then the bottom has been reached
        if (diff == 0) {
            sTrackBottomReachedStats.runIfNotLimited();
        }
    }

    private static final RateLimitedTask sTrackBottomReachedStats = new RateLimitedTask(2) {
        protected boolean run() {
            AnalyticsTracker.track(AnalyticsTracker.Stat.STATS_SCROLLED_TO_BOTTOM);
            return true;
        }
    };

    @Override
    public int getMatchingRowViewId() {
        return R.id.row_stats;
    }
}