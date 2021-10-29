package cc.kafuu.bilidownload.fragment;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;

import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

import cc.kafuu.bilidownload.R;
import cc.kafuu.bilidownload.adapter.VideoDownloadRecordAdapter;
import cc.kafuu.bilidownload.utils.RecordDatabase;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link DownloadFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class DownloadFragment extends Fragment {

    private View mRootView = null;

    private RecyclerView mDownloadRecordList;

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
    }

    private void initView() {
        mDownloadRecordList.setLayoutManager(new LinearLayoutManager(getContext()));

        IntentFilter filter = new IntentFilter();
        filter.addAction("notice.download.completed");
        Objects.requireNonNull(getContext()).registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                loadRecords();
            }
        }, filter);

        loadRecords();
    }

    private void loadRecords() {
        mDownloadRecordList.setAdapter(new VideoDownloadRecordAdapter(getContext()));
    }
}