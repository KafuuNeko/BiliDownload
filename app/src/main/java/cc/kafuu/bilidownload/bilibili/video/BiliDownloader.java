package cc.kafuu.bilidownload.bilibili.video;

import android.app.DownloadManager;
import android.net.Uri;
import android.util.Log;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;

import cc.kafuu.bilidownload.bilibili.Bili;
import kotlin.Pair;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Bili资源下载器
 * */
public class BiliDownloader {
    private static final String TAG = "BiliDownloader";

    private final String mVideoTitle;
    private final String mVideoDescription;
    private final File mSavePath;
    private final String mResourceUrl;

    /**
     * 构造资源下载器
     *
     * @param savePath 资源下载保存路径
     *
     * @param resourceUrl 资源下载地址
     *
     * */
    public BiliDownloader(String videoTitle, String videoDescription, File savePath, String resourceUrl) {
        this.mVideoTitle = videoTitle;
        this.mVideoDescription = videoDescription;
        this.mSavePath = savePath;
        this.mResourceUrl = resourceUrl;
    }

    public File getSavePath() {
        return mSavePath;
    }

    public interface GetDownloadIdCallback {
        void failure(String message);
        void completed(long id);
    }



    public void getDownloadId(DownloadManager downloadManager, GetDownloadIdCallback callback) {
        //发起一个预检请求
        Log.d(TAG, "getDownloadId: options request " + mResourceUrl);
        Request optionsRequest = new Request.Builder().url(mResourceUrl).headers(Bili.downloadHeaders)
                .method("OPTIONS", new FormBody.Builder().build())
                .build();
        Bili.httpClient.newCall(optionsRequest).enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                Log.d(TAG, "options request failure: " + e.getMessage());
                callback.failure(e.getMessage());
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) {
                if (response.code() != 200) {
                    if (callback != null) {
                        callback.failure("Options request return code " + response.code());
                    }
                    return;
                }

                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(mResourceUrl));
                for (Pair<? extends String, ? extends String> item : Bili.downloadHeaders){
                    request.addRequestHeader(item.getFirst(), item.getSecond());
                }

                //设置漫游条件下是否可以下载
                request.setAllowedOverRoaming(false);
                //在通知栏中显示
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE);
                //设置通知标题
                request.setTitle(mVideoTitle);
                //设置通知标题message
                request.setDescription(mVideoTitle + "-" + mVideoDescription);
                //下载保存地址
                request.setDestinationUri(Uri.fromFile(mSavePath));

                callback.completed(downloadManager.enqueue(request));
            }
        });
    }

}
