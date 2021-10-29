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
import cc.kafuu.bilidownload.bilibili.VideoParsingCallback;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class BiliVideo {
    /**
     * 通过BV号解析下载地址
     * */
    public static void fromBv(final String bv, final VideoParsingCallback callback) {
        try {
            //构造访问
            Request request = new Request.Builder()
                    .url("https://api.bilibili.com/x/web-interface/view/detail?aid=&bvid=" + bv.substring(2))
                    .headers(Bili.generalHeaders)
                    .build();
            Bili.httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NotNull Call call, @NotNull IOException e) {
                    callback.onFailure(e.getMessage());
                }

                @Override
                public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                    analyzingResponse(response, callback);
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
            callback.onFailure(e.getMessage());
        }
    }

    /**
     * 通过AV号解析下载地址
     * */
    public static void fromAv(final String av, final VideoParsingCallback callback) {
        try {
            //构造访问
            Request request = new Request.Builder()
                    .url("https://api.bilibili.com/x/web-interface/view/detail?aid=" + av + "&bvid=&recommend_type=&need_rcmd_reason=1")
                    .headers(Bili.generalHeaders)
                    .build();
            Bili.httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NotNull Call call, @NotNull IOException e) {
                    callback.onFailure(e.getMessage());
                }

                @Override
                public void onResponse(@NotNull Call call, @NotNull Response response) {
                    try {
                        analyzingResponse(response, callback);
                    } catch (Exception e) {
                        callback.onFailure(e.getMessage());
                    }
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
     * */
    private static void analyzingResponse(@NonNull Response response, final VideoParsingCallback callback) throws IOException {
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

        JsonObject data = res.get("data").getAsJsonObject();
        if (data == null) {
            callback.onFailure("Video data is returned empty");
        } else {
            callback.onComplete(new BiliVideo(data));
        }
    }


    private final String mBv;
    private final long mAid;
    private final String mPicUrl;
    private final String mTitle;
    private final String mDesc;
    private final List<BiliVideoPart> mParts;

    private BiliVideo(@NonNull JsonObject data) {
        JsonObject view = data.getAsJsonObject("View");

        mBv = view.get("bvid").getAsString();
        mAid = view.get("aid").getAsLong();
        mPicUrl = view.get("pic").getAsString();
        mTitle = view.get("title").getAsString();
        mDesc = view.get("desc").getAsString();

        mParts = new ArrayList<>();
        for (JsonElement element : view.getAsJsonArray("pages")) {
            JsonObject page = element.getAsJsonObject();

            long cid = page.get("cid").getAsLong();
            String partName = page.get("part").getAsString();
            String partDuration = page.get("duration").getAsString();

            mParts.add(new BiliVideoPart(mAid, cid, partName, partDuration));
        }
    }

    public String getBv() {
        return mBv;
    }

    public long getAid() {
        return mAid;
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
        return "Bv: " + getBv() + ", Aid: " + getAid() + ", Title: " + getTitle() + ", Desc: " + getDesc() + ", Pic: " + getPicUrl();
    }
}
