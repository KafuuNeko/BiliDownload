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

    //0无操作，1正在下载，2请求停止
    private volatile int mSaveStatus = 0;

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

    /**
     * @param savePath 下载保存路径
     *
     * @param callback 下载状态回调
     * */
    public BiliDownloader download(final File savePath, final ResourceDownloadCallback callback)
    {
        try {
            Response response = Bili.httpClient.newCall(Bili.playUrlRequest(mCid, mAvid, mQuality)).execute();
            ResponseBody body = response.body();
            if (body == null) {
                callback.onFailure("No data returned");
                return null;
            }

            JsonObject result = new Gson().fromJson(body.string(), JsonObject.class);
            if (result.get("code").getAsInt() != 0) {
                callback.onFailure(result.get("message").getAsString());
                return null;
            }

            JsonObject data = result.getAsJsonObject("data");
            if (data.get("quality").getAsInt() != mQuality) {
                callback.onFailure("Video quality is inconsistent");
                return null;
            }

            JsonArray durl = data.getAsJsonArray("durl");
            if (durl.size() == 0) {
                callback.onFailure("Video player source is empty");
                return null;
            }

            return new BiliDownloader(savePath, durl.get(0).getAsJsonObject().get("url").getAsString(), callback);
        } catch (Exception e) {
            e.printStackTrace();
            callback.onFailure(e.getMessage());
        }

        return null;
    }

}
