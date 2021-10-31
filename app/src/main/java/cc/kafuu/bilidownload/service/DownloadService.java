package cc.kafuu.bilidownload.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import cc.kafuu.bilidownload.R;
import cc.kafuu.bilidownload.bilibili.video.BiliDownloader;
import cc.kafuu.bilidownload.bilibili.video.BiliVideo;
import cc.kafuu.bilidownload.bilibili.video.BiliVideoPart;
import cc.kafuu.bilidownload.bilibili.video.BiliVideoResource;
import cc.kafuu.bilidownload.bilibili.video.ResourceDownloadCallback;
import cc.kafuu.bilidownload.utils.Pair;
import cc.kafuu.bilidownload.utils.RecordDatabase;

public class DownloadService extends Service {
    private final static String mChannelId = "cc.kafuu.bilidownload.DownloadService.channel";
    private final static String mChannelName = "DownloadService";

    private Handler mHandle = null;

    private NotificationManager mNotificationManager = null;

    private RecordDatabase mRecordDatabase;

    private final Map<Integer, BiliDownloader> mDownloaderMap = new HashMap<>();

    private static int mLastNotifyId = 1;
    synchronized private static int getNotifyId() {
        return mLastNotifyId++;
    }

    public class DownloadServiceBinder extends Binder {
        public void startDownload(final BiliVideo video, final BiliVideoPart part, final BiliVideoResource resource, File save) {
            new Thread(() -> newDownload(video, part, resource, save)).start();
        }
    }
    private final DownloadServiceBinder mBinder = new DownloadServiceBinder();

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d("DownloadService", "onCreate");

        mHandle = new Handler(getMainLooper());

        mRecordDatabase = new RecordDatabase(getApplicationContext());

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

                startForeground(getNotifyId(), new Notification.Builder(this, channel.getId())
                        .setSmallIcon(R.drawable.ic_app)
                        .setContentText("BVD后台下载服务运行中")
                        .build()
                );
            }

        }

    }

    private void newDownload(final BiliVideo video, final BiliVideoPart part, final BiliVideoResource resource, File save) {

        final int notifyId = getNotifyId();

        Pair<Integer, Integer> lastProgress = new Pair<>(0, 1000);
        BiliDownloader downloader = resource.download(save, new ResourceDownloadCallback() {
            @Override
            public void onStatus(long current, long contentLength) {
                int progress = (int) ((double)current / (double) contentLength * 1000.0D);
                if (lastProgress.first < progress) {
                    lastProgress.first = progress;
                    mHandle.post(() -> notifyProgress(video, part, notifyId, progress));
                }
            }

            @Override
            public void onStop() {
                mDownloaderMap.remove(notifyId);
                mHandle.post(() -> mNotificationManager.cancel(notifyId));
            }

            @Override
            public void onCompleted(final File file) {
                mDownloaderMap.remove(notifyId);
                mHandle.post(() -> {
                    mNotificationManager.cancel(notifyId);
                    onDownloadComplete(video, part, file, resource);
                });
            }

            @Override
            public void onFailure(String message) {
                mDownloaderMap.remove(notifyId);
                mHandle.post(() -> mNotificationManager.cancel(notifyId));
                mHandle.post(() -> Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show());
            }
        });

        if (downloader != null) {
            mDownloaderMap.put(notifyId, downloader);
            mHandle.post(() -> notifyProgress(video, part, notifyId, 0));
            downloader.start();
        }
    }

    /**
     * 通知下载进度
     * */
    private void notifyProgress(final BiliVideo video, final BiliVideoPart part, int id, int progress) {
        Notification.Builder builder = null;
        if (Build.VERSION.SDK_INT > 25) {
            builder = new Notification.Builder(this, mChannelId);
        } else {
            builder = new Notification.Builder(this);
        }
        builder.setContentTitle(video.getTitle() + " " + part.getPartName());
        builder.setSmallIcon(R.drawable.ic_app);
        builder.setProgress(1000, progress, false);

        mNotificationManager.notify(id, builder.build());
    }

    /**
     * 下载成功后将调用此函数
     * */
    private void onDownloadComplete(final BiliVideo video, final BiliVideoPart part, File file, BiliVideoResource resource) {
        mRecordDatabase.insertDownloadRecord(video.getVideoAddress(), video.getTitle(), part.getPartName(), file.getPath(), resource.getDescription() + " " + resource.getFormat(), part.getPic());
        Toast.makeText(getApplicationContext(), R.string.download_complete, Toast.LENGTH_SHORT).show();
        getApplication().sendBroadcast(new Intent("notice.download.completed"));
    }

}
