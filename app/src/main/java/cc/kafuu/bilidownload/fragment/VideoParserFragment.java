package cc.kafuu.bilidownload.fragment;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;

import org.jetbrains.annotations.NotNull;
import org.json.JSONException;


import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cc.kafuu.bilidownload.BiliLoginActivity;
import cc.kafuu.bilidownload.PersonalActivity;
import cc.kafuu.bilidownload.R;
import cc.kafuu.bilidownload.adapter.VideoParseResultAdapter;
import cc.kafuu.bilidownload.bilibili.Bili;
import cc.kafuu.bilidownload.bilibili.account.BiliAccount;
import cc.kafuu.bilidownload.bilibili.video.BiliVideo;
import cc.kafuu.bilidownload.model.VideoParserViewModel;
import cc.kafuu.bilidownload.utils.DialogTools;
import cc.kafuu.bilidownload.utils.ApplicationTools;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;
import okhttp3.ResponseBody;


public class VideoParserFragment extends Fragment {
    private static final String TAG = "VideoParserFragment";

    private final Handler mHandler;

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
    private TextView mVideoDownloadNotAllowed;

    private RecyclerView mVideoInfoList;

    private VideoParserViewModel mModel;

    ActivityResultLauncher<Intent> mLoginActivityResultLaunch;
    ActivityResultLauncher<Intent> mPersonalActivityResultLaunch;

    public VideoParserFragment() {
        mHandler = new Handler(Looper.getMainLooper());
    }


    public static VideoParserFragment newInstance() {
        Log.d(TAG, "newInstance");
        VideoParserFragment fragment = new VideoParserFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");
    }

    @Override
    public void onResume() {
        super.onResume();

        //??????????????????????????????????????????
        final CharSequence pasteText = ApplicationTools.paste(requireContext());
        if (pasteText != null) {
            //???????????????????????????????????????Id
            String pasteId = getInputId(pasteText.toString());
            if (pasteId == null) {
                //????????????????????????????????????Id
                return;
            }

            //???????????????????????????????????????????????????????????????
            if (mVideoAddress.getText() != null) {
                String curId = getInputId(mVideoAddress.getText().toString());
                if (curId != null && curId.equals(pasteId)) {
                    //???????????????????????????????????????
                    return;
                }
            }

            //???????????????????????????????????????????????????
            SharedPreferences sharedPreferences = requireContext().getSharedPreferences("app", Context.MODE_PRIVATE);
            if (sharedPreferences == null) {
                return;
            }
            String lastPasteId = sharedPreferences.getString("pasteId", null);
            if (lastPasteId != null) {
                Log.d(TAG, "onResume: lastPasteId " + lastPasteId);
                if (lastPasteId.equals(pasteId)) {
                    return;
                }
            }
            sharedPreferences.edit().putString("pasteId", pasteId).apply();

            DialogTools.confirm(getContext(), null, getText(R.string.confirm_use_contents_paste_board), (dialogInterface, i) -> mVideoAddress.setText(pasteText), null);
        }
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
            Log.d(TAG, "onCreateView: reuse");
        } else {
            mRootView = inflater.inflate(R.layout.fragment_video_parser, container, false);
            Log.d(TAG, "onCreateView: new view");
        }

        mModel = new ViewModelProvider(this).get(VideoParserViewModel.class);


        findView();
        initView();
        initLauncher();

        loadUserInfo();

        return mRootView;
    }

    private void loadUserInfo() {
        final String cookie = CookieManager.getInstance().getCookie("https://m.bilibili.com");
        if (cookie != null && (Bili.biliAccount == null || Bili.biliCookie == null)) {
            mLoginBiliCard.setEnabled(false);

            Thread thread = new Thread(() -> {
                Bili.biliAccount = BiliAccount.getAccount(cookie);

                Log.d(TAG, Bili.biliAccount == null ? "????????????Cookie" : "????????????: " + Bili.biliAccount.getUserName());

                Bili.updateHeaders(cookie);
                mHandler.post(() -> {
                    mLoginBiliCard.setEnabled(true);
                    if (Bili.biliAccount == null) {
                        return;
                    }
                    Glide.with(requireContext()).load(Bili.biliAccount.getFace()).placeholder(R.drawable.ic_2233).into(mUserFace);
                    mUserName.setText(Bili.biliAccount.getUserName());
                    if (Bili.biliAccount.getSign() == null || Bili.biliAccount.getSign().length() == 0) {
                        mUserSign.setText(getText(R.string.no_sign));
                    } else {
                        mUserSign.setText(Bili.biliAccount.getSign());
                    }
                });
            });
            thread.start();

        } else if (Bili.biliAccount != null && Bili.biliCookie != null) {
            Glide.with(requireContext()).load(Bili.biliAccount.getFace()).placeholder(R.drawable.ic_2233).into(mUserFace);
            mUserName.setText(Bili.biliAccount.getUserName());
            if (Bili.biliAccount.getSign() == null || Bili.biliAccount.getSign().length() == 0) {
                mUserSign.setText(getText(R.string.no_sign));
            } else {
                mUserSign.setText(Bili.biliAccount.getSign());
            }
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
        mVideoDownloadNotAllowed = mRootView.findViewById(R.id.videoDownloadNotAllowed);

        mVideoInfoList = mRootView.findViewById(R.id.videoInfoList);
    }

    private void initView() {
        mLoginBiliCard.setOnClickListener(v -> onLoginBiliCardClick());

        mVideoAddress.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_ENTER) {
                parsingVideoAddress();
            }
            return keyCode == KeyEvent.KEYCODE_ENTER;
        });
        mParsingVideo.setOnClickListener(v -> parsingVideoAddress());

        mVideoInfoCard.setVisibility(View.GONE);

        mVideoInfoList.setLayoutManager(new LinearLayoutManager(getContext()));

        //?????????????????????????????????
        mVideoInfoList.setNestedScrollingEnabled(false);
        mVideoInfoList.setHasFixedSize(true);
        //?????????????????????
        mVideoInfoList.setFocusable(false);

        if (mModel.biliVideo != null) {
            parsingVideoCompleted(mModel.biliVideo, null);
        }


    }

    private void initLauncher() {
        //???????????????
        mLoginActivityResultLaunch = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == BiliLoginActivity.ResultCodeOk && Bili.biliAccount != null) {
                //???????????? ?????????????????????????????????
                loadUserInfo();
            }
        });

        //?????????????????????
        mPersonalActivityResultLaunch =  registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == PersonalActivity.ResultCodeLogout) {
                onExitLogin();
                return;
            }

            if (result.getResultCode() == PersonalActivity.ResultCodeVideoClicked) {
                assert result.getData() != null;
                mVideoAddress.setText(result.getData().getStringExtra("video_id"));
                parsingVideoAddress();
            }
        });
    }

    /**
     * ???????????????????????????????????????
     */
    private void onLoginBiliCardClick() {
        if (Bili.biliAccount == null) {
            BiliLoginActivity.actionStartForResult(getContext(), mLoginActivityResultLaunch);
            return;
        }

        PersonalActivity.actionStartForResult(getContext(), mPersonalActivityResultLaunch);
    }

    /**
     * ????????????????????????
     */
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

                String jsonData = body.string();

                Log.d(TAG, "onResponse: " + jsonData);

                try {
                    JsonPrimitive jsonPrimitive = new Gson().fromJson(jsonData, JsonPrimitive.class);
                    if (jsonPrimitive.isJsonObject()) {
                        Log.d(TAG, "onResponse: onExitLogin " + jsonPrimitive);
                        if (jsonPrimitive.getAsJsonObject().get("code").getAsInt() != 0) {
                            mHandler.post(() -> Toast.makeText(getContext(), R.string.exit_login_failure, Toast.LENGTH_SHORT).show());
                            return;
                        }
                    }
                } catch (JsonParseException e) {
                    e.printStackTrace();
                }


                Bili.updateHeaders(null);
                Bili.biliAccount = null;

                mHandler.post(() -> {
                    Glide.with(requireContext()).load(R.drawable.ic_2233).into(mUserFace);
                    mUserName.setText(R.string.login_tips_1);
                    mUserSign.setText(R.string.login_tips_2);
                });
            }
        });
    }

    /**
     * ????????????????????????????????????????????????????????????
     */
    private void changeEnableStatus(boolean enable) {
        mVideoInfoList.setEnabled(enable);
        mVideoAddress.setEnabled(enable);
        mParsingVideo.setEnabled(enable);
    }

    /**
     * ????????????????????????????????????????????????
     */
    private void parsingVideoAddress() {
        String videoId = null;

        if (mVideoAddress.getText() != null) {
            String addressStr = mVideoAddress.getText().toString();

            if (addressStr.contains("https://b23.tv/")) {
                Pattern pattern = Pattern.compile("https://b23.tv/.*");
                Matcher matcher = pattern.matcher(addressStr);

                if (!matcher.find()) {
                    Toast.makeText(getContext(), getText(R.string.video_address_format_incorrect), Toast.LENGTH_SHORT).show();
                    return;
                }

                Bili.redirection(matcher.group(), new Bili.RedirectionCallback() {
                    @Override
                    public void onFailure(String message) {
                        mHandler.post(() -> Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show());
                    }

                    @Override
                    public void onCompleted(String location) {
                        mHandler.post(() -> {
                            mVideoAddress.setText(getInputId(location));
                            parsingVideoAddress();
                        });
                    }
                });
                return;
            }

            videoId = getInputId(addressStr);
        }

        if (videoId == null) {
            Toast.makeText(getContext(), getText(R.string.video_address_format_incorrect), Toast.LENGTH_SHORT).show();
            return;
        }

        BiliVideo.VideoParsingCallback callback = new BiliVideo.VideoParsingCallback() {
            @Override
            public void completed(BiliVideo biliVideos) {
                Log.d(TAG, "onCompleted: " + biliVideos.toString());
                mHandler.post(() -> parsingVideoCompleted(biliVideos, null));
            }

            @Override
            public void failure(String message) {
                Log.d(TAG, "onFailure: onFailure " + message);
                mHandler.post(() -> parsingVideoCompleted(null, message));
            }
        };

        changeEnableStatus(false);
        BiliVideo.fromVideoId(videoId, callback);
    }

    /**
     * ?????????????????????????????????BV??????AV???
     */
    private String getInputId(String address) {
        Pattern pattern = Pattern.compile("(BV.{10})|((av|ep|ss)\\d*)");
        Matcher matcher = pattern.matcher(address);

        if (!matcher.find()) {
            return null;
        }

        return matcher.group();
    }

    /**
     * ????????????????????????
     * ?????????????????????
     */
    private void parsingVideoCompleted(BiliVideo biliVideos, String message) {
        changeEnableStatus(true);
        if (biliVideos == null) {
            DialogTools.notify(getContext(), getString(R.string.error), message);
            return;
        }

        mModel.biliVideo = biliVideos;

        mVideoInfoCard.setVisibility(View.VISIBLE);
        mVideoTitle.setText(biliVideos.getTitle());
        mVideoDescribe.setText(biliVideos.getDesc());

        mVideoDownloadNotAllowed.setVisibility(biliVideos.allowDownload() ? View.GONE : View.VISIBLE);


        mVideoInfoList.setAdapter(new VideoParseResultAdapter(getActivity(), biliVideos));
    }

}