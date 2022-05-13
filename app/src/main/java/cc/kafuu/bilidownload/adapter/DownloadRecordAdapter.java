package cc.kafuu.bilidownload.adapter;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.app.ProgressDialog;
import android.content.Context;
import android.database.Cursor;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import org.litepal.FluentQuery;
import org.litepal.LitePal;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import cc.kafuu.bilidownload.R;
import cc.kafuu.bilidownload.bilibili.BvConvert;
import cc.kafuu.bilidownload.bilibili.video.BiliDownloader;
import cc.kafuu.bilidownload.bilibili.video.BiliVideoResource;
import cc.kafuu.bilidownload.database.VideoDownloadRecord;
import cc.kafuu.bilidownload.database.VideoInfo;
import cc.kafuu.bilidownload.fragment.ItemChangeListener;
import cc.kafuu.bilidownload.jniexport.JniTools;
import cc.kafuu.bilidownload.utils.DialogTools;
import cc.kafuu.bilidownload.utils.ApplicationTools;

public class DownloadRecordAdapter extends RecyclerView.Adapter<DownloadRecordAdapter.InnerHolder> {
    private static final String TAG = "DownloadRecordAdapter";

    private final Handler mHandle;
    private final Activity mActivity;
    private final DownloadManager mDownloadManager;
    private List<VideoDownloadRecord> mRecords;

    private ItemChangeListener mItemCountChangeListener = null;

    public DownloadRecordAdapter(Activity activity) {
        this.mHandle = new Handler(Looper.getMainLooper());

        this.mActivity = activity;
        this.mDownloadManager = (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);
        reloadRecords();
    }

    public void setItemCountChangeListener(ItemChangeListener mItemCountChangeListener) {
        this.mItemCountChangeListener = mItemCountChangeListener;
    }

    private void itemChange() {
        if (this.mItemCountChangeListener != null) {
            this.mItemCountChangeListener.onItemChange();
        }
    }



    public void reloadRecords() {
        mRecords = LitePal.findAll(VideoDownloadRecord.class);

        //删除无效数据
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            mRecords.removeIf(record -> !checkRecord(record));
        } else {
            List<VideoDownloadRecord> newRecords = new ArrayList<>();
            for (VideoDownloadRecord record : mRecords) {
                if (checkRecord(record)) {
                    newRecords.add(record);
                }
            }
            mRecords = newRecords;
        }

        notifyDataSetChanged();
        itemChange();
    }

    /**
     * 校验记录是否有效
     * 如果记录无效则删除并返回false
     * */
    private boolean checkRecord(VideoDownloadRecord record) {
        try (Cursor cursor = mDownloadManager.query(new DownloadManager.Query().setFilterById(record.getDownloadId()))) {
            if (cursor == null || !cursor.moveToFirst()) {
                Log.d(TAG, "checkRecord: " + record.getDownloadId() + " lose efficacy");
                LitePal.delete(VideoDownloadRecord.class, record.getId());
                return false;
            }
            return true;
        }
    }

    @NonNull
    @Override
    public DownloadRecordAdapter.InnerHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.list_item_download_record, parent, false);
        return new InnerHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull DownloadRecordAdapter.InnerHolder holder, int position) {
        if (ApplicationTools.isActivitySurvive(mActivity)) {
            holder.bindRecord(mRecords.get(position));
        }

    }

    @Override
    public int getItemCount() {
        return mRecords.size();
    }

    private enum LoadedStatus {
        Loading, LoadedFailure, LoadingCompleted
    }

    public class InnerHolder extends RecyclerView.ViewHolder {
        private VideoDownloadRecord mBindRecord = null;
        private VideoInfo mVideoInfo = null;

        private final ImageView mVideoPic;
        private final TextView mVideoTitle;
        private final TextView mVid;
        private final TextView mFormat;
        private final TextView mDownloadStatus;
        private final ProgressBar mDownloadProgress;
        private final TextView mDownloadInfo;

        //加载状态
        private LoadedStatus mLoadedStatus;
        //下载状态
        private int mDownloadStatusFlag;

        private Timer mUpdate = null;

        public InnerHolder(@NonNull View itemView) {
            super(itemView);

            LinearLayout mItem = itemView.findViewById(R.id.item);
            mVideoPic = itemView.findViewById(R.id.videoPic);
            mVideoTitle = itemView.findViewById(R.id.videoTitle);
            mVid = itemView.findViewById(R.id.vid);
            mFormat = itemView.findViewById(R.id.format);
            mDownloadStatus = itemView.findViewById(R.id.downloadStatus);
            mDownloadProgress = itemView.findViewById(R.id.downloadProgress);
            mDownloadInfo = itemView.findViewById(R.id.downloadInfo);


            //绑定点击事件
            mItem.setOnClickListener(view -> {if (mBindRecord != null) {onItemClick();}});
        }

        public void bindRecord(final VideoDownloadRecord record) {
            this.mBindRecord = record;
            mLoadedStatus = LoadedStatus.Loading;

            if (loadingBaseInfo() && loadingDownloadInfo(false)) {
                mLoadedStatus = LoadedStatus.LoadingCompleted;
            }

            if (mUpdate != null) {
                mUpdate.cancel();
            }
            mUpdate = null;

            //如果当前下载状态非成功，则启动定时查询
            if (mDownloadStatusFlag != DownloadManager.STATUS_SUCCESSFUL) {
                mUpdate = new Timer();
                mUpdate.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        mHandle.post(() -> {
                            if (mBindRecord != null && !loadingDownloadInfo(true)) {
                                //加载信息失败（任务已被删除），删除记录并通知列表更新
                                notifyDataSetChanged();
                                itemChange();
                            } else if (mUpdate != null && mDownloadStatusFlag == DownloadManager.STATUS_SUCCESSFUL){
                                mUpdate.cancel();
                                mUpdate = null;
                            }
                        });
                    }
                }, 1000, 1000);
            }
        }

        /**
         * 加载视频基础信息
         * 视频标题、BV、格式信息
         * */
        @SuppressLint("SetTextI18n")
        public boolean loadingBaseInfo() {
            FluentQuery query = LitePal.where("avid=? AND cid=? AND quality=?",
                    String.valueOf(mBindRecord.getAvid()),
                    String.valueOf(mBindRecord.getCid()),
                    String.valueOf(mBindRecord.getQuality()));
            mVideoInfo = query.findFirst(VideoInfo.class);
            if (mVideoInfo == null) {
                loadedFailure();
                return false;
            }

            if (ApplicationTools.isActivitySurvive(mActivity)) {
                Glide.with(mActivity).load(mVideoInfo.getPartPic()).placeholder(R.drawable.ic_2233).centerCrop().into(mVideoPic);
                mVideoTitle.setText(mVideoInfo.getPartTitle() + "-" + mVideoInfo.getVideoTitle());
                mVid.setText(BvConvert.av2bv(String.valueOf(mVideoInfo.getAvid())));
                mFormat.setText(mVideoInfo.getQualityDescription() + "(" + mBindRecord.getSaveTo().substring(mBindRecord.getSaveTo().lastIndexOf('.') + 1) + ")");
            }

            return true;
        }

        /**
         * 加载视频下载信息
         * 判断是否下载完成，以及下载进度
         * */
        public boolean loadingDownloadInfo(boolean failureRemoveItem) {
            if (mBindRecord == null) {
                return false;
            }

            try (Cursor cursor = mDownloadManager.query(new DownloadManager.Query().setFilterById(mBindRecord.getDownloadId()))) {
                if (cursor == null || !cursor.moveToNext()) {
                    if (failureRemoveItem) {
                        mRecords.remove(mBindRecord);
                    }
                    loadedFailure();
                    return false;
                }

                mDownloadStatusFlag = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS));
                mDownloadProgress.setVisibility(mDownloadStatusFlag == DownloadManager.STATUS_RUNNING ? View.VISIBLE : View.GONE);

                long completedSize = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
                long totalSize = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));

                @SuppressLint("SimpleDateFormat") SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                mDownloadInfo.setText(simpleDateFormat.format(mBindRecord.getStartTime()));

                if (mDownloadStatusFlag == DownloadManager.STATUS_RUNNING) {
                    mDownloadStatus.setTextColor(ContextCompat.getColor(mActivity, R.color.blue));
                    mDownloadStatus.setText(R.string.download_running);
                    mDownloadProgress.setMax(10000);
                    mDownloadProgress.setProgress((int)(((double) completedSize / (double) totalSize) * 10000.0D));

                } else if (mDownloadStatusFlag == DownloadManager.STATUS_PAUSED){
                    mDownloadStatus.setText(R.string.download_paused);
                    mDownloadStatus.setTextColor(ContextCompat.getColor(mActivity, R.color.gray));

                } else if (mDownloadStatusFlag == DownloadManager.STATUS_PENDING){
                    mDownloadStatus.setText(R.string.download_pending);
                    mDownloadStatus.setTextColor(ContextCompat.getColor(mActivity, R.color.gray));

                } else if (mDownloadStatusFlag == DownloadManager.STATUS_FAILED){
                    mDownloadStatus.setText(R.string.download_failure);
                    mDownloadStatus.setTextColor(ContextCompat.getColor(mActivity, R.color.red));

                } else if (mDownloadStatusFlag == DownloadManager.STATUS_SUCCESSFUL){
                    mDownloadStatus.setText(R.string.download_complete);
                    mDownloadStatus.setTextColor(ContextCompat.getColor(mActivity, R.color.green));

                } else {
                    mDownloadStatusFlag = -1;
                    mDownloadStatus.setText(R.string.download_unknown);
                    mDownloadStatus.setTextColor(ContextCompat.getColor(mActivity, R.color.red));
                }

                return true;
            }
        }

        /**
         * 视频信息加载失败
         * */
        @SuppressLint("SetTextI18n")
        public void loadedFailure() {
            mLoadedStatus = LoadedStatus.LoadedFailure;

            if (ApplicationTools.isActivitySurvive(mActivity)) {
                Glide.with(mActivity).load(R.drawable.ic_2233).into(mVideoPic);
                mVideoTitle.setText(R.string.load_video_info_failure);
                mVid.setText("AV:" + mBindRecord.getAvid() + ", CID: " + mBindRecord.getCid());
                mFormat.setText(null);
                mDownloadStatus.setText(null);
                mDownloadInfo.setText(null);
            }
        }

        /**
         * 项目被用户点击
         * */
        private void onItemClick() {
            if (mBindRecord == null || mLoadedStatus == LoadedStatus.Loading) {
                return;
            }

            //表项加载失败
            if (mLoadedStatus == LoadedStatus.LoadedFailure || mDownloadStatusFlag == -1) {
                DialogTools.confirm(mActivity, mActivity.getText(R.string.confirm),
                        mActivity.getText(R.string.download_item_undefined),
                        (dialogInterface, i) -> onDeleteItem(),
                        null);
                return;
            }

            if (mVideoInfo == null) {
                return;
            }

            //下载失败
            if (mDownloadStatusFlag == DownloadManager.STATUS_FAILED) {
                new AlertDialog.Builder(mActivity)
                        .setTitle(mVideoInfo.getVideoTitle())
                        .setMessage(R.string.download_failure_whether_restart_or_remode)
                        .setNegativeButton(R.string.cancel, null)
                        .setPositiveButton(R.string.restart, (dialogInterface, i) -> onRestartTask())
                        .setNeutralButton(R.string.delete, (dialogInterface, i) -> onDeleteItem())
                        .create().show();
                return;
            }

            //等待下载、下载挂起、正在下载
            if (mDownloadStatusFlag == DownloadManager.STATUS_PENDING || mDownloadStatusFlag == DownloadManager.STATUS_PAUSED || mDownloadStatusFlag == DownloadManager.STATUS_RUNNING) {
                DialogTools.confirm(mActivity, mActivity.getText(R.string.confirm),
                        mActivity.getText(R.string.cancel_download_confirm),
                        (dialogInterface, i) -> onDeleteItem(),
                        null);
                return;
            }

            //下载完成的任务
            if (mDownloadStatusFlag == DownloadManager.STATUS_SUCCESSFUL) {
                CharSequence[] items = new CharSequence[] {
                        mActivity.getText(R.string.view_video),
                        mActivity.getText(R.string.send_video),
                        mActivity.getText(R.string.convert_format),
                        mActivity.getText(R.string.extract_audio),
                        mActivity.getText(R.string.delete_video),
                        mActivity.getText(R.string.cancel_operation) };

                new AlertDialog.Builder(mActivity)
                        .setTitle(mVideoInfo.getVideoTitle())
                        .setItems(items, (dialogInterface, i) -> onOperationSelected(i))
                        .create().show();
            }

        }

        /**
         * 删除下载项
         * */
        private void onDeleteItem() {
            //删除转换临时记录文件
            File converting = (mBindRecord.getConverting() == null) ? null : new File(mBindRecord.getConverting());
            if (converting != null && converting.exists() && !converting.delete()) {
                Toast.makeText(mActivity, R.string.delete_download_file_failure, Toast.LENGTH_SHORT).show();
                return;
            }

            //删除音频文件
            File audio = (mBindRecord.getAudio() == null) ? null : new File(mBindRecord.getAudio());
            if (audio != null && audio.exists() && !audio.delete()) {
                Toast.makeText(mActivity, R.string.delete_download_file_failure, Toast.LENGTH_SHORT).show();
            }

            //删除视频文件
            File saveTo = new File(mBindRecord.getSaveTo());
            if (saveTo.exists() && !saveTo.delete()) {
                Toast.makeText(mActivity, R.string.delete_download_file_failure, Toast.LENGTH_SHORT).show();
                return;
            }

            LitePal.delete(VideoDownloadRecord.class, mBindRecord.getId());
            mDownloadManager.remove(mBindRecord.getDownloadId());
            mRecords.remove(mBindRecord);

            notifyDataSetChanged();
            itemChange();
        }

        /**
         * 重启任务
         * */
        private void onRestartTask() {
            //通过Cid/Avid/Quality重新获取下载源
            BiliVideoResource.getDownloadUrl(mVideoInfo.getVideoTitle(),
                    mVideoInfo.getPartTitle(),
                    mBindRecord.getCid(),
                    mBindRecord.getAvid(),
                    mBindRecord.getQuality(),
                    new File(mBindRecord.getSaveTo()),
                    new BiliVideoResource.GetDownloaderCallback() {
                @Override
                public void completed(BiliDownloader downloader) {
                    downloader.getDownloadId(mDownloadManager, new BiliDownloader.GetDownloadIdCallback() {
                        @Override
                        public void failure(String message) {
                            mHandle.post(() -> Toast.makeText(mActivity, message, Toast.LENGTH_SHORT).show());
                        }

                        @Override
                        public void completed(long id) {
                            mBindRecord.setDownloadId(id);
                            mBindRecord.saveOrUpdate("id=?", String.valueOf(mBindRecord.getId()));
                        }
                    });
                }

                @Override
                public void failure(String message) {
                    mHandle.post(() -> Toast.makeText(mActivity, message, Toast.LENGTH_SHORT).show());
                }
            });
        }

        /**
         * 视频操作被选择
         * */
        private void onOperationSelected(int i) {
            if (i == 0 || i == 1) {
                //查看、发送
                File file = new File(mBindRecord.getSaveTo());
                if (!file.exists()) {
                    Toast.makeText(mActivity, R.string.file_not_exist, Toast.LENGTH_SHORT).show();
                    return;
                }
                ApplicationTools.shareOrViewFile(mActivity, mVideoInfo.getVideoTitle() + "-" + mVideoInfo.getPartTitle(), file, "*/*", i == 0);

            } else if (i == 2) {
                //视频格式化转换
                //当前格式是flv就转为mp4，如果是mp4就转为flv
                final String format = mBindRecord.getSaveTo().substring(mBindRecord.getSaveTo().lastIndexOf('.') + 1);
                final String toFormat = format.contains("mp4") ? "flv" : "mp4";

                DialogTools.confirm(mActivity,
                        mVideoInfo.getVideoTitle(),
                        mActivity.getText(R.string.video_convert_tip).toString().replace("%args1", format).replace("%args2", toFormat),
                        (dialog, which) -> convertVideoFormat(toFormat),
                        null);
            } else if (i == 3) {
                //提取音频
                extractAudio();
            } else if (i == 4) {
                //删除
                DialogTools.confirm(mActivity, mVideoInfo.getVideoTitle(),
                        mActivity.getText(R.string.delete_download_record_confirm),
                        (dialogInterface, x) -> onDeleteItem(),
                        null);
            }
        }

        /**
         * 转换视频格式
         * */
        private void convertVideoFormat(final String toFormat) {
            Log.d(TAG, "convertVideoFormat: saveTo " + mBindRecord.getSaveTo());

            if (mBindRecord.getConverting() != null) {
                Log.d(TAG, "convertVideoFormat: getConverting " + mBindRecord.getConverting());
            }

            if (mBindRecord.getConverting() != null) {
                File file = new File(mBindRecord.getConverting());
                if (file.exists() && !file.delete()) {
                    Toast.makeText(mActivity, R.string.convert_failure_1, Toast.LENGTH_LONG).show();
                    return;
                }
            }

            final File convertSaveTo = new File(new File(mBindRecord.getSaveTo()).getParent() + "/" + new Date().getTime() + "." + toFormat);

            Log.d(TAG, "convertVideoFormat: saveTo " + convertSaveTo.toString());

            mBindRecord.setConverting(convertSaveTo.getPath());
            mBindRecord.saveOrUpdate("id=?", String.valueOf(mBindRecord.getId()));

            final ProgressDialog progressDialog = new ProgressDialog(mActivity);
            progressDialog.setMessage(mActivity.getText(R.string.convert_progress_title));
            progressDialog.setCancelable(false);
            progressDialog.show();

            Thread thread = new Thread(() -> {
                int rc = JniTools.videoFormatConversion(mBindRecord.getSaveTo(), convertSaveTo.getPath());

                progressDialog.cancel();
                if (rc != 0) {
                    mHandle.post(() -> Toast.makeText(mActivity, R.string.convert_failure_2, Toast.LENGTH_LONG).show());
                } else if (!new File(mBindRecord.getSaveTo()).delete()) {
                    mHandle.post(() -> Toast.makeText(mActivity, R.string.convert_failure_3, Toast.LENGTH_LONG).show());
                } else {
                    mHandle.post(() -> {
                        mBindRecord.setSaveTo(convertSaveTo.getPath());
                        mBindRecord.setConverting(null);
                        mBindRecord.saveOrUpdate("id=?", String.valueOf(mBindRecord.getId()));

                        if(!loadingBaseInfo()) {
                            loadedFailure();
                        }
                        Toast.makeText(mActivity, R.string.convert_completed, Toast.LENGTH_SHORT).show();
                    }); //重新加载数据
                }
            });

            thread.start();

        }

        /**
         * 提取音频
         * */
        private void extractAudio() {
            File saveTo = new File(mBindRecord.getSaveTo());
            if (!saveTo.exists()) {
                Toast.makeText(mActivity, R.string.file_not_exist, Toast.LENGTH_SHORT).show();
                return;
            }

            File audioFile = (mBindRecord.getAudio() == null) ? null : new File(mBindRecord.getAudio());

            if (audioFile == null || !audioFile.exists()) {
                String audioFormat = JniTools.getVideoAudioFormat(saveTo.getPath());
                if (audioFormat == null) {
                    Toast.makeText(mActivity, R.string.failed_extract_audio_1, Toast.LENGTH_SHORT).show();
                    return;
                }

                final ProgressDialog progressDialog = new ProgressDialog(mActivity);
                progressDialog.setMessage(mActivity.getText(R.string.extracting_audio));
                progressDialog.setCancelable(false);
                progressDialog.show();

                final File saveAudioFile = new File(saveTo.getParent() + "/" + new Date().getTime() + "." + audioFormat);
                mBindRecord.setAudio(saveAudioFile.getPath());
                mBindRecord.saveOrUpdate("id=?", String.valueOf(mBindRecord.getId()));

                new Thread(() -> {
                    int rc = JniTools.extractAudio(saveTo.getPath(), saveAudioFile.getPath());
                    progressDialog.cancel();
                    if (rc == 0) {
                        mHandle.post(this::extractAudio);
                    } else {
                        mHandle.post(() -> Toast.makeText(mActivity, mActivity.getText(R.string.failed_extract_audio_2).toString().replace("%ec", String.valueOf(rc)), Toast.LENGTH_SHORT).show());
                    }
                }).start();

                return;
            }


            CharSequence[] items = new CharSequence[] {
                    mActivity.getText(R.string.view_audio),
                    mActivity.getText(R.string.send_audio),
                    mActivity.getText(R.string.cancel_operation) };

            new AlertDialog.Builder(mActivity)
                    .setTitle(mVideoInfo.getVideoTitle())
                    .setItems(items, (dialogInterface, i) -> {
                        if (i == 0 || i == 1) {
                            ApplicationTools.shareOrViewFile(mActivity, mVideoInfo.getVideoTitle() + "-" + mVideoInfo.getPartTitle(), audioFile, "*/*", i == 0);
                        }
                    })
                    .create().show();

        }
    }
}
