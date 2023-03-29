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
import android.widget.Toast;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

import cc.kafuu.bilidownload.PersonalActivity;
import cc.kafuu.bilidownload.R;
import cc.kafuu.bilidownload.adapter.VideoListAdapter;
import cc.kafuu.bilidownload.bilibili.account.BiliHistory;
import cc.kafuu.bilidownload.model.HistoryViewModel;

public class HistoryFragment extends Fragment implements VideoListAdapter.VideoListItemClickedListener {
    private static final String TAG = "HistoryFragment";

    private HistoryViewModel mModel;

    private boolean mLoading;

    private View mRootView = null;

    private SwipeRefreshLayout mSwipeRefreshLayout;
    private RecyclerView mHistoryList;
    private TextView mNoRecordTip;


    public HistoryFragment() {
        // Required empty public constructor
    }

    public static HistoryFragment newInstance() {
        HistoryFragment fragment = new HistoryFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mModel = new ViewModelProvider(this).get(HistoryViewModel.class);
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
            loadHistory(false);
        } else {
            ((VideoListAdapter) Objects.requireNonNull(mHistoryList.getAdapter())).setRecords(mModel.records).notifyDataSetChanged();
        }


        return mRootView;
    }

    private void findView() {
        mSwipeRefreshLayout = mRootView.findViewById(R.id.swipeRefreshLayout);
        mHistoryList = mRootView.findViewById(R.id.videoList);
        mNoRecordTip = mRootView.findViewById(R.id.noRecordTip);
    }

    private void initView() {
        mSwipeRefreshLayout.setOnRefreshListener(() -> loadHistory(false));

        mHistoryList.setLayoutManager(new LinearLayoutManager(getContext()));
        mHistoryList.setAdapter(new VideoListAdapter(this, mModel.records));

        //当监听到列表快拉到尾部时加载新的历史记录数据
        mHistoryList.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);

                LinearLayoutManager linearLayoutManager =  (LinearLayoutManager) mHistoryList.getLayoutManager();

                assert linearLayoutManager != null;
                int total = linearLayoutManager.getItemCount();
                int lastVisible = linearLayoutManager.findLastVisibleItemPosition();
                Log.d(TAG, "onScrolled: " + total + "-" + lastVisible);

                if (mModel.nextCursor != null && total < lastVisible + 10) {
                    loadHistory(true);
                }
            }
        });

    }

    private void loadHistory(boolean loadMore) {
        if (mLoading) {
            if (!loadMore) {
                mSwipeRefreshLayout.setRefreshing(false);
            }
            return;
        }

        mLoading = true;

        if (!loadMore) {
            mSwipeRefreshLayout.setRefreshing(true);
            mModel.nextCursor = null;
            mModel.records.clear();
        }

        BiliHistory.getHistory(mModel.nextCursor, new BiliHistory.GetHistoryCallback() {
            @SuppressLint("NotifyDataSetChanged")
            @Override
            public void completed(final List<BiliHistory.Record> records, BiliHistory.Cursor nextCursor) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    mLoading = false;
                    mModel.nextCursor = nextCursor;

                    if (!loadMore) {
                        mSwipeRefreshLayout.setRefreshing(false);
                        ((VideoListAdapter) Objects.requireNonNull(mHistoryList.getAdapter())).clearRecord();
                    }

                    for (BiliHistory.Record record : records) {
                        VideoListAdapter.Record videoRecord = new VideoListAdapter.Record();

                        videoRecord.info = record.author;
                        videoRecord.cover = record.cover;
                        videoRecord.title = record.title;
                        videoRecord.videoId = record.bv;

                        mModel.records.add(videoRecord);
                    }

                    ((VideoListAdapter) Objects.requireNonNull(mHistoryList.getAdapter())).setRecords(mModel.records).notifyDataSetChanged();

                    //加载的数量不足20且还有未加载完的记录则继续加载更多历史记录
                    if (records.size() < 20 && nextCursor != null) {
                        loadHistory(true);
                    }

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

    /**
     * 视频列表项目被点击
     * */
    @Override
    public void onVideoListItemClicked(VideoListAdapter.Record record) {
        if (requireActivity().isDestroyed()) {
            return;
        }

        requireActivity().setResult(PersonalActivity.ResultCodeVideoClicked, new Intent().putExtra("video_id", record.videoId));
        requireActivity().finish();
    }

    private void updateTip() {
        new Handler(Looper.getMainLooper()).post(() -> mNoRecordTip.setVisibility((Objects.requireNonNull(mHistoryList.getAdapter()).getItemCount() == 0) ? View.VISIBLE : View.GONE));
    }
}