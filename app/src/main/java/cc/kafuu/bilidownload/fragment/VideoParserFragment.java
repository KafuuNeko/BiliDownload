package cc.kafuu.bilidownload.fragment;

import android.app.AlertDialog;
import android.os.Bundle;

import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;

import android.os.Handler;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;

import org.jetbrains.annotations.NotNull;


import java.io.File;
import java.util.List;

import cc.kafuu.bilidownload.R;
import cc.kafuu.bilidownload.bilibili.VideoParsingCallback;
import cc.kafuu.bilidownload.bilibili.video.BiliVideoPage;
import cc.kafuu.bilidownload.bilibili.video.BiliResource;
import cc.kafuu.bilidownload.bilibili.video.BiliVideo;
import cc.kafuu.bilidownload.bilibili.video.GetResourceCallback;
import cc.kafuu.bilidownload.bilibili.video.ResourceDownloadCallback;

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


    public VideoParserFragment() {
        // Required empty public constructor
        mHandler = new Handler();
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
    }

    private void initView() {
        mVideoAddress.setOnKeyListener((v, keyCode, event) -> {
            if(keyCode == KeyEvent.KEYCODE_ENTER) {
                onParsingVideo();
                return true;
            }
            return false;
        });
        mParsingVideo.setOnClickListener(v -> onParsingVideo());
    }

    private void changeEnableStatus(boolean enable) {
        mVideoInfoList.setEnabled(enable);
        mVideoAddress.setEnabled(enable);
        mParsingVideo.setEnabled(enable);

        
    }

    private void onParsingVideo() {
        changeEnableStatus(false);

        BiliVideo.fromBv("BV1A4411N7Kb", new VideoParsingCallback() {
            @Override
            public void onComplete(BiliVideo biliVideos) {
                Log.d("VideoParserFragment.onParsingVideo->onComplete", biliVideos.toString());
                parsingVideoComplete(biliVideos, null);
            }

            @Override
            public void onFailure(String message) {
                Log.d("[Failure]VideoParserFragment.onParsingVideo->onFailure", message);
                parsingVideoComplete(null, message);
            }
        });

    }

    private void parsingVideoComplete(BiliVideo biliVideos, String message) {

        if (biliVideos == null) {
            new AlertDialog.Builder(getContext()).setTitle("Error").setMessage(message).show();
            return;
        }

        for (BiliVideoPage page : biliVideos.getPages()) {
            Log.d("VideoParserFragment.onParsingVideo->parsingVideoComplete", "Page cid:" + page.getCid());

            page.getResource(new GetResourceCallback() {
                @Override
                public void onComplete(List<BiliResource> resources) {

                }

                @Override
                public void onFailure(String message) {
                    Log.d("[Failure]VideoParserFragment.onParsingVideo->parsingVideoComplete", message);
                }
            });
        }
    }
}