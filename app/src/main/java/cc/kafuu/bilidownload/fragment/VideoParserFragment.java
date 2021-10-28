package cc.kafuu.bilidownload.fragment;

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


import cc.kafuu.bilidownload.R;
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
                Log.d("Bili Videos", biliVideos.toString());
            }

            @Override
            public void onFailure(String message) {
                Log.d("Bili onFailure", message);
            }
        });


//        new BiliVideoResource(
//                32,
//                "https://www.bilibili.com/video/BV1A4411N7Kb",
//                "https://xy117x65x61x80xy.mcdn.bilivideo.cn:4483/upgcxcode/61/01/97160161/97160161_nb2-1-64.flv?e=ig8euxZM2rNcNbRjhzdVhwdlhWTzhwdVhoNvNC8BqJIzNbfqXBvEqxTEto8BTrNvN0GvT90W5JZMkX_YN0MvXg8gNEV4NC8xNEV4N03eN0B5tZlqNxTEto8BTrNvNeZVuJ10Kj_g2UB02J0mN0B5tZlqNCNEto8BTrNvNC7MTX502C8f2jmMQJ6mqF2fka1mqx6gqj0eN0B599M=&uipk=5&nbs=1&deadline=1635357326&gen=playurlv2&os=mcdn&oi=2043427825&trid=00010179a3481bbf441088ec116fe3280b54u&platform=pc&upsig=17a3c1b346ee014a23225d77be813cf0&uparams=e,uipk,nbs,deadline,gen,os,oi,trid,platform&mcdnid=9001189&mid=0&bvc=vod&nettype=0&orderid=0,3&agrr=1&logo=A0000100",
//                "test",
//                "flv",
//                "mp4"
//        ).download(getContext().getDataDir() + "/test.mp4", new ResourceDownloadCallback() {
//            @Override
//            public void onStatus(int current, int contentLength) {
//                //Log.d()
//            }
//
//            @Override
//            public void onComplete(File file) {
//                Log.d("onComplete", file.getPath());
//            }
//
//            @Override
//            public void onFailure(String message) {
//                Log.d("onFailure", message);
//            }
//        });
    }

    private void parsingVideoComplete(BiliVideo biliVideos, String message) {

    }
}