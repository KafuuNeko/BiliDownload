package cc.kafuu.bilidownload.bilibili.video;

import android.util.Log;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;

import cc.kafuu.bilidownload.bilibili.Bili;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class BiliDownloader {

    public enum Status {
        NO_OPS, DOWNLOADING, REQUEST_STOP
    }

    private File mSavePath;
    private String mResourceUrl;
    private ResourceDownloadCallback mCallback;

    private Status mStatus = Status.NO_OPS;

    public BiliDownloader(File savePath, String resourceUrl, ResourceDownloadCallback callback) {
        this.mSavePath = savePath;
        this.mResourceUrl = resourceUrl;
        this.mCallback = callback;
    }

    public void start() {
        Log.d("Bili", "start download: " + mResourceUrl);
        new Thread(() -> {
            try {
                download();
            } catch (Exception e) {
                e.printStackTrace();
                mCallback.onFailure(e.getMessage());
            } finally {
                mStatus = Status.NO_OPS;
            }
        }).start();
    }

    synchronized private void download() throws IOException {
        if (mStatus != Status.NO_OPS) {
            mCallback.onFailure("Download task is running");
            return;
        }

        mStatus = Status.DOWNLOADING;
        //发起一个预检请求
        Request options_request = new Request.Builder().url(mResourceUrl).headers(Bili.downloadHeaders)
                .method("OPTIONS", new FormBody.Builder().build())
                .build();
        Response options_response = Bili.httpClient.newCall(options_request).execute();
        if (options_response.code() != 200) {
            mCallback.onFailure("Options request return code " + options_response.code());
            return;
        } else {
            Log.d("Bili", "Options complete");
        }


        Request request = new Request.Builder().url(mResourceUrl).headers(Bili.downloadHeaders).build();
        Response response = Bili.httpClient.newCall(request).execute();
        if (response.code() != 200) {
            mCallback.onFailure("Request return code " + response.code());
            return;
        }

        ResponseBody body = response.body();
        if (body == null) {
            mCallback.onFailure("Body is empty");
            return;
        }

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
            mCallback.onStatus(cur, contentLength);
        }

        outputStream.close();

        if (mStatus == Status.REQUEST_STOP) {
            if (mSavePath.delete()) {
                Log.e("BiliVideoResource.startSave", "Failed to clear invalid files");
            }
            mCallback.onStop();
        } else {
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

}
