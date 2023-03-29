package cc.kafuu.bilidownload.fragment.personal;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;


import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

import cc.kafuu.bilidownload.PersonalActivity;
import cc.kafuu.bilidownload.R;
import cc.kafuu.bilidownload.adapter.VideoListAdapter;
import cc.kafuu.bilidownload.bilibili.account.BiliSpace;
import cc.kafuu.bilidownload.model.MyVideoViewModel;


public class MyVideoFragment extends Fragment implements VideoListAdapter.VideoListItemClickedListener {
    private static final String TAG = "CartoonFragment";

    private MyVideoViewModel mModel;

    private View mRootView = null;

    private boolean mLoading = false;

    private SwipeRefreshLayout mSwipeRefreshLayout;
    private RecyclerView mVideoList;
    private TextView mNoRecordTip;


    public static MyVideoFragment newInstance(long accountId) {
        MyVideoFragment fragment = new MyVideoFragment();
        Bundle args = new Bundle();
        args.putLong("accountId", accountId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mModel = new ViewModelProvider(this).get(MyVideoViewModel.class);
    }

    @SuppressLint("NotifyDataSetChanged")
    @Override
    public View onCreateView(@NotNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        if (mRootView != null) {
            ((ViewGroup) container.getParent()).removeView(mRootView);
        } else {
            mRootView = inflater.inflate(R.layout.fragment_videos, container, false);
        }

        findView();
        initView();

        if (mModel.firstLoad) {
            mModel.firstLoad = false;
            loadVideo(false);
        } else {
            ((VideoListAdapter) Objects.requireNonNull(mVideoList.getAdapter())).setRecords(mModel.records).notifyDataSetChanged();
        }

        return mRootView;
    }

    private void findView() {
        mSwipeRefreshLayout = mRootView.findViewById(R.id.swipeRefreshLayout);
        mVideoList = mRootView.findViewById(R.id.videoList);
        mNoRecordTip = mRootView.findViewById(R.id.noRecordTip);
    }

    private void initView() {
        mSwipeRefreshLayout.setOnRefreshListener(() -> loadVideo(false));

        mVideoList.setLayoutManager(new LinearLayoutManager(getContext()));
        mVideoList.setAdapter(new VideoListAdapter(this, mModel.records));

        //当监听到列表快拉到尾部时加载新的记录数据
        mVideoList.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);

                LinearLayoutManager linearLayoutManager =  (LinearLayoutManager) mVideoList.getLayoutManager();

                assert linearLayoutManager != null;
                int total = linearLayoutManager.getItemCount();
                int lastVisible = linearLayoutManager.findLastVisibleItemPosition();
                Log.d(TAG, "onScrolled: " + total + "-" + lastVisible);

                if (mModel.hasMore && total < lastVisible + 10) {
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
            mModel.page = 1;
            mModel.hasMore = true;
            mModel.records.clear();
        }

        assert getArguments() != null;
        BiliSpace.getSpaceVideos(getArguments().getLong("accountId"), mModel.page, 0, new BiliSpace.GetSpaceVideosCallback() {

            @SuppressLint("NotifyDataSetChanged")
            @Override
            public void completed(List<BiliSpace.VideoRecord> records) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    mLoading = false;
                    ++mModel.page;

                    if (!loadMore) {
                        mSwipeRefreshLayout.setRefreshing(false);
                        ((VideoListAdapter) Objects.requireNonNull(mVideoList.getAdapter())).clearRecord();
                    }

                    if (records.size() == 0) {
                        mModel.hasMore = false;
                        return;
                    }

                    for (BiliSpace.VideoRecord record : records) {
                        VideoListAdapter.VideoRecord videoRecord = new VideoListAdapter.VideoRecord();

                        videoRecord.info = record.description;
                        videoRecord.cover = record.pic;
                        videoRecord.title = record.title;
                        videoRecord.videoId = record.bvid;

                        mModel.records.add(videoRecord);

                    }

                    ((VideoListAdapter) Objects.requireNonNull(mVideoList.getAdapter())).setRecords(mModel.records).notifyDataSetChanged();
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

                    mNoRecordTip.setText(message);
                });
                updateTip();
            }
        });

    }

    @Override
    public void onVideoListItemClicked(VideoListAdapter.VideoRecord record) {
        if (requireActivity().isDestroyed()) {
            return;
        }

        requireActivity().setResult(PersonalActivity.ResultCodeVideoClicked, new Intent().putExtra("video_id", record.videoId));
        requireActivity().finish();
    }

    private void updateTip() {
        new Handler(Looper.getMainLooper()).post(() -> mNoRecordTip.setVisibility((Objects.requireNonNull(mVideoList.getAdapter()).getItemCount() == 0) ? View.VISIBLE : View.GONE));
    }
}