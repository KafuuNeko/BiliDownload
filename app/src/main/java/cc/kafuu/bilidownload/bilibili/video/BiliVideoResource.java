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

public class BiliVideoResource {

    private final static Headers mDownloadHead = new Headers.Builder()
            .add("accept", "*/*")
            .add("accept-encoding", "gzip, deflate, br")
            .add("Accept-Language", "zh-CN,zh-Hans;q=0.9")
            .add("Connection", "keep-alive")
            .add("Origin", "https://m.bilibili.com")
            .add("user-agent", Bili.UA)
            .build();

    //视频地址
    private final String mRefererUrl;
    //下载地址
    private final String mResourceUrl;
    //格式
    private final String mFormat;
    //描述
    private final String mDescription;

    protected BiliVideoResource(final String Referer, final String resource, final String format, final String description)
    {
        this.mRefererUrl = Referer;
        this.mResourceUrl = resource;
        this.mFormat = format;
        this.mDescription = description;
    }

    public String getResourceUrl() {
        return mResourceUrl;
    }

    public String getDescription() {
        return mDescription;
    }

    public String getFormat() {
        return mFormat;
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
        Request options_request = new Request.Builder().url(mResourceUrl)
                .headers(mDownloadHead).addHeader("Referer", mRefererUrl)
                .method("OPTIONS", new FormBody.Builder().build())
                .build();

        client.newCall(options_request).enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                callback.onFailure(e.getMessage());
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                Log.d("BiliResource.download->Options", "code: " + response.code());
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

        Request request = new Request.Builder().url(mResourceUrl)
                .headers(mDownloadHead).addHeader("Referer", mRefererUrl)
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
