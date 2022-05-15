package cc.kafuu.bilidownload.fragment.personal;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.SimpleAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import cc.kafuu.bilidownload.PersonalActivity;
import cc.kafuu.bilidownload.R;
import cc.kafuu.bilidownload.adapter.VideoListAdapter;
import cc.kafuu.bilidownload.bilibili.Bili;
import cc.kafuu.bilidownload.bilibili.account.BiliFavourite;

public class FavoriteFragment extends Fragment implements VideoListAdapter.VideoListItemClickedListener, AdapterView.OnItemSelectedListener {
    private static final String TAG = "FavoriteFragment";

    private View mRootView = null;

    private boolean mLoading = false;
    private boolean mHasMore = false;
    private int mNextPage = 1;

    private List<BiliFavourite.Favourite> mFavourites = null;
    private int mCurrentFavourite = 0;

    private Spinner mFavoriteSpinner;
    private SwipeRefreshLayout mSwipeRefreshLayout;
    private RecyclerView mVideoList;
    private TextView mNoRecordTip;

    public FavoriteFragment() {
        // Required empty public constructor
    }

    public static FavoriteFragment newInstance() {
        FavoriteFragment fragment = new FavoriteFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        if (mRootView != null) {
            ((ViewGroup) container.getParent()).removeView(mRootView);
        } else {
            mRootView = inflater.inflate(R.layout.fragment_favorite, container, false);
        }

        findView();
        initView();

        loadFavorite();

        return mRootView;
    }

    private void findView() {
        mFavoriteSpinner = mRootView.findViewById(R.id.favoriteSpinner);
        mSwipeRefreshLayout = mRootView.findViewById(R.id.swipeRefreshLayout);
        mVideoList = mRootView.findViewById(R.id.videoList);
        mNoRecordTip = mRootView.findViewById(R.id.noRecordTip);
    }

    private void initView() {
        mSwipeRefreshLayout.setOnRefreshListener(this::loadFavorite);

        mVideoList.setLayoutManager(new LinearLayoutManager(getContext()));
        mVideoList.setAdapter(new VideoListAdapter(this));

        mFavoriteSpinner.setOnItemSelectedListener(FavoriteFragment.this);

        //当监听到列表快拉到尾部时加载新的数据
        mVideoList.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);

                LinearLayoutManager linearLayoutManager =  (LinearLayoutManager) mVideoList.getLayoutManager();

                assert linearLayoutManager != null;
                int total = linearLayoutManager.getItemCount();
                int lastVisible = linearLayoutManager.findLastVisibleItemPosition();
                Log.d(TAG, "onScrolled: " + total + "-" + lastVisible);

                if (mHasMore && total < lastVisible + 10) {
                    loadVideos(true);
                }
            }
        });
    }

    /**
     * 加载收藏夹
     * */
    private void loadFavorite() {
        if (mLoading) {
            return;
        }

        mLoading = true;
        mSwipeRefreshLayout.setRefreshing(true);

        BiliFavourite.getFavourites(Bili.biliAccount.getId(), new BiliFavourite.Callback<BiliFavourite.Favourite>() {
            @Override
            public void completed(List<BiliFavourite.Favourite> favourites, boolean hasMore) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    mLoading = false;

                    mFavourites = favourites;

                    List<Map<String, String>> items = new ArrayList<>();

                    for (BiliFavourite.Favourite favourite : favourites) {
                        Map<String, String> item = new HashMap<>();
                        item.put("favouriteName", favourite.title);
                        item.put("itemCount", String.valueOf(favourite.mediaCount));
                        items.add(item);
                    }

                    if (favourites.size() <= mCurrentFavourite) {
                        mCurrentFavourite = 0;
                    }

                    mFavoriteSpinner.setAdapter(new SimpleAdapter(getContext(), items, R.layout.spinner_item_favourite, new String[]{"favouriteName", "itemCount"}, new int[]{R.id.favouriteName, R.id.itemCount}));
                    mFavoriteSpinner.setSelection(mCurrentFavourite);

                    loadVideos(false);
                });

            }

            @Override
            public void failure(String message) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    mLoading = false;
                    mSwipeRefreshLayout.setRefreshing(false);
                    Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void loadVideos(boolean loadMore) {
        if (mLoading) {
            if (!loadMore) {
                mSwipeRefreshLayout.setRefreshing(false);
            }
            return;
        }

        mLoading = true;

        if (!loadMore) {
            mSwipeRefreshLayout.setRefreshing(true);
            mNextPage = 1;
            mHasMore = true;
        }
        BiliFavourite.getVideos(mFavourites.get(mCurrentFavourite), mNextPage, new BiliFavourite.Callback<BiliFavourite.Video>() {

            @Override
            public void completed(List<BiliFavourite.Video> videos, boolean hasMore) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    mLoading = false;
                    ++mNextPage;

                    if (!loadMore) {
                        mSwipeRefreshLayout.setRefreshing(false);
                        ((VideoListAdapter) Objects.requireNonNull(mVideoList.getAdapter())).clearRecord();
                    }

                    mHasMore = hasMore;

                    for (BiliFavourite.Video video : videos) {
                        VideoListAdapter.Record videoRecord = new VideoListAdapter.Record();

                        videoRecord.info = video.intro;
                        videoRecord.cover = video.cover;
                        videoRecord.title = video.title;
                        videoRecord.videoId = video.bv;

                        ((VideoListAdapter) Objects.requireNonNull(mVideoList.getAdapter())).addRecord(videoRecord);
                    }

                    Objects.requireNonNull(mVideoList.getAdapter()).notifyDataSetChanged();
                });

                updateTip();
            }

            @Override
            public void failure(String message) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    mLoading = false;

                    if (!loadMore) {
                        mSwipeRefreshLayout.setRefreshing(false);
                    }

                    Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
                });
                updateTip();
            }
        });
    }

    private void updateTip() {
        new Handler(Looper.getMainLooper()).post(() -> mNoRecordTip.setVisibility((Objects.requireNonNull(mVideoList.getAdapter()).getItemCount() == 0) ? View.VISIBLE : View.GONE));
    }

    @Override
    public void onVideoListItemClicked(VideoListAdapter.Record record) {
        if (Objects.requireNonNull(getActivity()).isDestroyed()) {
            return;
        }

        getActivity().setResult(PersonalActivity.ResultCodeVideoClicked, new Intent().putExtra("video_id", record.videoId));
        getActivity().finish();
    }


    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        if (position >= mFavourites.size()) {
            return;
        }

        mCurrentFavourite = position;
        loadVideos(false);
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {

    }
}