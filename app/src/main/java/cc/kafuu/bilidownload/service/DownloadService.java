package cc.kafuu.bilidownload.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.widget.Toast;

import androidx.annotation.Nullable;

import java.io.File;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import cc.kafuu.bilidownload.MainActivity;
import cc.kafuu.bilidownload.R;
import cc.kafuu.bilidownload.bilibili.video.BiliDownloader;
import cc.kafuu.bilidownload.bilibili.video.BiliVideo;
import cc.kafuu.bilidownload.bilibili.video.BiliVideoPart;
import cc.kafuu.bilidownload.bilibili.video.BiliVideoResource;
import cc.kafuu.bilidownload.database.RecordDatabase;

public class DownloadService extends Service {
    private final static String mChannelId = "cc.kafuu.bilidownload.DownloadService.channel";
    private final static String mChannelName = "DownloadService";

    private Handler mHandle = null;
    private NotificationManager mNotificationManager = null;

    private RecordDatabase mRecordDatabase;

    private final BlockingQueue<DownloadTask> mTaskQueue = new ArrayBlockingQueue<>(10);

    private final DownloadServiceBinder mBinder = new DownloadServiceBinder();

    private Thread mTaskProcessThread = null;

    private PendingIntent mMainActivityIntent;

    private DownloadTask mCurrentDownloadTask;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onCreate() {
        startService();
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startService();
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    /**
     * 初始化下载服务
     * */
    private void startService() {
        if (mTaskProcessThread != null) {
            return;
        }

        mHandle = new Handler(getMainLooper());
        mRecordDatabase = new RecordDatabase(getApplicationContext());

        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("DownloadNotification", true);
        mMainActivityIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

        if (mNotificationManager == null)
        {
            mNotificationManager = (NotificationManager) getSystemService(Service.NOTIFICATION_SERVICE);

            //Android8.0以上要求Service必须在通知栏显示，将Service运行在前台，否者出错
            if (Build.VERSION.SDK_INT > 25)
            {
                NotificationChannel channel = new NotificationChannel(mChannelId, mChannelName, NotificationManager.IMPORTANCE_DEFAULT);
                channel.enableVibration(false);
                channel.setSound(null, null);
                mNotificationManager.createNotificationChannel(channel);

                changeForeground(getText(R.string.app_name), null);
            }
        }

        mTaskProcessThread = new Thread(() -> {
            while (true) {
                try {
                    mCurrentDownloadTask = mTaskQueue.poll(5, TimeUnit.SECONDS);

                    if (mCurrentDownloadTask != null) {
                        if (mCurrentDownloadTask.getDownloader() != null) {
                            mCurrentDownloadTask.getDownloader().start();
                        }
                    } else {
                        stopForeground(false);
                        stopSelf();
                        break;
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            mTaskProcessThread = null;
        });

        mTaskProcessThread.start();
    }

    private static class DownloadTask {
        private final BiliDownloader downloader;
        private final RecordDatabase.DownloadRecord record;

        private int progress = 0;

        public DownloadTask(BiliDownloader downloader, RecordDatabase.DownloadRecord record) {
            this.downloader = downloader;
            this.record = record;
        }

        public BiliDownloader getDownloader() {
            return downloader;
        }

        public void setProgress(int progress) {
            this.progress = progress;
        }

        public int getProgress() {
            record.setDownloadProgress(progress);
            return progress;
        }

        public RecordDatabase.DownloadRecord getRecord() {
            return record;
        }
    }

    public class DownloadServiceBinder extends Binder {
        /**
         * 申请一个新的下载任务
         * */
        public void startDownload(final BiliVideo video, final BiliVideoPart part, final BiliVideoResource resource, final File save) {
            //取得资源下载源
            resource.download(save, new BiliVideoResource.GetDownloaderCallback() {
                @Override
                public void onCompleted(BiliDownloader downloader) {
                    RecordDatabase.DownloadRecord record = mRecordDatabase.newDownloadRecord(video.getVideoAddress(),
                            video.getTitle(),
                            part.getPartName(),
                            save.getPath(),
                            resource.getDescription() + " " + resource.getFormat(),
                            part.getPic());

                    try {
                        addDownloadTask(new DownloadTask(downloader, record));
                    } catch (IllegalStateException e) {
                        //任务开始失败，删除这个任务记录
                        mRecordDatabase.removeDownloadRecord(record);
                        mHandle.post(() -> Toast.makeText(getApplicationContext(), getString(R.string.download_queue_full), Toast.LENGTH_SHORT).show());
                    }
                    getApplication().sendBroadcast(new Intent("notice.download.progress.update"));
                }

                @Override
                public void onFailure(String message) {
                    //取下载源失败，通知失败原因
                    mHandle.post(() -> Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show());
                }
            });
        }
    }


    /**
     * 添加下载任务到队列
     * */
    private void addDownloadTask(final DownloadTask task) throws IllegalStateException {
        task.getDownloader().setCallback(new BiliDownloader.ResourceDownloadCallback() {
            @Override
            public void onStart() {
                onStartDownload(task);
            }

            @Override
            public void onStatus(long current, long contentLength) {
                int progress = (int) ((double)current / (double) contentLength * 1000.0D);
                if (task.getProgress() < progress) {
                    task.setProgress(progress);
                    onDownloadStatus(task);
                }
            }

            @Override
            public void onStop() {
                onDownloadTerminal(null, task);
            }

            @Override
            public void onCompleted(final File file) {
                onDownloadComplete(task);
            }

            @Override
            public void onFailure(String message) {
                onDownloadTerminal(message, task);
            }
        });

        mTaskQueue.add(task);
    }

    /**
     * 开始了一个下载任务
     * */
    private void onStartDownload(final DownloadTask task) {
        changeForeground(getText(R.string.resoult_downloading_title), task);
    }

    /**
     * 通知下载进度
     * */
    private void onDownloadStatus(final DownloadTask task) {
        mRecordDatabase.updateDownloadRecord(task.getRecord());
        getApplication().sendBroadcast(new Intent("notice.download.progress.update"));
    }

    /**
     * 下载成功后将调用此函数
     * */
    private void onDownloadComplete(final DownloadTask task) {
        task.setProgress(1000);
        mRecordDatabase.updateDownloadRecord(task.getRecord());
        changeForeground(getText(R.string.download_complete), task);
    }


    /**
     * 下载失败或下载取消调用此函数
     * */
    private void onDownloadTerminal(String failureMessage, final DownloadTask task) {
        changeForeground(getText(R.string.download_task_terminal), task);
        if (failureMessage != null) {

        }
    }

    /**
     * 取得普通文本通知构建器
     * */
    private Notification.Builder textNotifyBuilder(CharSequence title, CharSequence text) {
        Notification.Builder builder = null;
        if (Build.VERSION.SDK_INT > 25) {
            builder = new Notification.Builder(this, mChannelId);
        } else {
            builder = new Notification.Builder(this);
        }
        builder.setSmallIcon(R.drawable.ic_app).setContentTitle(title).setContentText(text);
        return builder;
    }

    /**
     * 改变通知显示
     * */
    private void changeForeground(CharSequence title, DownloadTask task) {
        Notification.Builder builder;
        if (task != null) {
            builder = textNotifyBuilder(title, task.getRecord().getPartTitle() + "-" + task.getRecord().getVideoTitle());
        } else {
            builder = textNotifyBuilder(title, "DownloadService");
        }
        builder.setContentIntent(mMainActivityIntent);
        startForeground(1, builder.build());
    }
}
