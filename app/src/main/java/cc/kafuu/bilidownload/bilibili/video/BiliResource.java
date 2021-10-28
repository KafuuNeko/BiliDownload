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
import okhttp3.Headers;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class BiliResource {

    private final static Headers mDownloadHead1 = new Headers.Builder()
            .add("accept", "*/*")
            .add("accept-encoding", "gzip, deflate, br")
            .add("Accept-Language", "zh-CN,zh;q=0.8,zh-TW;q=0.7,zh-HK;q=0.5,en-US;q=0.3,en;q=0.2")
            .add("Connection", "keep-alive")
            .add("Origin", "https://www.bilibili.com")
            .add("sec-fetch-dest", "empty")
            .add("sec-fetch-mode", "cors")
            .add("sec-fetch-site", "cross-site")
            .add("user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:93.0) Gecko/20100101 Firefox/93.0")
            .build();

    private final static Headers mDownloadHead2 = new Headers.Builder()
            .add("accept", "*/*")
            .add("accept-encoding", "gzip, deflate, br")
            .add("Accept-Language", "zh-CN,zh;q=0.8,zh-TW;q=0.7,zh-HK;q=0.5,en-US;q=0.3,en;q=0.2")
            .add("Connection", "keep-alive")
            .add("Origin", "https://www.bilibili.com")
            .add("sec-fetch-dest", "empty")
            .add("sec-fetch-mode", "cors")
            .add("sec-fetch-site", "cross-site")
            .add("user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:93.0) Gecko/20100101 Firefox/93.0")
            .build();


    //资源id
    private final int mId;
    //视频地址
    private final String mVideoUrl;
    //下载地址
    private final String mDownloadUrl;
    //描述
    private final String mDescription;

    protected BiliResource(int id, final String videoUrl, final String url, final String description)
    {
        this.mId = id;
        this.mVideoUrl = videoUrl;
        this.mDownloadUrl = url;
        this.mDescription = description;
    }

    public int getId() {
        return mId;
    }

    public String getUrl() {
        return mDownloadUrl;
    }

    public String getDescription() {
        return mDescription;
    }

    /**
     * @param savePath 下载保存路径
     *
     * @param callback 下载状态回调
     * */
    public void download(final String savePath, final ResourceDownloadCallback callback)
    {
        final OkHttpClient client = Bili.httpClient;

        //发起一个预检请求
        Request options_request = new Request.Builder().url(mDownloadUrl)
                .headers(mDownloadHead1).addHeader("Referer", mVideoUrl)
                .method("OPTIONS", new FormBody.Builder().build())
                .build();

        client.newCall(options_request).enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                callback.onFailure(e.getMessage());
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                Log.d("Bili Options", "code: " + response.code());
                if (response.code() == 200) {
                    //预检通过
                    startDownload(savePath, callback);
                } else {
                    callback.onFailure("Options request return code " + response.code());
                }
            }
        });

    }

    /**
     * 预检请求通过后调用此过程开始下载
     * */
    private void startDownload(final String savePath, final ResourceDownloadCallback callback)
    {
        final OkHttpClient client = Bili.httpClient;

        Request request = new Request.Builder().url(mDownloadUrl)
                .headers(mDownloadHead2).addHeader("Referer", mVideoUrl)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                callback.onFailure(e.getMessage());
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                ResponseBody body = response.body();
                if (body == null) {
                    callback.onFailure("Body is empty");
                    return;
                }

                int contentLength = Integer.parseInt(Objects.requireNonNull(response.header("content-length")));

                try {
                    InputStream inputStream = body.byteStream();
                    OutputStream outputStream = new FileOutputStream(savePath);

                    byte[] buf = new byte[1024];
                    int len, cur = 0;
                    while ((len = inputStream.read(buf)) != -1)
                    {
                        cur += len;
                        outputStream.write(buf, 0, len);
                        callback.onStatus(cur, contentLength);
                    }

                    callback.onComplete(new File(savePath));

                } catch (IOException e) {
                    callback.onFailure(e.getMessage());
                }
            }
        });
    }
}
