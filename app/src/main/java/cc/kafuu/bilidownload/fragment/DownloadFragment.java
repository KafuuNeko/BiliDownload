package cc.kafuu.bilidownload.fragment;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;

import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

import cc.kafuu.bilidownload.R;
import cc.kafuu.bilidownload.adapter.DownloadRecordAdapter;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link DownloadFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class DownloadFragment extends Fragment {
    private Handler mHandler;

    private View mRootView = null;

    private RecyclerView mDownloadRecordList;
    private TextView mNoDownloadRecordTip;

    private DownloadRecordAdapter mVideoDownloadRecordAdapter = null;

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (mVideoDownloadRecordAdapter != null) {
                mVideoDownloadRecordAdapter.reloadRecords();
            }
        }
    };

    public DownloadFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @return A new instance of fragment DownloadFragment.
     */
    // TODO: Rename and change types and number of parameters
    public static DownloadFragment newInstance() {
        DownloadFragment fragment = new DownloadFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mHandler = new Handler(Looper.getMainLooper());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Objects.requireNonNull(getContext()).unregisterReceiver(mBroadcastReceiver);
    }

    @Override
    public View onCreateView(@NotNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        if (mRootView != null) {
            ((ViewGroup) container.getParent()).removeView(mRootView);
        } else {
            mRootView = inflater.inflate(R.layout.fragment_download, container, false);
        }

        findView();
        initView();

        return mRootView;
    }

    private void findView() {
        mDownloadRecordList = mRootView.findViewById(R.id.downloadRecordList);
        mNoDownloadRecordTip = mRootView.findViewById(R.id.noDownloadRecordTip);
    }

    private void initView() {
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getContext(), RecyclerView.VERTICAL, true);
        linearLayoutManager.setStackFromEnd(true);
        mDownloadRecordList.setLayoutManager(linearLayoutManager);

        IntentFilter filter = new IntentFilter();
        filter.addAction("download.task.create");
        Objects.requireNonNull(getContext()).registerReceiver(mBroadcastReceiver, filter);

        mVideoDownloadRecordAdapter = new DownloadRecordAdapter(Objects.requireNonNull(getActivity()));
        mVideoDownloadRecordAdapter.setItemCountChangeListener(this::showOrHideTip);

        mDownloadRecordList.setAdapter(mVideoDownloadRecordAdapter);

        showOrHideTip();
    }

    private void showOrHideTip() {
        if (mVideoDownloadRecordAdapter == null) {
            return;
        }

        mHandler.post(() -> mNoDownloadRecordTip.setVisibility(
                mVideoDownloadRecordAdapter.getItemCount() == 0 ? View.VISIBLE : View.GONE));
    }

}