package cc.kafuu.bilidownload.fragment;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import org.jetbrains.annotations.NotNull;


import java.io.IOException;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cc.kafuu.bilidownload.BiliLoginActivity;
import cc.kafuu.bilidownload.R;
import cc.kafuu.bilidownload.adapter.VideoParseResultAdapter;
import cc.kafuu.bilidownload.bilibili.Bili;
import cc.kafuu.bilidownload.bilibili.video.BiliVideo;
import cc.kafuu.bilidownload.utils.DialogTools;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link VideoParserFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class VideoParserFragment extends Fragment {
    private Handler mHandler;

    private View mRootView = null;

    private CardView mLoginBiliCard;
    private TextView mUserName;
    private TextView mUserSign;

    private EditText mVideoAddress;
    private Button mParsingVideo;

    private CardView mVideoInfoCard;
    private ImageView mUserFace;
    private TextView mVideoTitle;
    private TextView mVideoDescribe;

    private RecyclerView mVideoInfoList;


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
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
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

        loadUserInfo();

        return mRootView;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 100 && resultCode == 0 && Bili.biliAccount != null) {
            //登录成功 显示用户头像昵称和签名
            loadUserInfo();
        }
    }

    private void loadUserInfo() {
        if (Bili.biliAccount != null && Bili.biliCookie != null) {
            Glide.with(Objects.requireNonNull(getContext())).load(Bili.biliAccount.getFace()).placeholder(R.drawable.ic_2233).into(mUserFace);
            mUserName.setText(Bili.biliAccount.getUserName());
            mUserSign.setText(Bili.biliAccount.getSign());
        }
    }

    private void findView() {
        mLoginBiliCard = mRootView.findViewById(R.id.loginBiliCard);
        mUserName = mRootView.findViewById(R.id.userName);
        mUserSign = mRootView.findViewById(R.id.userSign);

        mVideoAddress = mRootView.findViewById(R.id.videoAddress);
        mParsingVideo = mRootView.findViewById(R.id.parsingVideo);

        mVideoInfoCard = mRootView.findViewById(R.id.videoInfoCard);
        mUserFace = mRootView.findViewById(R.id.userFace);
        mVideoTitle = mRootView.findViewById(R.id.videoTitle);
        mVideoDescribe = mRootView.findViewById(R.id.videoDescribe);

        mVideoInfoList = mRootView.findViewById(R.id.videoInfoList);
    }

    private void initView() {
        mLoginBiliCard.setOnClickListener(v -> onLoginBiliCardClick());

        mVideoAddress.setOnKeyListener((v, keyCode, event) -> {
            if(keyCode == KeyEvent.KEYCODE_ENTER) {
                onParsingVideo();
            }
            return keyCode == KeyEvent.KEYCODE_ENTER;
        });
        mParsingVideo.setOnClickListener(v -> onParsingVideo());

        mVideoInfoCard.setVisibility(View.GONE);

        mVideoInfoList.setLayoutManager(new LinearLayoutManager(getContext()));
    }

    /**
     * 询问用户是否退出登录
     * */
    private void onLoginBiliCardClick() {
        if (Bili.biliAccount == null) {
            startActivityForResult(new Intent(getContext(), BiliLoginActivity.class), 100);
            return;
        }
        DialogTools.confirm(getContext(), null, getString(R.string.exit_login_confirm), (dialog, which) -> onExitLogin(), null);
    }

    /**
     * 用户确认退出登录
     * */
    private void onExitLogin() {
        Bili.requestExitLogin(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                Toast.makeText(getContext(), R.string.exit_login_failure, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {

                ResponseBody body = response.body();
                if (body == null) {
                    mHandler.post(() -> Toast.makeText(getContext(), R.string.exit_login_failure, Toast.LENGTH_SHORT).show());
                    return;
                }

                JsonObject jsonObject = new Gson().fromJson(body.string(), JsonObject.class);
                Log.d("onExitLogin", jsonObject.toString());
                if (jsonObject.get("code").getAsInt() != 0) {
                    mHandler.post(() -> Toast.makeText(getContext(), R.string.exit_login_failure, Toast.LENGTH_SHORT).show());
                    return;
                }

                Bili.updateHeaders(null);
                Bili.biliAccount = null;

                mHandler.post(() -> {
                    Glide.with(getContext()).load(R.drawable.ic_2233).into(mUserFace);
                    mUserName.setText(R.string.login_tips_1);
                    mUserSign.setText(R.string.login_tips_2);
                });
            }
        });
    }

    /**
     * 请求解析视频时调用此函数暂时禁用解析功能
     * */
    private void changeEnableStatus(boolean enable) {
        mVideoInfoList.setEnabled(enable);
        mVideoAddress.setEnabled(enable);
        mParsingVideo.setEnabled(enable);
    }

    /**
     * 用户开始尝试解析视频地址获得视频
     * */
    private void onParsingVideo() {
        String videoId = getInputId();
        if (videoId == null) {
            Toast.makeText(getContext(), getText(R.string.video_address_format_incorrect), Toast.LENGTH_SHORT).show();
            return;
        }

        BiliVideo.VideoParsingCallback callback = new BiliVideo.VideoParsingCallback() {
            @Override
            public void onCompleted(BiliVideo biliVideos) {
                Log.d("VideoParserFragment.onParsingVideo->onComplete", biliVideos.toString());
                mHandler.post(() -> parsingVideoCompleted(biliVideos, null));
            }

            @Override
            public void onFailure(String message) {
                Log.d("[Failure]VideoParserFragment.onParsingVideo->onFailure", message);
                mHandler.post(() -> parsingVideoCompleted(null, message));
            }
        };

        changeEnableStatus(false);
        BiliVideo.fromVideoId(videoId, callback);
    }

    /**
     * 取得用户要获取的视频的BV号或AV号
     * */
    private String getInputId() {
        Editable address = mVideoAddress.getText();
        if (address == null) {
            return null;
        }

        Pattern pattern = Pattern.compile("(BV.{10})|((av|ep|ss)\\d*)");
        Matcher matcher = pattern.matcher(address);

        if (!matcher.find()) {
            return null;
        }

        return matcher.group();
    }

    /**
     * 视频地址解析完成
     * 显示解析的信息
     * */
    private void parsingVideoCompleted(BiliVideo biliVideos, String message) {
        changeEnableStatus(true);
        if (biliVideos == null) {
            DialogTools.notify(getContext(), getString(R.string.error), message);
            return;
        }

        mVideoInfoCard.setVisibility(View.VISIBLE);
        mVideoTitle.setText(biliVideos.getTitle());
        mVideoDescribe.setText(biliVideos.getDesc());

        mVideoInfoList.setAdapter(new VideoParseResultAdapter(getActivity(), biliVideos));
    }

}