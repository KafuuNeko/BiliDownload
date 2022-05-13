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

public class BiliHistory {
    private static final String TAG = "BiliHistory";
    //https://api.bilibili.com/x/web-interface/history/cursor?max=&view_at=&business=archive

    public static class Cursor {
        long max;
        long viewAt;
    }

    public static class Record {
        String title;
        String bv;
        String cover;
        String author;
    }

    public interface GetHistoryCallback {
        void completed(List<Record> records, Cursor nextCursor);
        void failure(String message);
    }

    public static void getHistory(Cursor cursor, final GetHistoryCallback callback) {
        String url = "https://api.bilibili.com/x/web-interface/history/cursor?business=archive";
        if (cursor != null) {
            url += "&max=" + cursor.max + "&view_at=" + cursor.viewAt;
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
                nextCursor.viewAt = jsonCursor.get("view_at").getAsLong();
                if (nextCursor.max == 0 && nextCursor.viewAt == 0) {
                    nextCursor = null;
                }

                List<Record> records = new ArrayList<>();
                JsonArray jsonList = jsonData.get("list").getAsJsonArray();
                for (JsonElement element : jsonList) {
                    Record record = new Record();
                    record.title = element.getAsJsonObject().get("title").getAsString();
                    record.cover = element.getAsJsonObject().get("cover").getAsString();
                    record.author = element.getAsJsonObject().get("author_name").getAsString();
                    record.bv = element.getAsJsonObject().get("history").getAsJsonObject().get("bvid").getAsString();
                    records.add(record);
                }

                callback.completed(records, nextCursor);
            }
        });
    }

}
