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
import okhttp3.Request;
import okhttp3.Response;

public class BiliFavourite {
    private static final String TAG = "BiliFavourite";

    public interface Callback<T> {
        void completed(List<T> list, boolean hasMore);
        void failure(String message);
    }

    public static class Favourite {
        public long id;
        public long fid;
        public long mid;
        public String title;
        public long mediaCount;
    }

    //https://api.bilibili.com/x/v3/fav/folder/created/list-all?up_mid=25552898&jsonp=jsonp 获取收藏列表
    public static void getFavourites(long mid, final Callback<Favourite> callback) {
        Request request = new Request.Builder()
                .url("https://api.bilibili.com/x/v3/fav/folder/created/list-all?up_mid=" + mid + "&jsonp=jsonp ")
                .headers(Bili.generalHeaders)
                .build();
        Log.d(TAG, "getFavourite: " + request.url());
        Bili.httpClient.newCall(request).enqueue(new okhttp3.Callback() {
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

                List<Favourite> favourites = new ArrayList<>();

                JsonElement data = body.get("data");
                if (!data.isJsonNull()) {
                    JsonArray list = data.getAsJsonObject().get("list").getAsJsonArray();
                    for (JsonElement element : list) {
                        Favourite info = new Favourite();
                        info.id = element.getAsJsonObject().get("id").getAsLong();
                        info.fid = element.getAsJsonObject().get("fid").getAsLong();
                        info.mid = element.getAsJsonObject().get("mid").getAsLong();
                        info.title = element.getAsJsonObject().get("title").getAsString();
                        info.mediaCount = element.getAsJsonObject().get("media_count").getAsLong();

                        favourites.add(info);
                    }
                }

                callback.completed(favourites, false);
            }
        });
    }

    //https://api.bilibili.com/x/v3/fav/resource/list?media_id=426737898&pn=1&ps=20&keyword=&order=mtime&type=0&tid=0&platform=web&jsonp=jsonp 获取收藏内容
    public static class Video {
        public String title;
        public String bv;
        public String cover;
        public String intro;
    }

    public static void getVideos(Favourite favourite, int pn, final Callback<Video> callback) {
        Request request = new Request.Builder()
                .url("https://api.bilibili.com/x/v3/fav/resource/list?media_id=" + favourite.id + "&pn=" + pn + "&ps=20&keyword=&order=mtime&type=0&tid=0&platform=web&jsonp=jsonp ")
                .headers(Bili.generalHeaders)
                .build();
        Log.d(TAG, "getVideos: " + request.url());

        Bili.httpClient.newCall(request).enqueue(new okhttp3.Callback() {
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

                List<Video> videos = new ArrayList<>();
                boolean hasMore = false;

                JsonElement jsonData = jsonBody.get("data").getAsJsonObject();
                if (!jsonData.isJsonNull() && jsonData.getAsJsonObject().get("medias").isJsonArray()) {

                    JsonArray jsonMedias = jsonData.getAsJsonObject().get("medias").getAsJsonArray();
                    for (JsonElement element : jsonMedias) {
                        Video video = new Video();

                        video.title = element.getAsJsonObject().get("title").getAsString();
                        video.intro = element.getAsJsonObject().get("intro").getAsString();
                        video.cover = element.getAsJsonObject().get("cover").getAsString();
                        video.bv = element.getAsJsonObject().get("bvid").getAsString();

                        videos.add(video);
                    }

                    hasMore = jsonData.getAsJsonObject().get("has_more").getAsBoolean();
                }

                callback.completed(videos, hasMore);
            }
        });
    }

}
