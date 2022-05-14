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
import android.widget.Toast;

import org.jetbrains.annotations.NotNull;

import java.util.List;

import cc.kafuu.bilidownload.PersonalActivity;
import cc.kafuu.bilidownload.R;
import cc.kafuu.bilidownload.adapter.VideoListAdapter;
import cc.kafuu.bilidownload.bilibili.account.BiliFollow;


public class FollowFragment extends Fragment implements VideoListAdapter.VideoListItemClickedListener {
    private static final String TAG = "CartoonFragment";

    private View mRootView = null;

    private BiliFollow.Type mType;

    private boolean mLoading = false;

    private int mPage = 1;
    private boolean mHasMore = true;

    private SwipeRefreshLayout mSwipeRefreshLayout;
    private RecyclerView mVideoList;


    public FollowFragment(BiliFollow.Type type) {
        mType = type;
    }


    public static FollowFragment newInstance(BiliFollow.Type type) {
        FollowFragment fragment = new FollowFragment(type);
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
    public View onCreateView(@NotNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        if (mRootView != null) {
            ((ViewGroup) container.getParent()).removeView(mRootView);
        } else {
            mRootView = inflater.inflate(R.layout.fragment_follow, container, false);
        }

        findView();
        initView();
        loadVideo(false);

        return mRootView;
    }

    private void findView() {
        mSwipeRefreshLayout = mRootView.findViewById(R.id.swipeRefreshLayout);
        mVideoList = mRootView.findViewById(R.id.videoList);
    }

    private void initView() {
        mSwipeRefreshLayout.setOnRefreshListener(() -> loadVideo(false));

        mVideoList.setLayoutManager(new LinearLayoutManager(getContext()));
        mVideoList.setAdapter(new VideoListAdapter(this));

        //当监听到列表快拉到尾部时加载新的历史记录数据
        mVideoList.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);

                LinearLayoutManager linearLayoutManager =  (LinearLayoutManager) mVideoList.getLayoutManager();

                int total = linearLayoutManager.getItemCount();
                int lastVisible = linearLayoutManager.findLastVisibleItemPosition();
                Log.d(TAG, "onScrolled: " + total + "-" + lastVisible);

                if (mHasMore && total < lastVisible + 10) {
                    loadVideo(true);
                }
            }
        });
    }

    private void loadVideo(boolean loadMore) {
        if (mLoading) {
            if (!loadMore) {
                mSwipeRefreshLayout.setRefreshing(false);
            }
            return;
        }

        mLoading = true;

        if (!loadMore) {
            mSwipeRefreshLayout.setRefreshing(true);
            mPage = 1;
            mHasMore = true;
        }

        BiliFollow.getFollows(mType, mPage, new BiliFollow.GetFollowsCallback() {

            @Override
            public void completed(List<BiliFollow.Record> records) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    mLoading = false;
                    ++mPage;

                    if (!loadMore) {
                        mSwipeRefreshLayout.setRefreshing(false);
                        ((VideoListAdapter) mVideoList.getAdapter()).clearRecord();
                    }

                    if (records.size() == 0) {
                        mHasMore = false;
                        return;
                    }

                    for (BiliFollow.Record record : records) {
                        VideoListAdapter.Record videoRecord = new VideoListAdapter.Record();

                        videoRecord.info = record.evaluate;
                        videoRecord.cover = record.cover;
                        videoRecord.title = record.title;
                        videoRecord.videoId = "ss" + record.seasonId;

                        ((VideoListAdapter) mVideoList.getAdapter()).addRecord(videoRecord);
                    }

                    mVideoList.getAdapter().notifyDataSetChanged();
                });

            }

            @Override
            public void failure(String message) {
                mLoading = false;

                if (!loadMore) {
                    mSwipeRefreshLayout.setRefreshing(false);
                }

                Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
            }
        });

    }

    @Override
    public void onVideoListItemClicked(VideoListAdapter.Record record) {
        if (getActivity().isDestroyed()) {
            return;
        }

        getActivity().setResult(PersonalActivity.ResultCodeVideoClicked, new Intent().putExtra("video_id", record.videoId));
        getActivity().finish();
    }
}