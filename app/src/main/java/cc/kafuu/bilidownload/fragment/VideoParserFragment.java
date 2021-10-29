package cc.kafuu.bilidownload.fragment;

import android.app.AlertDialog;
import android.os.Bundle;

import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import org.jetbrains.annotations.NotNull;


import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    private String getInputId() {
        Editable address = mVideoAddress.getText();
        if (address == null) {
            return null;
        }

        Pattern pattern = Pattern.compile("(BV.{10})|(av\\d*)");
        Matcher matcher = pattern.matcher(address);

        if (!matcher.find()) {
            return null;
        }

        return matcher.group();
    }

    private void onParsingVideo() {
        String videoId = getInputId();
        if (videoId == null) {
            Toast.makeText(getContext(), getText(R.string.video_address_format_incorrect), Toast.LENGTH_SHORT).show();
            return;
        }

        VideoParsingCallback callback = new VideoParsingCallback() {
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
        };

        changeEnableStatus(false);
        if (videoId.contains("BV")) {
            BiliVideo.fromBv(videoId, callback);
        } else {
            BiliVideo.fromAv(videoId, callback);
        }
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

        mVideoInfoList.setAdapter(new VideoParseResultAdapter(getActivity(), biliVideos));
    }


}