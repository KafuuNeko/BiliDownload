package cc.kafuu.bilidownload.bilibili.video;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import cc.kafuu.bilidownload.bilibili.Bili;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class BiliVideo {
    /**
     * 通过BV号解析下载地址
     * */
    public static void fromVideoId(final String videoId, final VideoParsingCallback callback) {
        if (videoId.length() < 3) {
            callback.onFailure("Id format error");
            return;
        }

        //通过给定Id类型选择Api
        String type = videoId.substring(0, 2);
        String requestUrl;

        switch (type) {
            case "ss":
                requestUrl = "https://api.bilibili.com/pgc/view/web/h5/season?season_id=" + videoId.substring(2);
                break;
            case "ep":
                requestUrl = "https://api.bilibili.com/pgc/view/web/h5/season?ep_id=" + videoId.substring(2);
                break;
            case "BV":
                requestUrl = "https://api.bilibili.com/x/web-interface/view/detail?aid=&bvid=" + videoId;
                break;
            case "av":
                requestUrl = "https://api.bilibili.com/x/web-interface/view/detail?aid=" + videoId.substring(2) + "&bvid=";
                break;
            default:
                callback.onFailure("Id format error");
                return;
        }

        //判断使用的Api是不是https://api.bilibili.com/pgc/view/web/h5/season
        //不同的Api返回结果解析方式也不同
        boolean isSeason = type.equals("ss") | type.equals("ep");

        try {
            //构造访问
            Request request = new Request.Builder().url(requestUrl).headers(Bili.generalHeaders).build();
            Bili.httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NotNull Call call, @NotNull IOException e) {
                    callback.onFailure(e.getMessage());
                }

                @Override
                public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                    //取得访问数据，交予analyzingResponse进行处理
                    analyzingResponse(response, callback, isSeason);
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
            callback.onFailure(e.getMessage());
        }
    }

    /**
     * 分析Api返回内容
     * 分析成功将构造BiliVideo并回调
     *
     * @param response response
     *
     * @param callback 分析回调
     *
     * @param isSeason 是否是ss或ep，不同协议结果不同，分析方式不一样
     * */
    private static void analyzingResponse(@NonNull Response response, final VideoParsingCallback callback, boolean isSeason) throws IOException {
        ResponseBody body = response.body();
        if (body == null) {
            callback.onFailure("Request is returned empty");
            return;
        }

        String json = body.string();

        Log.d("BiliVideo.analyzingResponse->json", json);

        JsonObject res = new Gson().fromJson(json, JsonObject.class);

        if (res.get("code").getAsInt() != 0) {
            callback.onFailure(res.get("message").getAsString());
            return;
        }

        JsonObject data = isSeason ? res.getAsJsonObject("result") : res.getAsJsonObject("data");
        if (data == null) {
            callback.onFailure("Video data is returned empty");
        } else {
            callback.onCompleted(new BiliVideo(data, isSeason));
        }
    }


    //视频地址，如BV1QN411o73X、ss34415、ep835
    private final String mVideoAddress;
    //视频ID，BV则是它对应的AV号，av、ss、ep则是后面的数字
    private final long mVideoId;
    //视频封面图片
    private final String mPicUrl;
    //视频标题
    private final String mTitle;
    //视频描述
    private final String mDesc;
    //视频片段（分集）
    private final List<BiliVideoPart> mParts;

    private BiliVideo(@NonNull JsonObject data, boolean isSeason) {
        mParts = new ArrayList<>();

        if (!isSeason) {
            JsonObject view = data.getAsJsonObject("View");

            mVideoAddress = view.get("bvid").getAsString();
            mVideoId = view.get("aid").getAsLong();
            mPicUrl = view.get("pic").getAsString();
            mTitle = view.get("title").getAsString();
            mDesc = view.get("desc").getAsString();

            for (JsonElement element : view.getAsJsonArray("pages")) {
                JsonObject page = element.getAsJsonObject();

                long cid = page.get("cid").getAsLong();
                String partName = page.get("part").getAsString();
                String partDuration = page.get("duration").getAsString();

                mParts.add(new BiliVideoPart(BiliVideo.this, mVideoId, cid, mPicUrl, partName, partDuration));
            }
        } else {
            mVideoAddress = "ss" + data.get("season_id").getAsLong();
            mVideoId = data.get("season_id").getAsLong();
            mPicUrl = data.get("cover").getAsString();
            mTitle = data.get("season_title").getAsString();
            mDesc = data.get("evaluate").getAsString();

            for (JsonElement element : data.getAsJsonArray("episodes")) {
                JsonObject episode = element.getAsJsonObject();
                long cid = episode.get("cid").getAsLong();
                String partName = episode.get("long_title").getAsString();
                String partDuration = episode.get("share_copy").getAsString();

                mParts.add(new BiliVideoPart(BiliVideo.this, episode.get("aid").getAsLong(), cid, episode.get("cover").getAsString(), partName, partDuration));
            }
        }
    }

    public String getVideoAddress() {
        return mVideoAddress;
    }

    public long getVideoId() {
        return mVideoId;
    }

    public String getTitle() {
        return mTitle;
    }

    public String getDesc() {
        return mDesc;
    }

    public String getPicUrl() {
        return mPicUrl;
    }

    public List<BiliVideoPart> getParts() {
        return mParts;
    }

    @NotNull
    @Override
    public String toString() {
        return "VideoAddress: " + getVideoAddress() + ", VideoId: " + getVideoId() + ", Title: " + getTitle() + ", Desc: " + getDesc() + ", Pic: " + getPicUrl();
    }

    public interface VideoParsingCallback {
        void onCompleted(BiliVideo biliVideos);
        void onFailure(String message);
    }
}
