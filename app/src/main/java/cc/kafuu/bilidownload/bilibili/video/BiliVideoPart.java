package cc.kafuu.bilidownload.bilibili.video;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import cc.kafuu.bilidownload.bilibili.Bili;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class BiliVideoPart {

    private final BiliVideo mVideo;
    private final long mAv;
    private final long mCid;
    private final String mPic;
    private final String mPartName;
    private final String mPartDuration;

    public BiliVideoPart(BiliVideo video, long av, long cid, String pic, String partName, String partDuration) {
        this.mVideo = video;
        this.mAv = av;
        this.mCid = cid;
        this.mPic = pic;
        this.mPartName = partName;
        this.mPartDuration = partDuration;
    }

    public long getAv() {
        return mAv;
    }

    public long getCid() {
        return mCid;
    }

    public String getPic() {
        return mPic;
    }

    public String getPartDuration() {
        return mPartDuration;
    }

    public String getPartName() {
        return mPartName;
    }

    public BiliVideo getVideo() {
        return mVideo;
    }

    /**
     * 取得此视频所支持的清晰度视频资源
     * */
    public void getResource(GetResourceCallback callback) {
        Bili.httpClient.newCall(Bili.playUrlRequest(mCid, mAv, 0)).enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                callback.onFailure(e.getMessage());
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) {
                ResponseBody body = response.body();
                if (body == null) {
                    callback.onFailure("Request is returned empty");
                    return;
                }

                try {
                    String json = body.string();
                    JsonObject res = new Gson().fromJson(json, JsonObject.class);
                    if (res.get("code").getAsInt() != 0) {
                        callback.onFailure(res.get("message").getAsString());
                        return;
                    }

                    JsonObject data = res.get("data").getAsJsonObject();

                    JsonArray support_formats = data.getAsJsonArray("support_formats");

                    List<BiliVideoResource> resources = new ArrayList<>();

                    for (JsonElement element : support_formats) {
                        JsonObject format = element.getAsJsonObject();

                        int quality = format.get("quality").getAsInt();
                        String new_description = format.get("new_description").getAsString();

                        resources.add(new BiliVideoResource(BiliVideoPart.this, quality, format.get("format").getAsString(), new_description));
                    }

                    callback.onComplete(resources);

                } catch (Exception e) {
                    e.printStackTrace();
                    callback.onFailure(e.getMessage());
                }
            }
        });
    }

    public interface GetResourceCallback {
        void onComplete(List<BiliVideoResource> resources);
        void onFailure(String message);
    }
}
