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
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Request;
import okhttp3.Response;

public class BiliFollow {
    private static final String TAG = "BiliFollow";

    public enum Type {
        Cartoon, Teleplay
    }

    public static class Record {
        public String title;
        public long seasonId;
        public long mediaId;
        public String cover;
        public String evaluate;
    }

    public interface GetFollowsCallback {
        void completed(List<Record> records);
        void failure(String message);
    }

    /**
     * 取得用户追的番剧或电视剧
     * */
    public static void getFollows(long uid, Type type, int pn, GetFollowsCallback callback) {
        String url = "https://api.bilibili.com/x/space/bangumi/follow/list?type= " + ((type == Type.Cartoon) ? 1 : 2) + " &follow_status=0&pn=" + pn + "&ps=15&vmid=" + uid;
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

                final List<Record> records = new ArrayList<>();
                for (JsonElement element : jsonList) {
                    Record record = new Record();

                    record.seasonId = element.getAsJsonObject().get("season_id").getAsLong();
                    record.mediaId = element.getAsJsonObject().get("media_id").getAsLong();
                    record.title = element.getAsJsonObject().get("title").getAsString();
                    record.cover = element.getAsJsonObject().get("cover").getAsString();
                    record.evaluate = element.getAsJsonObject().get("evaluate").getAsString();

                    records.add(record);
                }

                callback.completed(records);
            }
        });

    }


}
