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
import okhttp3.OkHttp;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class BiliPlayInfo {

    private long mAv;
    private long mCid;
    private String mPartName;
    private String mPartDuration;

    public BiliPlayInfo(long av, long cid, String partName, String partDuration) {
        this.mAv = av;
        this.mCid = cid;
        this.mPartName = partName;
        this.mPartDuration = partDuration;
    }

    public long getAv() {
        return mAv;
    }

    public long getCid() {
        return mCid;
    }

    public String getPartDuration() {
        return mPartDuration;
    }

    public String getPartName() {
        return mPartName;
    }

    public void getResource(GetResourceCallback callback) {
        Request request = playUrlRequest(0);
        Bili.httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                callback.onFailure(e.getMessage());
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
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
                    JsonArray accept_quality = data.getAsJsonArray("accept_quality");
                    JsonArray accept_description = data.getAsJsonArray("accept_description");

                    List<BiliResource> resources = new ArrayList<>();

                    for (int i = 0; i < accept_quality.size(); ++i) {
                        BiliResource resource = analysisPlayUrl(
                                accept_quality.get(i).getAsInt(),
                                accept_description.get(i).getAsString(),
                                callback
                        );
                        if (resource == null) {
                            return;
                        }
                        resources.add(resource);
                    }

                    callback.onComplete(resources);

                } catch (Exception e) {
                    e.printStackTrace();
                    callback.onFailure(e.getMessage());
                }
            }
        });
    }

    private Request playUrlRequest(int quality) {
        String url = "https://api.bilibili.com/x/player/playurl?cid=" + this.mCid + "&avid=" + this.mAv + "&otype=json";
        if (quality != 0) {
            url += "&qn=" + quality;
        }
        return new Request.Builder().url(url).headers(BiliVideo.generalHeaders).build();
    }

    public BiliResource analysisPlayUrl(int quality, String description, GetResourceCallback callback) throws IOException {
        Request request = playUrlRequest(quality);
        Response response = Bili.httpClient.newCall(request).execute();

        ResponseBody body = response.body();
        if (body == null) {
            callback.onFailure("Request is returned empty");
            return null;
        }

        JsonObject json = new Gson().fromJson(body.string(), JsonObject.class);
        if (json.get("code").getAsInt() != 0) {
            callback.onFailure(json.get("message").getAsString());
            return null;
        }


        JsonObject data = json.getAsJsonObject("data");
        //视频格式
        String format = data.get("format").getAsString();
        //播放源
        JsonArray durl = data.get("durl").getAsJsonArray();

        if (durl.size() == 0) {
            callback.onFailure("No player source");
            return null;
        }
        String video = durl.get(0).getAsJsonObject().get("url").getAsString();

        return new BiliResource("https://bilibili.com/", video, format, description);
    }

}
