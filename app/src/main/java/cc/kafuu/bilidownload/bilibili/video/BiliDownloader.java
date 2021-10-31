package cc.kafuu.bilidownload.bilibili.video;

import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;

import cc.kafuu.bilidownload.bilibili.Bili;
import okhttp3.FormBody;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Bili资源下载器
 * */
public class BiliDownloader {

    public enum Status {
        NO_OPS, DOWNLOADING, REQUEST_STOP
    }

    private final File mSavePath;
    private final String mResourceUrl;
    private ResourceDownloadCallback mCallback = null;

    private Status mStatus = Status.NO_OPS;

    /**
     * 构造资源下载器
     *
     * @param savePath 资源下载保存路径
     *
     * @param resourceUrl 资源下载地址
     *
     * */
    public BiliDownloader(File savePath, String resourceUrl) {
        this.mSavePath = savePath;
        this.mResourceUrl = resourceUrl;
    }

    public void setCallback(ResourceDownloadCallback callback) {
        this.mCallback = callback;
    }

    /**
     * 异步开始下载资源
     * 将开启一个线程开始下载资源
     * */
    public void start_async() {
        Log.d("Bili", "start download: " + mResourceUrl);
        new Thread(this::start).start();
    }

    public void start() {
        try {
            download();
        } catch (Exception e) {
            e.printStackTrace();
            if (mCallback != null) {
                mCallback.onFailure(e.getMessage());
            }

        } finally {
            mStatus = Status.NO_OPS;
        }
    }

    /**
     * 资源下载过程函数（在线程执行）
     * */
    synchronized private void download() throws IOException {
        if (mStatus != Status.NO_OPS) {
            if (mCallback != null) {
                mCallback.onFailure("Download task is running");
            }
            return;
        }

        if (mCallback != null) {
            mCallback.onStart();
        }

        mStatus = Status.DOWNLOADING;
        //发起一个预检请求
        Request options_request = new Request.Builder().url(mResourceUrl).headers(Bili.downloadHeaders)
                .method("OPTIONS", new FormBody.Builder().build())
                .build();
        Response options_response = Bili.httpClient.newCall(options_request).execute();
        if (options_response.code() != 200) {
            if (mCallback != null) {
                mCallback.onFailure("Options request return code " + options_response.code());
            }
            return;
        } else {
            Log.d("Bili", "Options complete");
        }


        //预检通过，开始请求资源
        Request request = new Request.Builder().url(mResourceUrl).headers(Bili.downloadHeaders).build();
        Response response = Bili.httpClient.newCall(request).execute();
        if (response.code() != 200) {
            if (mCallback != null) {
                mCallback.onFailure("Request return code " + response.code());
            }
            return;
        }

        ResponseBody body = response.body();
        if (body == null) {
            if (mCallback != null) {
                mCallback.onFailure("Body is empty");
            }
            return;
        }

        //取得资源总长度
        long contentLength = Long.parseLong(Objects.requireNonNull(response.header("content-length")));
        Log.d("Bili", "contentLength=" + contentLength);

        InputStream inputStream = body.byteStream();
        OutputStream outputStream = new FileOutputStream(mSavePath);

        byte[] buf = new byte[4096];
        int len;
        long cur = 0;
        while ((len = inputStream.read(buf)) != -1 && mStatus == Status.DOWNLOADING)
        {
            cur += len;
            outputStream.write(buf, 0, len);
            if (mCallback != null) {
                mCallback.onStatus(cur, contentLength);
            }
        }

        outputStream.close();

        if (mStatus == Status.REQUEST_STOP) {
            //如果下载是被用户请求停止的，则删除下载的文件
            if (!mSavePath.delete()) {
                Log.e("BiliVideoResource.startSave", "Failed to clear invalid files");
            }
            if (mCallback != null) {
                mCallback.onStop();
            }
        } else if (mCallback != null){
            mCallback.onCompleted(mSavePath);
        }
    }

    /**
     * 请求停止下载
     * 当下载停止后将调用ResourceDownloadCallback.onStop回调
     * */
    public void requestStop() {
        if (mStatus == Status.DOWNLOADING) {
            mStatus = Status.REQUEST_STOP;
        }
    }

    public static interface ResourceDownloadCallback {
        void onStart();
        void onStatus(long current, long contentLength);
        void onStop();
        void onCompleted(File file);
        void onFailure(String message);
    }
}
