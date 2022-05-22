package cc.kafuu.bilidownload.bilibili.video;

import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.Date;

import cc.kafuu.bilidownload.bilibili.Bili;
import cc.kafuu.bilidownload.database.VideoInfo;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class BiliVideoResource {
    private static final String TAG = "BiliVideoResource";

    private final BiliVideoPart mPart;
    //清晰度
    private final int mQuality;
    //格式
    private final String mFormat;
    //描述
    private final String mDescription;

    protected BiliVideoResource(BiliVideoPart part, final int quality, final String format, final String description)
    {
        this.mPart = part;
        this.mQuality = quality;
        this.mFormat = format;
        this.mDescription = description;

        updateRecord();
    }

    private void updateRecord() {
        VideoInfo info = new VideoInfo(getPart().getAv(), getPart().getCid(), getQuality());

        info.setFormat(getFormat());
        info.setQualityDescription(getDescription());

        info.setVideoTitle(getPart().getVideo().getTitle());
        info.setVideoDescription(getPart().getVideo().getDesc());
        info.setVideoPic(getPart().getVideo().getPicUrl());

        info.setPartTitle(getPart().getPartName());
        info.setPartDescription(getPart().getPartDuration());
        info.setPartPic(getPart().getPic());

        info.saveOrUpdate();
    }

    public BiliVideoPart getPart() {
        return mPart;
    }

    public long getAvid() {
        return mPart.getAv();
    }

    public long getCid() {
        return mPart.getCid();
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
        void completed(BiliDownloader downloader);
        void failure(String message);
    }

    public static void getDownloadUrl(String videoTitle, String partTitle, long cid, long avid, int quality, final File saveTo, GetDownloaderCallback callback) {
        Bili.httpClient.newCall(Bili.playUrlRequest(cid, avid, quality)).enqueue(new Callback() {
            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                ResponseBody body = response.body();
                if (body == null) {
                    callback.failure("No data returned");
                    return;
                }

                JsonObject result = new Gson().fromJson(body.string(), JsonObject.class);
                if (result.get("code").getAsInt() != 0) {
                    callback.failure(result.get("message").getAsString());
                    return;
                }

                JsonObject data = result.getAsJsonObject("data");
                if (data.get("quality").getAsInt() != quality) {
                    callback.failure("您还未登录或当前登录的账户不支持下载此视频");
                    return;
                }

                JsonArray durl = data.getAsJsonArray("durl");
                if (durl.size() == 0) {
                    callback.failure("Video player source is empty");
                    return;
                }

                String url = durl.get(0).getAsJsonObject().get("url").getAsString();

                callback.completed(new BiliDownloader(videoTitle, partTitle, saveTo, url));
            }

            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                Log.d(TAG, "onFailure: " + e.getMessage());
                e.printStackTrace();
                callback.failure(e.getMessage());
            }
        });
    }

    /**
     * 取得下载此资源的下载器
     *
     * @param callback 下载状态回调
     * */
    public void download(final GetDownloaderCallback callback, File saveTo)
    {
        if (saveTo == null) {
            String fileSuffix = mFormat.contains("flv") ? "flv" : mFormat;
            saveTo = new File(Bili.saveDir + "/Video/" + getAvid() + "/" + getCid() + "/" + getQuality() + "/" + new Date().getTime() + "." + fileSuffix);
        }

        getDownloadUrl(getPart().getVideo().getTitle(), getPart().getPartName(), getCid(), getAvid(), getQuality(), saveTo, callback);
    }

}
