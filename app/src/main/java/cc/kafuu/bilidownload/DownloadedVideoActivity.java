package cc.kafuu.bilidownload;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.MenuItem;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;

import org.litepal.LitePal;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Objects;

import cc.kafuu.bilidownload.adapter.OperatorListAdapter;
import cc.kafuu.bilidownload.bilibili.BvConvert;
import cc.kafuu.bilidownload.bilibili.account.BiliHistory;
import cc.kafuu.bilidownload.database.VideoDownloadRecord;
import cc.kafuu.bilidownload.database.VideoInfo;
import cc.kafuu.bilidownload.jniexport.JniTools;
import cc.kafuu.bilidownload.utils.ApplicationTools;
import cc.kafuu.bilidownload.utils.DialogTools;
import cc.kafuu.bilidownload.utils.Utility;

public class DownloadedVideoActivity extends AppCompatActivity {
    private static final String TAG = "DownloadedVideoActivity";

    public static int RequestCode = 0x03;

    public static int ResultCodeDeleted = 0x01;

    private VideoInfo mVideoInfo = null;
    private VideoDownloadRecord mDownloadRecord = null;

    private ImageView mVideoPic;
    private TextView mVideoTitle;
    private TextView mPart;
    private TextView mDownloadTime;

    private TextView mVideoBv;
    private TextView mVideoAvid;
    private TextView mVideoCid;
    private TextView mVideoFormat;
    private TextView mVideoSize;


    private RecyclerView mViewOperator;
    private RecyclerView mVideoFormatOperator;
    private RecyclerView mAudioOperator;
    private RecyclerView mVideoOtherOperator;


    public static void actionStartForResult(Fragment fragment, long videoRecordId, long downloadRecordId) {
        Intent intent = new Intent(fragment.getContext(), DownloadedVideoActivity.class);

        intent.putExtra("video_record_id", videoRecordId);
        intent.putExtra("download_record_id", downloadRecordId);

        fragment.startActivityForResult(intent, RequestCode);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_downloaded_video);

        Intent intent = getIntent();

        mDownloadRecord = LitePal.find(VideoDownloadRecord.class, intent.getLongExtra("download_record_id", 0));
        mVideoInfo = LitePal.find(VideoInfo.class, intent.getLongExtra("video_record_id", 0));

        findView();
        initView();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
        }
        return super.onOptionsItemSelected(item);
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
        mVideoSize = findViewById(R.id.videoSize);

        mViewOperator = findViewById(R.id.viewOperator);
        mVideoFormatOperator = findViewById(R.id.videoFormatOperator);
        mAudioOperator = findViewById(R.id.audioOperator);
        mVideoOtherOperator = findViewById(R.id.videoOtherOperator);
    }

    private void initView() {
        setSupportActionBar(findViewById(R.id.toolbar));
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);

        Glide.with(this).load(mVideoInfo.getPartPic()).placeholder(R.drawable.ic_2233).centerCrop().into(mVideoPic);
        mVideoTitle.setText(mVideoInfo.getVideoTitle());
        mPart.setText(mVideoInfo.getPartTitle());
        @SuppressLint("SimpleDateFormat") SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        mDownloadTime.setText(simpleDateFormat.format(mDownloadRecord.getStartTime()));

        updateVideoInfo();

        mViewOperator.setLayoutManager(new LinearLayoutManager(this));
        mVideoFormatOperator.setLayoutManager(new LinearLayoutManager(this));
        mAudioOperator.setLayoutManager(new LinearLayoutManager(this));
        mVideoOtherOperator.setLayoutManager(new LinearLayoutManager(this));

        Utility.setRecyclerViewNested(mViewOperator);
        Utility.setRecyclerViewNested(mVideoFormatOperator);
        Utility.setRecyclerViewNested(mAudioOperator);
        Utility.setRecyclerViewNested(mVideoOtherOperator);

        OperatorListAdapter adapter;

        adapter = new OperatorListAdapter(this);
        adapter.addItem(R.string.view_video, v -> viewVideo(false));
        adapter.addItem(R.string.send_video, v -> viewVideo(true));
        mViewOperator.setAdapter(adapter);

        adapter = new OperatorListAdapter(this);
        adapter.addItem(R.string.view_audio, v -> viewAudio(false));
        adapter.addItem(R.string.send_audio, v -> viewAudio(true));
        mAudioOperator.setAdapter(adapter);

        adapter = new OperatorListAdapter(this);
        adapter.addItem(R.string.convert_to_flv_format, v -> convertVideoFormatCheck("flv"));
        adapter.addItem(R.string.convert_to_mp4_format, v -> convertVideoFormatCheck("mp4"));
        adapter.addItem(R.string.convert_to_mkv_format, v -> convertVideoFormatCheck("mkv"));
        adapter.addItem(R.string.convert_to_wmv_format, v -> convertVideoFormatCheck("wmv"));
        mVideoFormatOperator.setAdapter(adapter);

        adapter = new OperatorListAdapter(this);
        adapter.addItem(R.string.delete_video, v -> deleteVideo());
        mVideoOtherOperator.setAdapter(adapter);


    }

    @SuppressLint("SetTextI18n")
    private void updateVideoInfo() {
        mVideoBv.setText(BvConvert.av2bv(String.valueOf(mVideoInfo.getAvid())));
        mVideoAvid.setText(String.valueOf(mVideoInfo.getAvid()));
        mVideoCid.setText(String.valueOf(mVideoInfo.getCid()));
        mVideoFormat.setText((mDownloadRecord.getSaveTo().substring(mDownloadRecord.getSaveTo().lastIndexOf('.') + 1) + " " + mVideoInfo.getFormat()).toUpperCase());
        mVideoSize.setText(Utility.getFileSizeString(new File(mDownloadRecord.getSaveTo()).length()));
    }

    /**
     * 发送或查看视频
     * */
    private void viewVideo(boolean isSend) {
        File file = new File(mDownloadRecord.getSaveTo());
        if (!file.exists()) {
            Toast.makeText(this, R.string.file_not_exist, Toast.LENGTH_SHORT).show();
            return;
        }
        ApplicationTools.shareOrViewFile(this, mVideoInfo.getVideoTitle() + "-" + mVideoInfo.getPartTitle(), file, "*/*", !isSend);
    }

    /**
     * 提取音频
     * */
    private void viewAudio(boolean isSend) {
        File saveTo = new File(mDownloadRecord.getSaveTo());
        if (!saveTo.exists()) {
            Toast.makeText(this, R.string.file_not_exist, Toast.LENGTH_SHORT).show();
            return;
        }

        File audioFile = (mDownloadRecord.getAudio() == null) ? null : new File(mDownloadRecord.getAudio());

        if (audioFile == null || !audioFile.exists()) {
            String audioFormat = JniTools.getVideoAudioFormat(saveTo.getPath());
            if (audioFormat == null) {
                Toast.makeText(this, R.string.failed_extract_audio_1, Toast.LENGTH_SHORT).show();
                return;
            }

            final ProgressDialog progressDialog = new ProgressDialog(this);
            progressDialog.setMessage(this.getText(R.string.extracting_audio));
            progressDialog.setCancelable(false);
            progressDialog.show();

            final File saveAudioFile = new File(saveTo.getParent() + "/" + new Date().getTime() + "." + audioFormat);
            mDownloadRecord.setAudio(saveAudioFile.getPath());
            mDownloadRecord.saveOrUpdate("id=?", String.valueOf(mDownloadRecord.getId()));

            new Thread(() -> {
                int rc = JniTools.extractAudio(saveTo.getPath(), saveAudioFile.getPath());
                progressDialog.cancel();
                if (rc == 0) {
                    new Handler(getMainLooper()).post(() -> viewAudio(isSend));
                } else {
                    new Handler(getMainLooper()).post(() -> Toast.makeText(this, getText(R.string.failed_extract_audio_2).toString().replace("%ec", String.valueOf(rc)), Toast.LENGTH_SHORT).show());
                }
            }).start();

            return;
        }

        ApplicationTools.shareOrViewFile(this, mVideoInfo.getVideoTitle() + "-" + mVideoInfo.getPartTitle(), audioFile, "*/*", !isSend);
    }

    private void convertVideoFormatCheck(String toFormat) {
        final String oldFormat = mDownloadRecord.getSaveTo().substring(mDownloadRecord.getSaveTo().lastIndexOf('.') + 1);

        if (oldFormat.equals(toFormat)) {
            return;
        }

        String message = getText(R.string.video_convert_tip).toString().replace("%args1", oldFormat).replace("%args2", toFormat);

        DialogTools.confirm(this, mVideoInfo.getVideoTitle(), message, (dialog, which) -> convertVideoFormat(toFormat) , null);
    }

    private void convertVideoFormat(final String toFormat) {
        Log.d(TAG, "convertVideoFormat: saveTo " + mDownloadRecord.getSaveTo());

        if (mDownloadRecord.getConverting() != null) {
            Log.d(TAG, "convertVideoFormat: getConverting " + mDownloadRecord.getConverting());
        }

        if (mDownloadRecord.getConverting() != null) {
            File file = new File(mDownloadRecord.getConverting());
            if (file.exists() && !file.delete()) {
                Toast.makeText(this, R.string.convert_failure_1, Toast.LENGTH_LONG).show();
                return;
            }
        }

        final File convertSaveTo = new File(new File(mDownloadRecord.getSaveTo()).getParent() + "/" + new Date().getTime() + "." + toFormat);

        Log.d(TAG, "convertVideoFormat: saveTo " + convertSaveTo.toString());

        mDownloadRecord.setConverting(convertSaveTo.getPath());
        mDownloadRecord.saveOrUpdate("id=?", String.valueOf(mDownloadRecord.getId()));

        final ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage(getText(R.string.convert_progress_title));
        progressDialog.setCancelable(false);
        progressDialog.show();

        Thread thread = new Thread(() -> {
            int rc = JniTools.videoFormatConversion(mDownloadRecord.getSaveTo(), convertSaveTo.getPath());

            progressDialog.cancel();
            if (rc != 0) {
                new Handler(getMainLooper()).post(() -> Toast.makeText(this, R.string.convert_failure_2, Toast.LENGTH_LONG).show());
            } else if (!new File(mDownloadRecord.getSaveTo()).delete()) {
                new Handler(getMainLooper()).post(() -> Toast.makeText(this, R.string.convert_failure_3, Toast.LENGTH_LONG).show());
            } else {
                new Handler(getMainLooper()).post(() -> {
                    mDownloadRecord.setSaveTo(convertSaveTo.getPath());
                    mDownloadRecord.setConverting(null);
                    mDownloadRecord.saveOrUpdate("id=?", String.valueOf(mDownloadRecord.getId()));

                    //转换成功，更新显示信息
                    updateVideoInfo();

                    Toast.makeText(this, R.string.convert_completed, Toast.LENGTH_SHORT).show();
                }); //重新加载数据
            }
        });

        thread.start();
    }

    private void deleteVideo() {
        DialogTools.confirm(this, mVideoInfo.getVideoTitle(), this.getText(R.string.delete_download_record_confirm), (dialog, which) -> {
            setResult(ResultCodeDeleted, new Intent().putExtra("download_record_id", mDownloadRecord.getId()));
            finish();
        }, null);
    }

}