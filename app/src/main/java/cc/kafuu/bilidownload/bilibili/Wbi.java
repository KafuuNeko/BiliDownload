package cc.kafuu.bilidownload.bilibili;

import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;

import cc.kafuu.bilidownload.bilibili.account.callback.IGetWbiCallback;
import cn.hutool.core.util.URLUtil;
import cn.hutool.crypto.SecureUtil;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Request;
import okhttp3.Response;

public class Wbi {
    private static final String TAG = "Wbi";
    private static String[] wbiCache = null;
    private static long wbiCacheTime = 0;

    private static final int[] mixinKeyEncTab = new int[]{
            46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 58, 3, 45, 35, 27, 43, 5, 49,
            33, 9, 42, 19, 29, 28, 14, 39, 12, 38, 41, 13, 37, 48, 7, 16, 24, 55, 40,
            61, 26, 17, 0, 1, 60, 51, 30, 4, 22, 25, 54, 21, 56, 59, 6, 63, 57, 62, 11,
            36, 20, 34, 44, 52
    };

    public static String getMixinKey(String imgKey, String subKey) {
        String s = imgKey + subKey;
        StringBuilder key = new StringBuilder();
        for (int i = 0; i < 32; i++) {
            key.append(s.charAt(mixinKeyEncTab[i]));
        }
        return key.toString();
    }

    public static String param(String imgKey, String subKey, Map<String, Object> paramMap) {
        Map<String, Object> map = new LinkedHashMap<>(paramMap);
        map.put("wts", System.currentTimeMillis() / 1000);

        String mixinKey = getMixinKey(imgKey, subKey);
        System.out.println(mixinKey);

        StringJoiner param = new StringJoiner("&");
        //排序 + 拼接字符串
        map.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> param.add(entry.getKey() + "=" + URLUtil.encode(entry.getValue().toString())));

        map.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> param.add(entry.getKey() + "=" + URLUtil.encode(entry.getValue().toString())));
        String s = param + mixinKey;
        String wbiSign = SecureUtil.md5(s);

        return param + "&w_rid=" + wbiSign;
    }

    public static void getWbi(IGetWbiCallback callback) {
        if (System.currentTimeMillis() - wbiCacheTime > 60000 * 5) {
            wbiCache = null;
        }

        if (wbiCache != null) {
            callback.completed(wbiCache[0], wbiCache[1]);
            return;
        }

        Bili.httpClient.newCall(new Request.Builder()
                .url("https://api.bilibili.com/x/web-interface/nav")
                .get()
                .headers(Bili.generalHeaders)
                .build()
        ).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                callback.failure(e.getMessage());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.code() != 200) {
                    if (callback != null) {
                        callback.failure("Options request return code " + response.code());
                    }
                    return;
                }
                JsonObject body = new Gson().fromJson(Objects.requireNonNull(response.body()).string(), JsonObject.class);
                Log.d(TAG, "onResponse: " + body.toString());

                if (body.get("code").getAsLong() != 0) {
                    callback.failure(body.get("message").getAsString());
                    return;
                }


                JsonObject wbiImgJsonObject = body.get("data").getAsJsonObject().get("wbi_img").getAsJsonObject();

                String imgUrl = wbiImgJsonObject.get("img_url").getAsString();
                String subUrl = wbiImgJsonObject.get("sub_url").getAsString();

                imgUrl = imgUrl.substring(imgUrl.indexOf("wbi/") + 4);
                imgUrl = imgUrl.substring(0, imgUrl.indexOf("."));

                subUrl = subUrl.substring(subUrl.indexOf("wbi/") + 4);
                subUrl = subUrl.substring(0, subUrl.indexOf("."));

                wbiCache = new String[2];
                wbiCache[0] = imgUrl;
                wbiCache[1] = subUrl;
                wbiCacheTime = System.currentTimeMillis();

                Log.d(TAG, "onResponse: imgUrl=" + imgUrl + ", subUrl=" + subUrl);
                callback.completed(imgUrl, subUrl);

            }
        });
    }
}
