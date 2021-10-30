package cc.kafuu.bilidownload.bilibili;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.IOException;

import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class BiliAccount {
    public static BiliAccount getAccount(String cookie) {
        String account = getRequest("https://api.bilibili.com/x/member/web/account", cookie);
        if (account == null) {
            return null;
        }
        String nav = getRequest("https://api.bilibili.com/x/web-interface/nav", cookie);
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

    private static String getRequest(String url, String cookie) {
        try {
            Response response = Bili.httpClient.newCall(new Request.Builder()
                    .url(url)
                    .headers(Bili.generalHeaders)
                    .addHeader("Cookie", cookie)
                    .build()
            ).execute();

            ResponseBody body = response.body();
            if (body == null) {
                return null;
            }
            return body.string();

        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private final long mId;
    private final String mFace;
    private final String mUserName;
    private final String mUserId;
    private final String mSign;
    private final String mBirthday;
    private final String mSex;
    private final String mRank;

    private BiliAccount(JsonObject accountData, JsonObject navData) {
        this.mId = accountData.get("mid").getAsLong();
        this.mUserName = accountData.get("uname").getAsString();
        this.mUserId = accountData.get("userid").getAsString();
        this.mSign = accountData.get("sign").getAsString();
        this.mBirthday = accountData.get("birthday").getAsString();
        this.mSex = accountData.get("sex").getAsString();
        this.mRank = accountData.get("rank").getAsString();

        this.mFace = navData.get("face").getAsString();
    }

    public long getId() {
        return mId;
    }

    public String getFace() {
        return mFace;
    }

    public String getBirthday() {
        return mBirthday;
    }

    public String getRank() {
        return mRank;
    }

    public String getSex() {
        return mSex;
    }

    public String getSign() {
        return mSign;
    }

    public String getUserId() {
        return mUserId;
    }

    public String getUserName() {
        return mUserName;
    }
}
