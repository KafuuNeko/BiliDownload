package cc.kafuu.bilidownload.bilibili;

import android.os.Environment;

import java.io.File;

import cc.kafuu.bilidownload.utils.OkHttpUtils;
import okhttp3.Headers;
import okhttp3.OkHttpClient;

public class Bili {
    public static final OkHttpClient httpClient = new OkHttpClient();//OkHttpUtils.getUnsafeOkHttpClient()

    public static final String UA = "Mozilla/5.0 (iPhone; CPU iPhone OS 15_0_2 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/15.0 EdgiOS/46.3.30 Mobile/15E148 Safari/605.1.15";
    public static Headers generalHeaders;
    public static Headers downloadHeaders;
    public static void updateHeaders(String cookie) {
        Headers.Builder generalHeadersBuilder = new Headers.Builder()
                .add("Accept", "application/json, text/plain, */*")
                .add("Accept-Language", "zh-CN,zh-Hans;q=0.9")
                .add("Origin", "https://m.bilibili.com")
                .add("Referer", "https://m.bilibili.com/")
                .add("User-Agent", Bili.UA);

        Headers.Builder downloadHeadersBuilder = new Headers.Builder()
                .add("accept", "*/*")
                .add("accept-encoding", "gzip, deflate, br")
                .add("Accept-Language", "zh-CN,zh-Hans;q=0.9")
                .add("Connection", "keep-alive")
                .add("Origin", "https://m.bilibili.com")
                .add("user-agent", Bili.UA);

        if (cookie != null) {
            generalHeadersBuilder.add("Cookie", cookie);
            downloadHeadersBuilder.add("Cookie", cookie);
        }

        generalHeaders = generalHeadersBuilder.build();
        downloadHeaders = downloadHeadersBuilder.build();
    }
    static {
        updateHeaders(null);
    }

    public static BiliAccount biliAccount = null;

    public static final File saveDir = new File(Environment.getExternalStorageDirectory().getPath() + "/Download/BiliVideo");
}
