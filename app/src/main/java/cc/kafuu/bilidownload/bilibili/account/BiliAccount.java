package cc.kafuu.bilidownload.bilibili.account;

import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.IOException;

import cc.kafuu.bilidownload.bilibili.Bili;
import cc.kafuu.bilidownload.bilibili.model.BiliUser;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public final class BiliAccount extends BiliUser {
    private static final String TAG = "BiliAccount";

    /**
     * 传入Cookie取得用户信息（需要在线程中执行）
     * 用以判断Cookie是否有效，如果有效则返回用户信息，否则返回空
     *
     * @param cookie cookie
     * */
    public static BiliAccount getAccount(String cookie) {
        //Api-1 取得用户信息
        String account = getCookieRequest("https://api.bilibili.com/x/member/web/account", cookie);
        if (account == null) {
            return null;
        }
        //Api-2 取得用户头像，判断是否登录
        String nav = getCookieRequest("https://api.bilibili.com/x/web-interface/nav", cookie);
        if (nav == null) {
            return null;
        }

        try {
            JsonObject accountJson = new Gson().fromJson(account, JsonObject.class);
            JsonObject navJson = new Gson().fromJson(nav, JsonObject.class);;
            if (accountJson.get("code").getAsInt() != 0 || navJson.get("code").getAsInt() != 0) {
                return null;
            }

            JsonObject navData = navJson.getAsJsonObject("data");
            if (!navData.get("isLogin").getAsBoolean()) {
                return null;
            }

            return new BiliAccount(accountJson.getAsJsonObject("data"), navData);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * getAccount的辅助函数（需要在线程中进行）
     * 以get方式取得网络数据并返回
     * */
    private static String getCookieRequest(String url, String cookie) {
        try {
            Response response = Bili.httpClient.newCall(new Request.Builder()
                    .url(url)
                    .headers(Bili.generalHeaders)
                    .addHeader("Cookie", cookie)
                    .build()
            ).execute();

            ResponseBody body = response.body();
            if (body == null) {
                Log.d(TAG, "getCookieRequest: Body is null; Response code: " + response.code());
                return null;
            }
            String data = body.string();
            Log.d(TAG, "getCookieRequest: " + data);
            return data;

        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }


    //用户ID bili_xxxxxx
    private String mUserId;
    //用户生日
    private String mBirthday;
    //用户性别
    private String mSex;
    //用户排名
    private String mRank;

    private BiliAccount(JsonObject accountData, JsonObject navData) {
        this.mId = accountData.get("mid").getAsLong();
        this.mName = accountData.get("uname").getAsString();
        this.mUserId = accountData.get("userid").getAsString();
        this.mSign = accountData.get("sign").getAsString();
        this.mBirthday = accountData.get("birthday").getAsString();
        this.mSex = accountData.get("sex").getAsString();
        this.mRank = accountData.get("rank").getAsString();

        this.mFace = navData.get("face").getAsString();
    }


    public String getBirthday() {
        return mBirthday;
    }

    public void setBirthday(String mBirthday) {
        this.mBirthday = mBirthday;
    }

    public String getRank() {
        return mRank;
    }

    public void setRank(String mRank) {
        this.mRank = mRank;
    }

    public String getSex() {
        return mSex;
    }

    public void setSex(String mSex) {
        this.mSex = mSex;
    }

    public String getUserId() {
        return mUserId;
    }

    public void setUserId(String mUserId) {
        this.mUserId = mUserId;
    }
}
