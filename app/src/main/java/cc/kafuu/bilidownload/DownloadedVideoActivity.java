package cc.kafuu.bilidownload;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.FileProvider;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentProvider;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.litepal.LitePal;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Objects;

import cc.kafuu.bilidownload.adapter.OperatorListAdapter;
import cc.kafuu.bilidownload.bilibili.BvConvert;
import cc.kafuu.bilidownload.database.VideoDownloadRecord;
import cc.kafuu.bilidownload.database.VideoInfo;
import cc.kafuu.bilidownload.jniexport.JniTools;
import cc.kafuu.bilidownload.model.DownloadedVideoViewModel;
import cc.kafuu.bilidownload.service.ExportService;
import cc.kafuu.bilidownload.utils.ApplicationTools;
import cc.kafuu.bilidownload.utils.DialogTools;
import cc.kafuu.bilidownload.utils.Utility;

public class DownloadedVideoActivity extends BaseActivity {
    private static final String TAG = "DownloadedVideoActivity";

    public static int ResultCodeDeleted = 0x01;

    private DownloadedVideoViewModel mModel;

    private ImageView mVideoPic;
    private TextView mVideoTitle;
    private TextView mPart;
    private TextView mDownloadTime;

    private TextView mVideoBv;
    private TextView mVideoAvid;
    private TextView mVideoCid;
    private TextView mVideoFormat;
    private TextView mCoderInfo;
    private TextView mVideoCodeRate;
    private TextView mVideoSize;
    private TextView mVideoDuration;

    private RecyclerView mViewOperator;
    private RecyclerView mVideoFormatOperator;

    private TextView mAudioFormat;
    private TextView mAudioCodeRate;
    private TextView mAudioSize;

    private RecyclerView mAudioOperator;
    private RecyclerView mVideoOtherOperator;

    private ActivityResultLauncher<Intent> mExportVideoLauncher;
    private ActivityResultLauncher<Intent> mExportAudioLauncher;

    public static void actionStartForResult(Context context, ActivityResultLauncher<Intent> launcher, long videoRecordId, long downloadRecordId) {
        if (ActivityCollector.contains(DownloadedVideoActivity.class)) {
            return;
        }

        Intent intent = new Intent(context, DownloadedVideoActivity.class);

        intent.putExtra("video_record_id", videoRecordId);
        intent.putExtra("download_record_id", downloadRecordId);

        launcher.launch(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_downloaded_video);

        mModel = new ViewModelProvider(this).get(DownloadedVideoViewModel.class);

        Intent intent = getIntent();

        if (mModel.downloadRecord == null) {
            mModel.downloadRecord = LitePal.find(VideoDownloadRecord.class, intent.getLongExtra("download_record_id", 0));
        }

        if (mModel.videoInfo == null) {
            mModel.videoInfo = LitePal.find(VideoInfo.class, intent.getLongExtra("video_record_id", 0));
        }

        mModel.convertVideoStatus.observe(this, this::onConvertVideoStatusChanged);
        mModel.extractingAudioStatus.observe(this, this::onExtractingAudioStatusChanged);

        findView();
        initView();
        initLauncher();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        //禁止在提取音频或转换视频封装格式时返回上一个Activity
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (mModel.convertVideoStatus.getValue() == DownloadedVideoViewModel.ConvertVideoStatus.Converting) {
                Toast.makeText(this, R.string.converting_video, Toast.LENGTH_SHORT).show();
                return true;
            }

            if (mModel.extractingAudioStatus.getValue() == DownloadedVideoViewModel.ExtractingAudioStatus.Extracting) {
                Toast.makeText(this, R.string.extracting_audio, Toast.LENGTH_SHORT).show();
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    private void findView() {
        mVideoPic = findViewById(R.id.videoPic);
        mVideoTitle = findViewById(R.id.videoTitle);
        mPart = findViewById(R.id.part);
        mDownloadTime = findViewById(R.id.downloadTime);

        mVideoBv = findViewById(R.id.videoBv);
        mVideoAvid = findViewById(R.id.videoAvid);
        mVideoCid = findViewById(R.id.videoCid);
        mVideoFormat = findViewById(R.id.videoFormat);
        mCoderInfo = findViewById(R.id.coderInfo);
        mVideoCodeRate = findViewById(R.id.videoCodeRate);
        mVideoSize = findViewById(R.id.videoSize);
        mVideoDuration = findViewById(R.id.videoDuration);

        mViewOperator = findViewById(R.id.viewOperator);
        mVideoFormatOperator = findViewById(R.id.videoFormatOperator);

        mAudioFormat = findViewById(R.id.audioFormat);
        mAudioCodeRate = findViewById(R.id.audioCodeRate);
        mAudioSize = findViewById(R.id.audioSize);

        mAudioOperator = findViewById(R.id.audioOperator);
        mVideoOtherOperator = findViewById(R.id.videoOtherOperator);
    }

    private void initView() {
        setSupportActionBar(findViewById(R.id.toolbar));
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);

        Glide.with(this).load(mModel.videoInfo.getPartPic()).placeholder(R.drawable.ic_2233).centerCrop().into(mVideoPic);
        mVideoTitle.setText(mModel.videoInfo.getVideoTitle());
        mPart.setText(mModel.videoInfo.getPartTitle());
        @SuppressLint("SimpleDateFormat") SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        mDownloadTime.setText(simpleDateFormat.format(mModel.downloadRecord.getStartTime()));

        reloadVideoInfo();

        mViewOperator.setLayoutManager(new LinearLayoutManager(this));
        mVideoFormatOperator.setLayoutManager(new LinearLayoutManager(this));
        mAudioOperator.setLayoutManager(new LinearLayoutManager(this));
        mVideoOtherOperator.setLayoutManager(new LinearLayoutManager(this));

        Utility.setRecyclerViewNested(mViewOperator);
        Utility.setRecyclerViewNested(mVideoFormatOperator);
        Utility.setRecyclerViewNested(mAudioOperator);
        Utility.setRecyclerViewNested(mVideoOtherOperator);

        reloadOperatorListAdapter();
        reloadAudioInfo();

    }

    private void initLauncher() {
        mExportVideoLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == Activity.RESULT_OK) {
                assert result.getData() != null;
                startExportService(mModel.downloadRecord.getSaveTo(), result.getData().getData());
            }
        });

        mExportAudioLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == Activity.RESULT_OK) {
                assert result.getData() != null;
                startExportService(mModel.downloadRecord.getAudio(), result.getData().getData());
            }
        });
    }

    /**
     * 启动资源导出服务
     * */
    private void startExportService(String source, Uri dest) {
        File sourceFile = new File(source);
        if (!sourceFile.exists()) {
            Toast.makeText(this, R.string.file_not_exist, Toast.LENGTH_SHORT).show();
            return;
        }

        Uri sourceUri = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                ? FileProvider.getUriForFile(this, BuildConfig.APPLICATION_ID + ".fileprovider", sourceFile)
                : Uri.fromFile(sourceFile);

        ExportService.export(this, sourceUri, dest);
    }

    private void reloadOperatorListAdapter() {
        OperatorListAdapter adapter;

        adapter = new OperatorListAdapter(this);
        adapter.addItem(R.string.view_video, v -> viewVideo(false));
        adapter.addItem(R.string.send_video, v -> viewVideo(true));
        adapter.addItem(R.string.export_video, v -> exportResource(true));
        mViewOperator.setAdapter(adapter);

        adapter = new OperatorListAdapter(this);
        adapter.addItem(R.string.view_audio, v -> viewAudio(false));
        adapter.addItem(R.string.send_audio, v -> viewAudio(true));
        adapter.addItem(R.string.export_audio, v -> exportResource(false));
        mAudioOperator.setAdapter(adapter);

        adapter = new OperatorListAdapter(this);

        String currentFormat = mModel.downloadRecord.getSaveTo().substring(mModel.downloadRecord.getSaveTo().lastIndexOf('.') + 1).toLowerCase();

        if (!currentFormat.equals("flv")) {
            boolean converting = (mModel.convertVideoStatus.getValue() == DownloadedVideoViewModel.ConvertVideoStatus.Converting && mModel.convertTo == DownloadedVideoViewModel.VideoFormat.FLV);
            adapter.addItem(R.string.convert_to_flv_format, v -> convertVideoFormatCheck("flv"), converting ? R.color.gray : null);
        }
        if (!currentFormat.equals("mp4")) {
            boolean converting = (mModel.convertVideoStatus.getValue() == DownloadedVideoViewModel.ConvertVideoStatus.Converting && mModel.convertTo == DownloadedVideoViewModel.VideoFormat.MP4);
            adapter.addItem(R.string.convert_to_mp4_format, v -> convertVideoFormatCheck("mp4"), converting ? R.color.gray : null);
        }
        if (!currentFormat.equals("mkv")) {
            boolean converting = (mModel.convertVideoStatus.getValue() == DownloadedVideoViewModel.ConvertVideoStatus.Converting && mModel.convertTo == DownloadedVideoViewModel.VideoFormat.MKV);
            adapter.addItem(R.string.convert_to_mkv_format, v -> convertVideoFormatCheck("mkv"), converting ? R.color.gray : null);
        }
        if (!currentFormat.equals("wmv")) {
            boolean converting = (mModel.convertVideoStatus.getValue() == DownloadedVideoViewModel.ConvertVideoStatus.Converting && mModel.convertTo == DownloadedVideoViewModel.VideoFormat.WMV);
            adapter.addItem(R.string.convert_to_wmv_format, v -> convertVideoFormatCheck("wmv"), converting ? R.color.gray : null);
        }

        mVideoFormatOperator.setAdapter(adapter);

        adapter = new OperatorListAdapter(this);
        adapter.addItem(R.string.delete_video, v -> deleteVideo());
        mVideoOtherOperator.setAdapter(adapter);
    }

    @SuppressLint("SetTextI18n")
    private void reloadVideoInfo() {
        mModel.mediaInfo = new Gson().fromJson(JniTools.getMediaInfo(mModel.downloadRecord.getSaveTo()), JsonObject.class);

        mVideoBv.setText(BvConvert.av2bv(String.valueOf(mModel.videoInfo.getAvid())));
        mVideoAvid.setText(String.valueOf(mModel.videoInfo.getAvid()));
        mVideoCid.setText(String.valueOf(mModel.videoInfo.getCid()));

        if (mModel.convertVideoStatus.getValue() != DownloadedVideoViewModel.ConvertVideoStatus.Converting) {
            mVideoFormat.setText((mModel.downloadRecord.getSaveTo().substring(mModel.downloadRecord.getSaveTo().lastIndexOf('.') + 1) + " " + mModel.videoInfo.getQualityDescription()).toUpperCase());
        } else {
            mVideoFormat.setText(R.string.converting_video);
        }

        mVideoSize.setText(Utility.getFileSizeString(new File(mModel.downloadRecord.getSaveTo()).length()));

        mVideoDuration.setText("null");
        mCoderInfo.setText("null");
        mVideoCodeRate.setText("null");

        if (mModel.mediaInfo.get("code").getAsInt() == 0) {
            mVideoDuration.setText(Utility.secondToTime(mModel.mediaInfo.get("second").getAsLong()));

            mVideoCodeRate.setText((mModel.mediaInfo.get("bit_rate").getAsLong() / 1024) + "kbps");

            if (mModel.mediaInfo.get("streams").isJsonArray()) {
                StringBuilder coders = new StringBuilder();
                for (JsonElement element : mModel.mediaInfo.get("streams").getAsJsonArray()) {
                    if (coders.length() != 0) {
                        coders.append(", ");
                    }
                    coders.append(element.getAsJsonObject().get("name").getAsString());
                }
                mCoderInfo.setText(coders);
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private void reloadAudioInfo() {
        if (mModel.extractingAudioStatus.getValue() == DownloadedVideoViewModel.ExtractingAudioStatus.Extracting) {
            mAudioFormat.setText(R.string.extracting_audio);
            mAudioCodeRate.setText(R.string.extracting_audio);
            mAudioSize.setText(R.string.extracting_audio);
            return;
        }

        File audioFile = (mModel.downloadRecord.getAudio() == null) ? null : new File(mModel.downloadRecord.getAudio());

        if (audioFile == null || !audioFile.exists()) {
            mAudioFormat.setText(R.string.audio_not_extract);
            mAudioSize.setText(R.string.audio_not_extract);
            mAudioCodeRate.setText(R.string.audio_not_extract);
        } else {
            mModel.audioInfo = new Gson().fromJson(JniTools.getMediaInfo(audioFile.getPath()), JsonObject.class);

            mAudioCodeRate.setText("null");
            if (mModel.audioInfo.get("code").getAsLong() == 0) {
                mAudioCodeRate.setText((mModel.audioInfo.get("bit_rate").getAsLong() / 1024) + "kbps");
            }

            mAudioFormat.setText(audioFile.getPath().substring(mModel.downloadRecord.getSaveTo().lastIndexOf('.') + 1).toUpperCase());
            mAudioSize.setText(Utility.getFileSizeString(audioFile.length()));
        }
    }

    /**
     * 发送或查看视频
     */
    private void viewVideo(boolean isSend) {
        if (mModel.convertVideoStatus.getValue() == DownloadedVideoViewModel.ConvertVideoStatus.Converting) {
            Toast.makeText(this, R.string.converting_video, Toast.LENGTH_SHORT).show();
            return;
        }

        File file = new File(mModel.downloadRecord.getSaveTo());
        if (!file.exists()) {
            Toast.makeText(this, R.string.file_not_exist, Toast.LENGTH_SHORT).show();
            return;
        }
        ApplicationTools.shareOrViewFile(this, mModel.videoInfo.getVideoTitle() + "-" + mModel.videoInfo.getPartTitle(), file, "*/*", !isSend);
    }

    /**
     * 提取音频
     */
    private void viewAudio(boolean isSend) {
        if (mModel.convertVideoStatus.getValue() == DownloadedVideoViewModel.ConvertVideoStatus.Converting) {
            Toast.makeText(this, R.string.converting_video, Toast.LENGTH_SHORT).show();
            return;
        }

        if (mModel.extractingAudioStatus.getValue() == DownloadedVideoViewModel.ExtractingAudioStatus.Extracting) {
            Toast.makeText(this, R.string.extracting_audio, Toast.LENGTH_SHORT).show();
            return;
        }

        if (checkAudioExist()) {
            assert mModel.downloadRecord.getAudio() != null;
            ApplicationTools.shareOrViewFile(this, mModel.videoInfo.getVideoTitle() + "-" + mModel.videoInfo.getPartTitle(), new File(mModel.downloadRecord.getAudio()), "*/*", !isSend);
        }
    }

    /**
     * 检查Audio是否已提取
     * 如果还未提取则开始音频提取并返回false
     * */
    private boolean checkAudioExist() {
        File saveTo = new File(mModel.downloadRecord.getSaveTo());
        if (!saveTo.exists()) {
            Toast.makeText(this, R.string.file_not_exist, Toast.LENGTH_SHORT).show();
            return false;
        }

        File audioFile = (mModel.downloadRecord.getAudio() == null) ? null : new File(mModel.downloadRecord.getAudio());

        if (audioFile == null || !audioFile.exists()) {
            String audioFormat = JniTools.getVideoAudioFormat(saveTo.getPath());
            if (audioFormat == null) {
                Toast.makeText(this, R.string.failed_extract_audio_1, Toast.LENGTH_SHORT).show();
                return false;
            }

            final File saveAudioFile = new File(saveTo.getParent() + "/" + new Date().getTime() + "." + audioFormat);
            mModel.downloadRecord.setAudio(saveAudioFile.getPath());
            mModel.downloadRecord.saveOrUpdate("id=?", String.valueOf(mModel.downloadRecord.getId()));

            CharSequence failureMessage = getText(R.string.failed_extract_audio_2).toString();
            new Thread(() -> {
                //改变状态为正在提取音频
                mModel.extractingAudioStatus.postValue(DownloadedVideoViewModel.ExtractingAudioStatus.Extracting);
                //提取音频
                int rc = JniTools.extractAudio(saveTo.getPath(), saveAudioFile.getPath());
                //检查状态
                if (rc == 0) {
                    mModel.extractingAudioStatus.postValue(DownloadedVideoViewModel.ExtractingAudioStatus.Ok);
                } else {
                    mModel.extractingAudioFailureMessage = failureMessage.toString().replace("%ec", String.valueOf(rc));
                    mModel.extractingAudioStatus.postValue(DownloadedVideoViewModel.ExtractingAudioStatus.Failure);
                }
            }).start();

            return false;
        }

        return true;
    }

    /**
     * 导出视频或音频资源到其它目录
     * */
    private void exportResource(boolean isVideo) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED
                || ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE}, 0);
            return;
        }

        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        if (isVideo) {
            intent.setType("video/" + mModel.downloadRecord.getSaveTo().substring(mModel.downloadRecord.getSaveTo().lastIndexOf('.') + 1));
            intent.putExtra(Intent.EXTRA_TITLE, new File(mModel.downloadRecord.getSaveTo()).getName());
            mExportVideoLauncher.launch(intent);
        } else {
            if (checkAudioExist()) {
                assert mModel.downloadRecord.getAudio() != null;
                intent.setType("audio/" + mModel.downloadRecord.getAudio().substring(mModel.downloadRecord.getAudio().lastIndexOf('.') + 1));
                intent.putExtra(Intent.EXTRA_TITLE, new File(mModel.downloadRecord.getAudio()).getName());
                mExportAudioLauncher.launch(intent);
            }
        }
    }

    /**
     * 确认是否将视频转换到指定类型
     */
    private void convertVideoFormatCheck(String toFormat) {
        if (mModel.convertVideoStatus.getValue() == DownloadedVideoViewModel.ConvertVideoStatus.Converting) {
            Toast.makeText(this, R.string.converting_video, Toast.LENGTH_SHORT).show();
            return;
        }

        final String oldFormat = mModel.downloadRecord.getSaveTo().substring(mModel.downloadRecord.getSaveTo().lastIndexOf('.') + 1);

        switch (toFormat.toLowerCase()) {
            case "mp4":
                mModel.convertTo = DownloadedVideoViewModel.VideoFormat.MP4;
                break;
            case "flv":
                mModel.convertTo = DownloadedVideoViewModel.VideoFormat.FLV;
                break;
            case "mkv":
                mModel.convertTo = DownloadedVideoViewModel.VideoFormat.MKV;
                break;
            case "wmv":
                mModel.convertTo = DownloadedVideoViewModel.VideoFormat.WMV;
                break;
        }

        if (oldFormat.equals(toFormat)) {
            return;
        }

        String message = getText(R.string.video_convert_tip).toString().replace("%args1", oldFormat).replace("%args2", toFormat);

        DialogTools.confirm(this, mModel.videoInfo.getVideoTitle(), message, (dialog, which) -> convertVideoFormat(toFormat), null);
    }

    /**
     * 转换视频到指定格式
     */
    private void convertVideoFormat(final String toFormat) {
        Log.d(TAG, "convertVideoFormat: saveTo " + mModel.downloadRecord.getSaveTo());

        if (mModel.downloadRecord.getConverting() != null) {
            Log.d(TAG, "convertVideoFormat: getConverting " + mModel.downloadRecord.getConverting());
        }

        if (mModel.downloadRecord.getConverting() != null) {
            File file = new File(mModel.downloadRecord.getConverting());
            if (file.exists() && !file.delete()) {
                Toast.makeText(this, R.string.convert_failure_1, Toast.LENGTH_LONG).show();
                return;
            }
        }

        final File convertSaveTo = new File(new File(mModel.downloadRecord.getSaveTo()).getParent() + "/" + new Date().getTime() + "." + toFormat);
        //记录转换格式后保存的文件
        mModel.downloadRecord.setConverting(convertSaveTo.getPath());
        mModel.downloadRecord.saveOrUpdate("id=?", String.valueOf(mModel.downloadRecord.getId()));

        Log.d(TAG, "convertVideoFormat: saveTo " + convertSaveTo);

        final CharSequence[] failureMessage = {
                getText(R.string.convert_failure_2),
                getText(R.string.convert_failure_3)
        };
        Thread thread = new Thread(() -> {
            mModel.convertVideoStatus.postValue(DownloadedVideoViewModel.ConvertVideoStatus.Converting);

            int rc = JniTools.videoFormatConversion(mModel.downloadRecord.getSaveTo(), convertSaveTo.getPath());

            if (rc != 0) {
                mModel.convertVideoFailureMessage = failureMessage[0].toString();
                mModel.convertVideoStatus.postValue(DownloadedVideoViewModel.ConvertVideoStatus.Failure);
            } else if (!new File(mModel.downloadRecord.getSaveTo()).delete()) {
                mModel.convertVideoFailureMessage = failureMessage[1].toString();
                mModel.convertVideoStatus.postValue(DownloadedVideoViewModel.ConvertVideoStatus.Failure);
            } else {

                mModel.downloadRecord.setSaveTo(convertSaveTo.getPath());
                mModel.downloadRecord.setConverting(null);
                mModel.downloadRecord.saveOrUpdate("id=?", String.valueOf(mModel.downloadRecord.getId()));

                mModel.convertVideoStatus.postValue(DownloadedVideoViewModel.ConvertVideoStatus.Ok);
            }
        });

        thread.start();
    }

    /**
     * 转换视频状态被改变
     */
    private void onConvertVideoStatusChanged(DownloadedVideoViewModel.ConvertVideoStatus convertVideoStatus) {
        if (convertVideoStatus == DownloadedVideoViewModel.ConvertVideoStatus.Ok) {
            //转换成功，更新显示信息
            reloadVideoInfo();
            reloadOperatorListAdapter();
            Toast.makeText(this, R.string.convert_completed, Toast.LENGTH_SHORT).show();
        } else if (convertVideoStatus == DownloadedVideoViewModel.ConvertVideoStatus.Failure) {
            reloadVideoInfo();
            reloadOperatorListAdapter();
            Toast.makeText(this, mModel.convertVideoFailureMessage, Toast.LENGTH_SHORT).show();
        } else if (convertVideoStatus == DownloadedVideoViewModel.ConvertVideoStatus.Converting) {
            reloadVideoInfo();
            reloadOperatorListAdapter();
        }
    }

    /**
     * 提取音频状态被改变
     */
    private void onExtractingAudioStatusChanged(DownloadedVideoViewModel.ExtractingAudioStatus extractingAudioStatus) {
        if (extractingAudioStatus == DownloadedVideoViewModel.ExtractingAudioStatus.Ok) {
            reloadAudioInfo();
            Toast.makeText(this, R.string.extract_audio_ok, Toast.LENGTH_SHORT).show();
        } else if (extractingAudioStatus == DownloadedVideoViewModel.ExtractingAudioStatus.Failure) {
            reloadAudioInfo();
            Toast.makeText(this, mModel.extractingAudioFailureMessage, Toast.LENGTH_SHORT).show();
        } else if (extractingAudioStatus == DownloadedVideoViewModel.ExtractingAudioStatus.Extracting) {
            reloadAudioInfo();
        }
    }

    private void deleteVideo() {
        if (mModel.convertVideoStatus.getValue() == DownloadedVideoViewModel.ConvertVideoStatus.Converting) {
            Toast.makeText(this, R.string.converting_video, Toast.LENGTH_SHORT).show();
            return;
        }

        DialogTools.confirm(this, mModel.videoInfo.getVideoTitle(), this.getText(R.string.delete_download_record_confirm), (dialog, which) -> {
            setResult(ResultCodeDeleted, new Intent().putExtra("download_record_id", mModel.downloadRecord.getId()));
            finish();
        }, null);
    }

}