package cc.kafuu.bilidownload.bilibili;

import android.util.Log;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Request;
import okhttp3.Response;

public class BiliFavourite {
    private static final String TAG = "BiliFavourite";

    //https://api.bilibili.com/x/v3/fav/folder/created/list-all?up_mid=25552898&jsonp=jsonp 获取收藏列表
    //https://api.bilibili.com/x/v3/fav/resource/list?media_id=426737898&pn=1&ps=20&keyword=&order=mtime&type=0&tid=0&platform=web&jsonp=jsonp 获取收藏内容
    public static void getFavourite() {
        Request request = new Request.Builder()
                .url("https://api.bilibili.cn/favourite?ver=2")
                .headers(Bili.generalHeaders)
                .build();

        Bili.httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {

            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                Log.d(TAG, "onResponse: " + response.body().string());
            }
        });
    }

}
