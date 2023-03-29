package cc.kafuu.bilidownload.bilibili.account;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import cc.kafuu.bilidownload.bilibili.Bili;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Request;
import okhttp3.Response;

public class BiliSpace {
    private static final String TAG = "BiliSpace";

    //视频稿件记录
    public static class VideoRecord {
        //稿件av号
        public long avid;
        //稿件bv号
        public String bvid;
        //稿件封面图片
        public String pic;
        //稿件标题
        public String title;
        //稿件描述
        public String description;
    }


    public interface GetSpaceVideosCallback {
        void completed(List<VideoRecord> records);
        void failure(String message);
    }

    /**
     * 取得用户所有投稿视频
     * @param uid 用户id
     * @param pn 页码
     * @param tid 筛选目标分区
     *            0：不进行分区筛选
     *            分区tid为所筛选的分区
     * @param callback 结果回调
     * */
    public static void getSpaceVideos(long uid, int pn, int tid, GetSpaceVideosCallback callback) {
        String url = "https://api.bilibili.com/x/space/wbi/arc/search?mid=" + uid + "&ps=10&pn=" + pn + "&tid=" + tid;
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

                List<VideoRecord> records = new ArrayList<>();
                for (JsonElement element : vlist) {
                    JsonObject object = element.getAsJsonObject();

                    VideoRecord record = new VideoRecord();
                    record.avid = object.get("aid").getAsLong();
                    record.bvid = object.get("bvid").getAsString();
                    record.title = object.get("title").getAsString();
                    record.description = object.get("description").getAsString();
                    record.pic = object.get("pic").getAsString();

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
