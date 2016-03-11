package com.thebluealliance.androidclient.fragments.event;

import com.google.gson.JsonElement;

import com.thebluealliance.androidclient.Constants;
import com.thebluealliance.androidclient.R;
import com.thebluealliance.androidclient.activities.TeamAtEventActivity;
import com.thebluealliance.androidclient.adapters.EventStatsFragmentAdapter;
import com.thebluealliance.androidclient.adapters.ListViewAdapter;
import com.thebluealliance.androidclient.adapters.ViewEventFragmentPagerAdapter;
import com.thebluealliance.androidclient.binders.StatsListBinder;
import com.thebluealliance.androidclient.eventbus.TabLeavingEvent;
import com.thebluealliance.androidclient.eventbus.TabSelectedAndSettledEvent;
import com.thebluealliance.androidclient.fragments.DatafeedFragment;
import com.thebluealliance.androidclient.helpers.EventHelper;
import com.thebluealliance.androidclient.helpers.TeamHelper;
import com.thebluealliance.androidclient.listitems.ListElement;
import com.thebluealliance.androidclient.listitems.ListItem;
import com.thebluealliance.androidclient.models.NoDataViewParams;
import com.thebluealliance.androidclient.subscribers.StatsListSubscriber;
import com.thebluealliance.androidclient.views.NoDataView;

import android.os.Bundle;
import android.os.Parcelable;
import android.support.annotation.Nullable;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.Animation;
import android.view.animation.Transformation;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RadioGroup;

import java.util.Arrays;
import java.util.List;

import rx.Observable;

/**
 * Fragment that displays the team statistics for an FRC event.
 *
 * @author Phil Lopreiato
 * @author Bryce Matsuda
 * @author Nathan Walters
 */
public class EventStatsFragment
        extends DatafeedFragment<JsonElement, List<ListItem>, StatsListSubscriber, StatsListBinder> {

    private static final String KEY = "eventKey", SORT = "sort";

    private AlertDialog mStatsDialog;
    private String[] mItems;
    private String mEventKey;
    private Parcelable mListState;
    private SparseArray<Parcelable> mRadioState;
    private EventStatsFragmentAdapter mAdapter;
    private ListView mListView;
    private RadioGroup mRadioGroup;
    private String mStatSortCategory;
    private int mSelectedStatSort = -1;

    private View mSelectorGroup, mSelectorContainer;
    private boolean mHasCapturedHeightOfContainer = false;
    private int mOriginalStatsTypeSelectionContainerHeight;

    /**
     * Creates new event stats fragment for an event.
     *
     * @param eventKey key that represents an FRC event
     * @return new event stats fragment.
     */
    public static EventStatsFragment newInstance(String eventKey) {
        EventStatsFragment f = new EventStatsFragment();
        Bundle data = new Bundle();
        data.putString(KEY, eventKey);
        f.setArguments(data);
        return f;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        // Reload key if returning from another fragment/activity
        if (getArguments() != null) {
            mEventKey = getArguments().getString(KEY, "");
        }
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null) {
            mSelectedStatSort = savedInstanceState.getInt(SORT, -1);
        }
        if (mSelectedStatSort == -1) {
            /* Sort has not yet been set. Default to OPR */
            mSelectedStatSort = Arrays.binarySearch(getResources().getStringArray(R.array.statsDialogArray),
                    getString(R.string.dialog_stats_sort_opr));
        }

        // Setup stats sort dialog box
        mItems = getResources().getStringArray(R.array.statsDialogArray);
        mStatSortCategory = getSortTypeFromPosition(mSelectedStatSort);
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(R.string.dialog_stats_title)
                .setSingleChoiceItems(mItems, mSelectedStatSort, (dialogInterface, i) -> {
                    mSelectedStatSort = i;
                    mStatSortCategory = getSortTypeFromPosition(mSelectedStatSort);

                    dialogInterface.dismiss();

                    mAdapter = (EventStatsFragmentAdapter) mListView.getAdapter();
                    if (mAdapter != null && mStatSortCategory != null) {
                        mAdapter.sortStats(mStatSortCategory);
                    }
                }).setNegativeButton(R.string.dialog_cancel, (dialog, id) -> {
            dialog.cancel();
        });

        mStatsDialog = builder.create();
        setHasOptionsMenu(true);
        mSubscriber.setStatToSortBy(mStatSortCategory);
        mSubscriber.setEventYear(EventHelper.getYear(mEventKey));
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Setup views & listeners
        View view = inflater.inflate(R.layout.fragment_event_stats, null);
        mBinder.setRootView(view);
        mListView = (ListView) view.findViewById(R.id.list);
        mRadioGroup = (RadioGroup) view.findViewById(R.id.stats_type_selector);

        ProgressBar mProgressBar = (ProgressBar) view.findViewById(R.id.progress);
        // Either reload data if returning from another fragment/activity
        // Or get data if viewing fragment for the first time.
        if (mAdapter != null) {
            mListView.setAdapter(mAdapter);
            mListView.onRestoreInstanceState(mListState);
            mRadioGroup.restoreHierarchyState(mRadioState);
            mProgressBar.setVisibility(View.GONE);
        }

        mBinder.setNoDataView((NoDataView) view.findViewById(R.id.no_data));

        mListView.setOnItemClickListener((adapterView, view1, position, id) -> {
            if (!(adapterView.getAdapter() instanceof ListViewAdapter)
                    || position >= adapterView.getAdapter().getCount()
                    || !(((ListViewAdapter) adapterView.getAdapter()).getItem(position) instanceof ListElement)) {
                Log.d(Constants.LOG_TAG, "Can't open stat item");
                return;
            }
            String teamKey = ((ListElement) ((ListViewAdapter) adapterView.getAdapter()).getItem(position)).getKey();
            if (TeamHelper.validateTeamKey(teamKey) ^ TeamHelper.validateMultiTeamKey(teamKey)) {
                teamKey = TeamHelper.baseTeamKey(teamKey);
                startActivity(TeamAtEventActivity.newInstance(getActivity(), mEventKey, teamKey));
            }
        });

        mSelectorContainer = view.findViewById(R.id.stats_type_selector_container);
        mSelectorGroup = view.findViewById(R.id.stats_type_selector);

        mSelectorContainer.getViewTreeObserver().addOnGlobalLayoutListener(
                new ViewTreeObserver.OnGlobalLayoutListener(){

                    @Override
                    public void onGlobalLayout() {
                        mOriginalStatsTypeSelectionContainerHeight = mSelectorContainer.getHeight();
                        mHasCapturedHeightOfContainer = true;

                        mSelectorContainer.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                        mSelectorContainer.setVisibility(View.GONE);
                    }

                });
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        mEventBus.register(this);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.stats_sort_menu, menu);
        inflater.inflate(R.menu.stats_help_menu, menu);
        mBinder.setMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_sort_by) {
            mStatsDialog.show();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(SORT, mSelectedStatSort);
    }

    @Override
    public void onPause() {
        // Save the data if moving away from fragment.
        super.onPause();
        if (mListView != null) {
            mAdapter = (EventStatsFragmentAdapter) mListView.getAdapter();
            mListState = mListView.onSaveInstanceState();
        }
        if (mRadioGroup != null) {
            mRadioState = new SparseArray<>();
            mRadioGroup.saveHierarchyState(mRadioState);
        }

        mEventBus.unregister(this);
    }

    @Override
    protected void inject() {
        mComponent.inject(this);
    }

    @Override
    protected Observable<? extends JsonElement> getObservable(String tbaCacheHeader) {
        return mDatafeed.fetchEventStats(mEventKey, tbaCacheHeader);
    }

    @Override
    protected String getRefreshTag() {
        return String.format("eventStats_%1$s", mEventKey);
    }

    @Override
    protected NoDataViewParams getNoDataParams() {
        return new NoDataViewParams(R.drawable.ic_poll_black_48dp, R.string.no_stats_data);
    }

    private String getSortTypeFromPosition(int position) {
        if (mItems[position].equals(getString(R.string.dialog_stats_sort_opr))) {
            return "opr";
        } else if (mItems[position].equals(getString(R.string.dialog_stats_sort_dpr))) {
            return "dpr";
        } else if (mItems[position].equals(getString(R.string.dialog_stats_sort_ccwm))) {
            return "ccwm";
        } else if (mItems[position].equals(getString(R.string.dialog_stats_sort_team))) {
            return "team";
        }
        return "";
    }

    public void onEvent(TabSelectedAndSettledEvent e) {
        if (e.getIndex() == ViewEventFragmentPagerAdapter.TAB_STATS) {
            // Show selection view
            if (getView() == null) {
                return;
            }

            View selectorContainer = getView().findViewById(R.id.stats_type_selector_container);
            View selectorGroup = getView().findViewById(R.id.stats_type_selector);

            if (!mHasCapturedHeightOfContainer) {
                mOriginalStatsTypeSelectionContainerHeight = selectorContainer.getHeight();
                mHasCapturedHeightOfContainer = true;
            }

            selectorGroup.setAlpha(0.0f);
            selectorGroup.animate().alpha(1.0f).setDuration(250).setStartDelay(250).start();

            selectorContainer.setVisibility(View.VISIBLE);
            Animation anim = new HeightAnimation(selectorContainer, 0, mOriginalStatsTypeSelectionContainerHeight);
            anim.setDuration(250);
            selectorContainer.startAnimation(anim);
        }
    }

    public void onEvent(TabLeavingEvent e) {
        // Hide selection view
        if (getView() == null) {
            return;
        }

        View selectorContainer = getView().findViewById(R.id.stats_type_selector_container);
        View selectorGroup = getView().findViewById(R.id.stats_type_selector);

        if (!mHasCapturedHeightOfContainer) {
            mOriginalStatsTypeSelectionContainerHeight = selectorContainer.getHeight();
            mHasCapturedHeightOfContainer = true;
        }

        selectorGroup.setAlpha(1.0f);
        selectorGroup.animate().alpha(0.0f).setDuration(250).start();

        Animation anim = new HeightAnimation(selectorContainer, mOriginalStatsTypeSelectionContainerHeight, 0);
        anim.setDuration(250);
        anim.setStartOffset(250);
        selectorContainer.startAnimation(anim);
    }


    public class HeightAnimation extends Animation {
        protected final int originalHeight;
        protected final View view;
        protected float perValue;

        public HeightAnimation(View view, int fromHeight, int toHeight) {
            this.view = view;
            this.originalHeight = fromHeight;
            this.perValue = (toHeight - fromHeight);
        }

        @Override
        protected void applyTransformation(float interpolatedTime, Transformation t) {
            int height = (int) (originalHeight + perValue * interpolatedTime);
            //Log.d(Constants.LOG_TAG, "Height: " + height);
            view.getLayoutParams().height = height;
            view.requestLayout();
        }

        @Override
        public boolean willChangeBounds() {
            return true;
        }
    }

}
