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
import cc.kafuu.bilidownload.bilibili.video.callback.IGetDownloaderCallback;
import cc.kafuu.bilidownload.database.VideoInfo;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class BiliVideoResourceParser {
    private static final String TAG = "BiliVideoResource";

    private final BiliVideoPartParser mPart;
    //清晰度
    private final int mQuality;
    //格式
    private final String mFormat;
    //描述
    private final String mDescription;

    protected BiliVideoResourceParser(BiliVideoPartParser part, final int quality, final String format, final String description)
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

    public BiliVideoPartParser getPart() {
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


    public static void getDownloadUrl(String videoTitle, String partTitle, long cid, long avid, int quality, final File saveTo, IGetDownloaderCallback callback) {
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

                JsonObject dash_data = data.getAsJsonObject("dash");
                if (dash_data == null) {
                    callback.failure("Video player source is empty");
                    return;
                }

                JsonArray videos = dash_data.getAsJsonArray("video");
                if (videos.size() == 0) {
                    callback.failure("Video player source is empty");
                    return;
                }

                for (int i = 0; i < videos.size(); i++) {
                    JsonObject video = videos.get(i).getAsJsonObject();
                    if (video.get("id").getAsInt() == quality) {
                        JsonArray urls = video.getAsJsonArray("backup_url");
                        if (urls.size() == 0) {
                            callback.failure("Video player source is empty");
                            return;
                        }
                        String url = urls.get(0).getAsString();
                        callback.completed(new BiliVideoDownloader(videoTitle, partTitle, saveTo, url));
                        return;
                    }
                }

                callback.failure("Video player source is empty");
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
    public void download(final IGetDownloaderCallback callback, File saveTo)
    {
        if (saveTo == null) {
            String fileSuffix = mFormat.contains("flv") ? "flv" : mFormat;
            saveTo = new File(Bili.saveDir + "/Video/" + getAvid() + "/" + getCid() + "/" + getQuality() + "/" + new Date().getTime() + "." + fileSuffix);
        }

        getDownloadUrl(getPart().getVideo().getTitle(), getPart().getPartName(), getCid(), getAvid(), getQuality(), saveTo, callback);
    }

}
