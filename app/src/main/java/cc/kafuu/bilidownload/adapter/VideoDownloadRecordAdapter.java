package cc.kafuu.bilidownload.adapter;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Context;
import android.content.DialogInterface;
import android.database.Cursor;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import org.litepal.FluentQuery;
import org.litepal.LitePal;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import cc.kafuu.bilidownload.R;
import cc.kafuu.bilidownload.bilibili.BvConvert;
import cc.kafuu.bilidownload.bilibili.video.BiliDownloader;
import cc.kafuu.bilidownload.bilibili.video.BiliVideoResource;
import cc.kafuu.bilidownload.database.VideoDownloadRecord;
import cc.kafuu.bilidownload.database.VideoInfo;
import cc.kafuu.bilidownload.utils.DialogTools;
import cc.kafuu.bilidownload.utils.SystemTools;

public class VideoDownloadRecordAdapter extends RecyclerView.Adapter<VideoDownloadRecordAdapter.InnerHolder> {
    private final Handler mHandle;
    private final Context mContext;
    private final DownloadManager mDownloadManager;
    private List<VideoDownloadRecord> mRecords;

    public VideoDownloadRecordAdapter(Context context) {
        this.mHandle = new Handler(Looper.getMainLooper());

        this.mContext = context;
        this.mDownloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        reloadRecords();
    }

    public void reloadRecords() {
        mRecords = LitePal.findAll(VideoDownloadRecord.class);

        //删除无效数据
        mRecords.removeIf(record -> {
            try (Cursor cursor = mDownloadManager.query(new DownloadManager.Query().setFilterById(record.getDownloadId()))) {
                if (cursor == null || !cursor.moveToFirst()) {
                    LitePal.delete(VideoDownloadRecord.class, record.getId());
                    return true;
                }
                return false;
            }
        });

        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VideoDownloadRecordAdapter.InnerHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.list_item_download_record, parent, false);
        return new InnerHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VideoDownloadRecordAdapter.InnerHolder holder, int position) {
        holder.bindRecord(mRecords.get(position));
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
                                //加载信息失败（任务已被删除），通知列表更新
                                notifyDataSetChanged();
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

            Glide.with(mContext).load(mVideoInfo.getPartPic()).placeholder(R.drawable.ic_2233).centerCrop().into(mVideoPic);
            mVideoTitle.setText(mVideoInfo.getPartTitle() + "-" + mVideoInfo.getVideoTitle());
            mVid.setText(BvConvert.av2bv(String.valueOf(mVideoInfo.getAvid())));
            mFormat.setText(mVideoInfo.getQualityDescription() + "(" + mVideoInfo.getFormat() + ")");

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
                    mDownloadStatus.setTextColor(mContext.getColor(R.color.blue));
                    mDownloadStatus.setText(R.string.download_running);
                    mDownloadProgress.setMax(10000);
                    mDownloadProgress.setProgress((int)(((double) completedSize / (double) totalSize) * 10000.0D));

                } else if (mDownloadStatusFlag == DownloadManager.STATUS_PAUSED){
                    mDownloadStatus.setText(R.string.download_paused);
                    mDownloadStatus.setTextColor(mContext.getColor(R.color.gray));

                } else if (mDownloadStatusFlag == DownloadManager.STATUS_PENDING){
                    mDownloadStatus.setText(R.string.download_pending);
                    mDownloadStatus.setTextColor(mContext.getColor(R.color.gray));

                } else if (mDownloadStatusFlag == DownloadManager.STATUS_FAILED){
                    mDownloadStatus.setText(R.string.download_failure);
                    mDownloadStatus.setTextColor(mContext.getColor(R.color.red));

                } else if (mDownloadStatusFlag == DownloadManager.STATUS_SUCCESSFUL){
                    mDownloadStatus.setText(R.string.download_complete);
                    mDownloadStatus.setTextColor(mContext.getColor(R.color.green));

                } else {
                    mDownloadStatusFlag = -1;
                    mDownloadStatus.setText(R.string.download_unknown);
                    mDownloadStatus.setTextColor(mContext.getColor(R.color.red));
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

            Glide.with(mContext).load(R.drawable.ic_2233).into(mVideoPic);
            mVideoTitle.setText(R.string.load_video_info_failure);
            mVid.setText("AV:" + mBindRecord.getAvid() + ", CID: " + mBindRecord.getCid());
            mFormat.setText(null);
            mDownloadStatus.setText(null);
            mDownloadInfo.setText(null);
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
                DialogTools.confirm(mContext, mContext.getText(R.string.confirm),
                        mContext.getText(R.string.download_item_undefined),
                        (dialogInterface, i) -> onDeleteItem(),
                        null);
                return;
            }

            if (mVideoInfo == null) {
                return;
            }

            //下载失败
            if (mDownloadStatusFlag == DownloadManager.STATUS_FAILED) {
                new AlertDialog.Builder(mContext)
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
                DialogTools.confirm(mContext, mContext.getText(R.string.confirm),
                        mContext.getText(R.string.cancel_download_confirm),
                        (dialogInterface, i) -> onDeleteItem(),
                        null);
                return;
            }

            //下载完成的任务
            if (mDownloadStatusFlag == DownloadManager.STATUS_SUCCESSFUL) {
                CharSequence[] items = new CharSequence[] {mContext.getText(R.string.view), mContext.getText(R.string.send), mContext.getText(R.string.delete), mContext.getText(R.string.cancel) };
                new AlertDialog.Builder(mContext)
                        .setTitle(mVideoInfo.getVideoTitle())
                        .setItems(items, (dialogInterface, i) -> onOperationSelected(i))
                        .create().show();
            }

        }

        private void onDeleteItem() {
            File saveTo = new File(mBindRecord.getSaveTo());

            if (saveTo.exists() && !saveTo.delete()) {
                Toast.makeText(mContext, R.string.delete_download_file_failure, Toast.LENGTH_SHORT).show();
                return;
            }

            LitePal.delete(VideoDownloadRecord.class, mBindRecord.getId());
            mDownloadManager.remove(mBindRecord.getDownloadId());
            mRecords.remove(mBindRecord);

            notifyDataSetChanged();
        }

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
                public void onCompleted(BiliDownloader downloader) {
                    downloader.getDownloadId(mDownloadManager, new BiliDownloader.GetDownloadIdCallback() {
                        @Override
                        public void onFailure(String message) {
                            mHandle.post(() -> Toast.makeText(mContext, message, Toast.LENGTH_SHORT).show());
                        }

                        @Override
                        public void onCompleted(long id) {
                            mBindRecord.setDownloadId(id);
                            mBindRecord.saveOrUpdate("id=?", String.valueOf(mBindRecord.getId()));
                        }
                    });
                }

                @Override
                public void onFailure(String message) {
                    mHandle.post(() -> Toast.makeText(mContext, message, Toast.LENGTH_SHORT).show());
                }
            });
        }

        private void onOperationSelected(int i) {
            if (i == 0 || i == 1) {
                //查看、发送
                File file = new File(mBindRecord.getSaveTo());
                if (!file.exists()) {
                    Toast.makeText(mContext, R.string.file_not_exist, Toast.LENGTH_SHORT).show();
                    return;
                }
                SystemTools.shareOrViewFile(mContext, mVideoInfo.getVideoTitle() + "-" + mVideoInfo.getPartTitle(), file, "*/*", i == 0);

            } else if (i == 2) {
                //删除
                DialogTools.confirm(mContext, mVideoInfo.getVideoTitle(),
                        mContext.getText(R.string.delete_download_record_confirm),
                        (dialogInterface, x) -> onDeleteItem(),
                        null);
            }
        }
    }
}
