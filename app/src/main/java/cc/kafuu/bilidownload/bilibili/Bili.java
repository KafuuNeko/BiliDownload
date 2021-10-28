package cc.kafuu.bilidownload.bilibili;

import cc.kafuu.bilidownload.utils.OkHttpUtils;
import okhttp3.OkHttpClient;

public class Bili {
    public static OkHttpClient httpClient = OkHttpUtils.getUnsafeOkHttpClient();
    public static String UA = "Mozilla/5.0 (iPhone; CPU iPhone OS 15_0_2 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/15.0 EdgiOS/46.3.30 Mobile/15E148 Safari/605.1.15";
}
