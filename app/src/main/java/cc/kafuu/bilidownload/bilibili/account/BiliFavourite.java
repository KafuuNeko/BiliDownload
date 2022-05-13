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

public class BiliFavourite {
    private static final String TAG = "BiliFavourite";

    public static class Info {
        long id;
        long fid;
        long mid;
        String title;
        long mediaCount;
    }

    //https://api.bilibili.com/x/v3/fav/folder/created/list-all?up_mid=25552898&jsonp=jsonp 获取收藏列表
    //https://api.bilibili.com/x/v3/fav/resource/list?media_id=426737898&pn=1&ps=20&keyword=&order=mtime&type=0&tid=0&platform=web&jsonp=jsonp 获取收藏内容
    public interface GetFavouritesCallback {
        void completed(List<Info> favourites);
        void failure(String message);
    }
    public static void getFavourites(long mid, final GetFavouritesCallback callback) {
        Request request = new Request.Builder()
                .url("https://api.bilibili.com/x/v3/fav/folder/created/list-all?up_mid=" + mid + "&jsonp=jsonp ")
                .headers(Bili.generalHeaders)
                .build();
        Log.d(TAG, "getFavourite: " + request.url());
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

                JsonObject body = new Gson().fromJson(Objects.requireNonNull(response.body()).string(), JsonObject.class);
                Log.d(TAG, "getFavourite: " + body.toString());

                if (body.get("code").getAsInt() != 0) {
                    callback.failure(body.get("message").getAsString());
                    return;
                }

                List<Info> favourites = new ArrayList<>();

                JsonArray list = body.get("data").getAsJsonObject().get("list").getAsJsonArray();
                for (JsonElement element : list) {
                    Info info = new Info();
                    info.id = element.getAsJsonObject().get("id").getAsLong();
                    info.fid = element.getAsJsonObject().get("fid").getAsLong();
                    info.mid = element.getAsJsonObject().get("mid").getAsLong();
                    info.title = element.getAsJsonObject().get("title").getAsString();
                    info.mediaCount = element.getAsJsonObject().get("media_count").getAsLong();

                    favourites.add(info);
                }

                callback.completed(favourites);
            }
        });
    }

}
