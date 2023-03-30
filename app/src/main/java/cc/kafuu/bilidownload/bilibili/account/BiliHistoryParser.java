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
import cc.kafuu.bilidownload.bilibili.account.callback.IGetHistoryCallback;
import cc.kafuu.bilidownload.bilibili.model.BiliVideo;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Request;
import okhttp3.Response;

public class BiliHistoryParser {
    private static final String TAG = "BiliHistory";
    //https://api.bilibili.com/x/web-interface/history/cursor?max=&view_at=&business=archive

    public static class Cursor {
        public long max;
        public String business;
        public long viewAt;
    }

    public static void getHistory(Cursor cursor, final IGetHistoryCallback callback) {
        String url = "https://api.bilibili.com/x/web-interface/history/cursor";
        if (cursor != null) {
            url += "?business=" + cursor.business + "&max=" + cursor.max + "&view_at=" + cursor.viewAt;
        }
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

                if (jsonBody.get("code").getAsInt() != 0) {
                    callback.failure(jsonBody.get("message").getAsString());
                    return;
                }

                JsonObject jsonData = jsonBody.get("data").getAsJsonObject();

                JsonObject jsonCursor = jsonData.get("cursor").getAsJsonObject();

                Cursor nextCursor = new Cursor();
                nextCursor.max = jsonCursor.get("max").getAsLong();
                nextCursor.business = jsonCursor.get("business").getAsString();
                nextCursor.viewAt = jsonCursor.get("view_at").getAsLong();

                if (nextCursor.max == 0 && nextCursor.viewAt == 0) {
                    nextCursor = null;
                }

                List<BiliVideo> records = new ArrayList<>();
                JsonArray jsonList = jsonData.get("list").getAsJsonArray();
                for (JsonElement element : jsonList) {
                    if (!element.getAsJsonObject().get("history").getAsJsonObject().get("business").getAsString().equals("archive")) {
                        //这条历史记录不是视频 跳过
                        continue;
                    }

                    BiliVideo record = new BiliVideo();
                    record.setTitle(element.getAsJsonObject().get("title").getAsString());
                    record.setCover(element.getAsJsonObject().get("cover").getAsString());
                    record.setInfo(element.getAsJsonObject().get("author_name").getAsString());
                    record.setVideoId(element.getAsJsonObject().get("history").getAsJsonObject().get("bvid").getAsString());
                    records.add(record);
                }

                callback.completed(records, nextCursor);
            }
        });
    }

}
