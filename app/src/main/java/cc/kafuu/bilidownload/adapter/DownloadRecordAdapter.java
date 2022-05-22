package cc.kafuu.bilidownload.adapter;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
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

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import org.litepal.FluentQuery;
import org.litepal.LitePal;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import cc.kafuu.bilidownload.DownloadedVideoActivity;
import cc.kafuu.bilidownload.R;
import cc.kafuu.bilidownload.bilibili.BvConvert;
import cc.kafuu.bilidownload.bilibili.video.BiliDownloader;
import cc.kafuu.bilidownload.bilibili.video.BiliVideoResource;
import cc.kafuu.bilidownload.database.VideoDownloadRecord;
import cc.kafuu.bilidownload.database.VideoInfo;
import cc.kafuu.bilidownload.utils.DialogTools;
import cc.kafuu.bilidownload.utils.ApplicationTools;

public class DownloadRecordAdapter extends RecyclerView.Adapter<DownloadRecordAdapter.InnerHolder> {
    private static final String TAG = "DownloadRecordAdapter";

    private final Handler mHandle;
    private final Activity mActivity;
    private final DownloadManager mDownloadManager;
    private List<VideoDownloadRecord> mRecords;

    private final ActivityResultLauncher<Intent> mDownloadVideoActivityResultLauncher;

    public interface ItemChangeListener {
        void onItemChange();
    }

    private ItemChangeListener mItemCountChangeListener = null;

    public DownloadRecordAdapter(Activity activity, ActivityResultLauncher<Intent> launcher) {
        this.mHandle = new Handler(Looper.getMainLooper());

        this.mActivity = activity;
        this.mDownloadManager = (DownloadManager) mActivity.getSystemService(Context.DOWNLOAD_SERVICE);

        this.mDownloadVideoActivityResultLauncher = launcher;
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



    @SuppressLint("NotifyDataSetChanged")
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
                removeDownloadRecord(record.getId(), false);
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

    public void removeDownloadRecord(long downloadRecordId, boolean changeItems) {

        VideoDownloadRecord videoDownloadRecord = LitePal.find(VideoDownloadRecord.class, downloadRecordId);

        //删除转换临时记录文件
        File converting = (videoDownloadRecord.getConverting() == null) ? null : new File(videoDownloadRecord.getConverting());
        if (converting != null && converting.exists() && !converting.delete()) {
            Toast.makeText(mActivity, R.string.delete_download_file_failure, Toast.LENGTH_SHORT).show();
            return;
        }

        //删除音频文件
        File audio = (videoDownloadRecord.getAudio() == null) ? null : new File(videoDownloadRecord.getAudio());
        if (audio != null && audio.exists() && !audio.delete()) {
            Toast.makeText(mActivity, R.string.delete_download_file_failure, Toast.LENGTH_SHORT).show();
        }

        //删除视频文件
        File saveTo = new File(videoDownloadRecord.getSaveTo());
        if (saveTo.exists() && !saveTo.delete()) {
            Toast.makeText(mActivity, R.string.delete_download_file_failure, Toast.LENGTH_SHORT).show();
            return;
        }

        LitePal.delete(VideoDownloadRecord.class, videoDownloadRecord.getId());
        mDownloadManager.remove(videoDownloadRecord.getDownloadId());

        if (changeItems) {
            for (VideoDownloadRecord item : mRecords) {
                if (item.getId() == downloadRecordId) {
                    mRecords.remove(item);
                    break;
                }
            }

            notifyDataSetChanged();
            itemChange();
        }
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
                mFormat.setText(mVideoInfo.getQualityDescription());
            }

            return true;
        }

        /**
         * 加载视频下载信息
         * 判断是否下载完成，以及下载进度
         * */
        @SuppressLint("Range")
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

                //下载时间
                @SuppressLint("SimpleDateFormat") SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                mDownloadInfo.setText(simpleDateFormat.format(mBindRecord.getStartTime()));

                //下载状态
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
                        (dialogInterface, i) -> removeDownloadRecord(mBindRecord.getId(), true),
                        null);
                return;
            }

            if (mVideoInfo == null) {
                return;
            }

            //下载失败
            if (mDownloadStatusFlag == DownloadManager.STATUS_FAILED) {
                int ec = -1;
                try (Cursor cursor = mDownloadManager.query(new DownloadManager.Query().setFilterById(mBindRecord.getDownloadId()))) {
                    if (cursor.moveToFirst()) {
                        int columnIndex = cursor.getColumnIndex(DownloadManager.COLUMN_REASON);
                        ec = cursor.getInt(columnIndex);
                    }
                }

                new AlertDialog.Builder(mActivity)
                        .setTitle(mVideoInfo.getVideoTitle())
                        .setMessage(mActivity.getText(R.string.download_failure_whether_restart_or_remode).toString().replace("%1", String.valueOf(ec)))
                        .setNegativeButton(R.string.cancel, null)
                        .setPositiveButton(R.string.restart, (dialogInterface, i) -> restartTask())
                        .setNeutralButton(R.string.delete, (dialogInterface, i) -> removeDownloadRecord(mBindRecord.getId(), true))
                        .create().show();
                return;
            }

            //等待下载、下载挂起、正在下载
            if (mDownloadStatusFlag == DownloadManager.STATUS_PENDING || mDownloadStatusFlag == DownloadManager.STATUS_PAUSED || mDownloadStatusFlag == DownloadManager.STATUS_RUNNING) {
                DialogTools.confirm(mActivity, mActivity.getText(R.string.confirm),
                        mActivity.getText(R.string.cancel_download_confirm),
                        (dialogInterface, i) -> removeDownloadRecord(mBindRecord.getId(), true),
                        null);
                return;
            }

            //下载完成的任务
            if (mDownloadStatusFlag == DownloadManager.STATUS_SUCCESSFUL) {
                DownloadedVideoActivity.actionStartForResult(mActivity, mDownloadVideoActivityResultLauncher, mVideoInfo.getId(), mBindRecord.getId());
            }

        }



        /**
         * 重启任务
         * */
        private void restartTask() {
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

    }


}
