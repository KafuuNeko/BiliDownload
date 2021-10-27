package cc.kafuu.bilidownload.bilibili;

import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cc.kafuu.bilidownload.bilibili.resource.BiliAudioResource;
import cc.kafuu.bilidownload.bilibili.resource.BiliResource;
import cc.kafuu.bilidownload.bilibili.resource.BiliVideoResource;
import cc.kafuu.bilidownload.utils.Pair;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class Bili {
    public static final OkHttpClient httpClient = new OkHttpClient();

    public static void parse(final String videoUrl, final VideoParsingCallback callback)
    {
        //构造访问
        Request request = new Request.Builder()
                .url(videoUrl)
                .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                .addHeader("Accept-Language", "zh-CN,zh;q=0.8,zh-TW;q=0.7,zh-HK;q=0.5,en-US;q=0.3,en;q=0.2")
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:93.0) Gecko/20100101 Firefox/93.0")
                .build();

        //开始访问视频链接
        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                callback.onFailure(e.getMessage());
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                if (response.body() == null) {
                    callback.onFailure("The request returns null data");
                    return;
                }

                String html = Objects.requireNonNull(response.body()).string();
                Log.d("Bili", html);

                //通过正则从返回的信息中取出视频播放信息
                Pattern pattern = Pattern.compile("<script>window.__playinfo__=(.*?)</script>");
                Matcher matcher = pattern.matcher(html);
                if (!matcher.find()) {
                    callback.onFailure("Video message could not be found");
                    return;
                }

                String playInfo = matcher.group();
                //去除前缀(<script>window.__playinfo__=)以及后缀(</script>)
                playInfo = playInfo.substring(playInfo.indexOf('{'), playInfo.lastIndexOf("}") + 1);

                Log.d("Bili PlayInfo", playInfo);
                //解析视频信息json
                JsonObject videoInfo = new Gson().fromJson(playInfo, JsonObject.class);
                if (videoInfo.get("code").getAsInt() != 0) {
                    callback.onFailure(videoInfo.get("message").getAsString());
                    return;
                }
                parseVideoInfo(videoUrl, response, videoInfo, callback);
            }
        });
    }

    private static void parseVideoInfo(final String videoUrl, Response response, JsonObject videoInfo, final VideoParsingCallback callback) {
        JsonObject videoData = videoInfo.get("data").getAsJsonObject();

        String[] accept_format = videoData.get("accept_format").getAsString().split(",");
        JsonArray accept_description = videoData.get("accept_description").getAsJsonArray();
        JsonArray accept_quality = videoData.get("accept_quality").getAsJsonArray();

        if (!(accept_format.length == accept_description.size() && accept_description.size() == accept_quality.size()))
        {
            callback.onFailure("The length of accept_format, accept_description, and accept_quality do not match");
            return;
        }

        //建立accept_quality -> (accept_format, accept_description)关系表
        Map<Integer, Pair<String, String>> description = new HashMap<>();
        for (int i = 0; i < accept_quality.size(); ++i) {
            description.put(
                    accept_quality.get(i).getAsInt(),
                    new Pair<>(accept_format[i], accept_description.get(i).getAsString())
            );
        }

        List<BiliResource> resources = new ArrayList<>();

        JsonObject videoDash = videoData.get("dash").getAsJsonObject();

        Log.d("Bili VideoDash", videoDash.toString());

        //解析所有视频地址
        JsonArray videos = videoDash.get("video").getAsJsonArray();
        for (JsonElement item : videos) {
            JsonObject info = item.getAsJsonObject();
            int id = info.get("id").getAsInt();
            BiliVideoResource biliVideo = new BiliVideoResource(
                    id,
                    videoUrl,
                    info.get("base_url").getAsString(),
                    Objects.requireNonNull(description.get(id)).second,
                    Objects.requireNonNull(description.get(id)).first,
                    info.get("codecs").getAsString()
            );
            resources.add(biliVideo);
        }

        //解析所有音频地址
        JsonArray audios = videoDash.get("audio").getAsJsonArray();
        for (JsonElement item : audios) {
            JsonObject info = item.getAsJsonObject();
            int id = info.get("id").getAsInt();
            BiliAudioResource biliAudio = new BiliAudioResource(
                    id,
                    videoUrl,
                    info.get("base_url").getAsString(),
                    "audio-" + info.get("bandwidth").getAsInt(),
                    info.get("codecs").getAsString()
            );
            resources.add(biliAudio);
        }

        callback.onComplete(new BiliVideos(response, resources));
    }
}
