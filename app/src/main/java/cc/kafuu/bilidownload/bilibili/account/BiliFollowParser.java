package cc.kafuu.bilidownload.bilibili.account;

import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import cc.kafuu.bilidownload.bilibili.Bili;
import cc.kafuu.bilidownload.bilibili.account.callback.IGetFollowsCallback;
import cc.kafuu.bilidownload.bilibili.model.BiliFollowVideo;
import cc.kafuu.bilidownload.bilibili.model.BiliFollowType;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Request;
import okhttp3.Response;

public class BiliFollowParser {
    private static final String TAG = "BiliFollow";

    /**
     * 取得用户追的番剧或电视剧
     * */
    public static void getFollows(long uid, BiliFollowType type, int pn, IGetFollowsCallback callback) {
        String url = "https://api.bilibili.com/x/space/bangumi/follow/list?type= " + ((type == BiliFollowType.Cartoon) ? 1 : 2) + "&follow_status=0&pn=" + pn + "&ps=15&vmid=" + uid;
        Request request = new Request.Builder().url(url).headers(Bili.generalHeaders).build();

        Bili.httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                callback.failure(e.getMessage());
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                if (response.code() != 200) {
                    if (callback != null) {
                        callback.failure("Options request return code " + response.code());
                    }
                    return;
                }

                JsonObject jsonBody = new Gson().fromJson(Objects.requireNonNull(response.body()).string(), JsonObject.class);
                Log.d(TAG, "onResponse: " + jsonBody.toString());

                if (jsonBody.get("code").getAsLong() != 0) {
                    callback.failure(jsonBody.get("message").getAsString());
                    return;
                }

                JsonObject jsonData = jsonBody.get("data").getAsJsonObject();
                JsonArray jsonList = jsonData.get("list").getAsJsonArray();

                final List<BiliFollowVideo> records = new ArrayList<>();
                for (JsonElement element : jsonList) {
                    BiliFollowVideo record = new BiliFollowVideo();


                    record.setSeasonId(element.getAsJsonObject().get("season_id").getAsLong());
                    record.setMediaId(element.getAsJsonObject().get("media_id").getAsLong());
                    record.setTitle(element.getAsJsonObject().get("title").getAsString());
                    record.setCover(element.getAsJsonObject().get("cover").getAsString());
                    record.setInfo(element.getAsJsonObject().get("evaluate").getAsString());
                    record.setVideoId("ss" + record.getSeasonId());

                    records.add(record);
                }

                callback.completed(records);
            }
        });

    }


}
