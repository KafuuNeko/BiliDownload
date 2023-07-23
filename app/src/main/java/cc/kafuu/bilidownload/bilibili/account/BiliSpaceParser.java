package cc.kafuu.bilidownload.bilibili.account;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import cc.kafuu.bilidownload.bilibili.Bili;
import cc.kafuu.bilidownload.bilibili.Wbi;
import cc.kafuu.bilidownload.bilibili.account.callback.IGetSpaceVideosCallback;
import cc.kafuu.bilidownload.bilibili.account.callback.IGetWbiCallback;
import cc.kafuu.bilidownload.bilibili.model.BiliVideo;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Request;
import okhttp3.Response;

public class BiliSpaceParser {
    private static final String TAG = "BiliSpace";


    /**
     * 取得用户所有投稿视频
     * @param uid 用户id
     * @param pn 页码
     * @param tid 筛选目标分区
     *            0：不进行分区筛选
     *            分区tid为所筛选的分区
     * @param callback 结果回调
     * */
    public static void getSpaceVideos(long uid, int pn, int tid, IGetSpaceVideosCallback callback) {
        Wbi.getWbi(new IGetWbiCallback() {
            @Override
            public void completed(String imgUrl, String subUrl) {
                wbiLoaded(imgUrl, subUrl, uid, pn, tid, callback);
            }

            @Override
            public void failure(String message) {
                callback.failure(message);
            }
        });
    }

    private static void wbiLoaded(String imgUrl, String subUrl, long uid, int pn, int tid, IGetSpaceVideosCallback callback) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("mid", uid);
        map.put("ps", "10");
        map.put("pn", pn);
        map.put("tid", tid);

        String param = Wbi.param(imgUrl, imgUrl, map);
        Log.d(TAG, "wbiLoaded: " + param);
        String url = "http://api.bilibili.com/x/space/arc/search?" + param;
        Request request = new Request.Builder().url(url).headers(Bili.generalHeaders).build();
        Bili.httpClient.newCall(request).enqueue(new Callback() {

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.code() != 200) {
                    if (callback != null) {
                        callback.failure("Options request return code " + response.code());
                    }
                    return;
                }

                JsonObject jsonBody = new Gson().fromJson(Objects.requireNonNull(response.body()).string(), JsonObject.class);
                Log.d(TAG, "onResponse: " + jsonBody.toString());

                //api调用失败（错误代码非0）
                //0：成功
                //-400：请求错误
                //-412：请求被拦截
                //-1200：被降级过滤的请求(一种意义不明的偶发状况)
                if (jsonBody.get("code").getAsLong() != 0) {
                    callback.failure(jsonBody.get("message").getAsString());
                    return;
                }

                JsonObject data = jsonBody.get("data").getAsJsonObject();
                JsonObject list = data.get("list").getAsJsonObject();

                JsonArray vlist = list.get("vlist").getAsJsonArray();

                List<BiliVideo> records = new ArrayList<>();
                for (JsonElement element : vlist) {
                    JsonObject object = element.getAsJsonObject();

                    BiliVideo record = new BiliVideo();
                    record.setVideoId(object.get("bvid").getAsString());
                    record.setTitle(object.get("title").getAsString());
                    record.setInfo(object.get("description").getAsString());
                    record.setCover(object.get("pic").getAsString());
                    records.add(record);
                }

                callback.completed(records);
            }

            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                callback.failure(e.getMessage());
            }
        });
    }
}
