package cc.kafuu.bilidownload.bilibili.model;

public class BiliUser {
    //上传者id
    protected long mId;
    //上传者名称
    protected String mName;
    //上传者头像
    protected String mFace;
    //上传者签名
    protected String mSign;

    public BiliUser() {

    }

    public BiliUser(long id, String name, String face, String sign) {
        mId = id;
        mName = name;
        mFace = face;
        mSign = sign;
    }

    public long getId() {
        return mId;
    }

    public void setId(long mId) {
        this.mId = mId;
    }

    public String getFace() {
        return mFace;
    }

    public void setFace(String mFace) {
        this.mFace = mFace;
    }

    public String getName() {
        return mName;
    }

    public void setName(String mName) {
        this.mName = mName;
    }

    public String getSign() {
        return mSign;
    }

    public void setSign(String mSign) {
        this.mSign = mSign;
    }
}
