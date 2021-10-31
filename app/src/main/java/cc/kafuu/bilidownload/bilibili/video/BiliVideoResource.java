package cc.kafuu.bilidownload.bilibili.video;

import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

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
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class BiliVideoResource {

    //清晰度
    private final int mQuality;
    //下载地址
    private final long mCid;
    private final long mAvid;
    //格式
    private final String mFormat;
    //描述
    private final String mDescription;

    protected BiliVideoResource(final int quality, final long cid, final long avid, final String format, final String description)
    {
        this.mQuality = quality;
        this.mCid = cid;
        this.mAvid = avid;
        this.mFormat = format;
        this.mDescription = description;
    }

    public int getQuality() {
        return mQuality;
    }

    public String getDescription() {
        return mDescription;
    }

    public String getFormat() {
        return mFormat;
    }

    public interface GetDownloaderCallback {
        void onCompleted(BiliDownloader downloader);
        void onFailure(String message);
    }

    /**
     * 取得下载此资源的下载器
     *
     * @param savePath 下载保存路径
     *
     * @param callback 下载状态回调
     * */
    public void download(final File savePath, final GetDownloaderCallback callback)
    {
        Bili.httpClient.newCall(Bili.playUrlRequest(mCid, mAvid, mQuality)).enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                e.printStackTrace();
                callback.onFailure(e.getMessage());
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                ResponseBody body = response.body();
                if (body == null) {
                    callback.onFailure("No data returned");
                    return;
                }

                JsonObject result = new Gson().fromJson(body.string(), JsonObject.class);
                if (result.get("code").getAsInt() != 0) {
                    callback.onFailure(result.get("message").getAsString());
                    return;
                }

                JsonObject data = result.getAsJsonObject("data");
                if (data.get("quality").getAsInt() != mQuality) {
                    callback.onFailure("您还未登录或当前登录的账户不支持下载此视频");
                    return;
                }

                JsonArray durl = data.getAsJsonArray("durl");
                if (durl.size() == 0) {
                    callback.onFailure("Video player source is empty");
                    return;
                }

                callback.onCompleted(new BiliDownloader(savePath, durl.get(0).getAsJsonObject().get("url").getAsString()));
            }
        });

    }

}
