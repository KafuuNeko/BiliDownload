package cc.kafuu.bilidownload.fragment;

import android.app.AlertDialog;
import android.os.Bundle;

import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import org.jetbrains.annotations.NotNull;


import cc.kafuu.bilidownload.R;
import cc.kafuu.bilidownload.adapter.VideoParseResultAdapter;
import cc.kafuu.bilidownload.bilibili.VideoParsingCallback;
import cc.kafuu.bilidownload.bilibili.video.BiliVideo;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link VideoParserFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class VideoParserFragment extends Fragment {
    private Handler mHandler;

    private View mRootView = null;
    private RecyclerView mVideoInfoList;
    private EditText mVideoAddress;
    private Button mParsingVideo;

    private CardView mVideoInfoCard;
    private TextView mVideoTitle;
    private TextView mVideoDescribe;


    public VideoParserFragment() {
        // Required empty public constructor
        mHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @return A new instance of fragment VideoParserFragment.
     */
    // TODO: Rename and change types and number of parameters
    public static VideoParserFragment newInstance() {
        VideoParserFragment fragment = new VideoParserFragment();
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
            mRootView = inflater.inflate(R.layout.fragment_video_parser, container, false);
        }

        findView();
        initView();

        return mRootView;
    }

    private void findView() {
        mVideoInfoList = mRootView.findViewById(R.id.videoInfoList);
        mVideoAddress = mRootView.findViewById(R.id.videoAddress);
        mParsingVideo = mRootView.findViewById(R.id.parsingVideo);

        mVideoInfoCard = mRootView.findViewById(R.id.videoInfoCard);
        mVideoTitle = mRootView.findViewById(R.id.videoTitle);
        mVideoDescribe = mRootView.findViewById(R.id.videoDescribe);
    }

    private void initView() {
        mVideoInfoCard.setVisibility(View.GONE);

        mVideoAddress.setOnKeyListener((v, keyCode, event) -> {
            if(keyCode == KeyEvent.KEYCODE_ENTER) {
                onParsingVideo();
            }
            return keyCode == KeyEvent.KEYCODE_ENTER;
        });
        mParsingVideo.setOnClickListener(v -> onParsingVideo());

        mVideoInfoList.setLayoutManager(new LinearLayoutManager(getContext()));
    }

    private void changeEnableStatus(boolean enable) {
        mVideoInfoList.setEnabled(enable);
        mVideoAddress.setEnabled(enable);
        mParsingVideo.setEnabled(enable);
    }

    private void onParsingVideo() {
        changeEnableStatus(false);

        BiliVideo.fromBv(mVideoAddress.getText().toString(), new VideoParsingCallback() {
            @Override
            public void onComplete(BiliVideo biliVideos) {
                Log.d("VideoParserFragment.onParsingVideo->onComplete", biliVideos.toString());
                mHandler.post(() -> parsingVideoComplete(biliVideos, null));
            }

            @Override
            public void onFailure(String message) {
                Log.d("[Failure]VideoParserFragment.onParsingVideo->onFailure", message);
                mHandler.post(() -> parsingVideoComplete(null, message));
            }
        });

    }


    private void parsingVideoComplete(BiliVideo biliVideos, String message) {
        changeEnableStatus(true);
        if (biliVideos == null) {
            new AlertDialog.Builder(getContext()).setTitle(R.string.error).setMessage(message).show();
            return;
        }

        mVideoInfoCard.setVisibility(View.VISIBLE);
        mVideoTitle.setText(biliVideos.getTitle());
        mVideoDescribe.setText(biliVideos.getDesc());


        mVideoInfoList.setAdapter(new VideoParseResultAdapter(biliVideos));
    }
}