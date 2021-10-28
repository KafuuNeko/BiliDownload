package cc.kafuu.bilidownload.bilibili.video;

public class BiliPlayInfo {

    private long mCid;
    private String mPartName;
    private String mPartDuration;

    public BiliPlayInfo(long cid, String partName, String partDuration) {
        this.mCid = cid;
        this.mPartName = partName;
        this.mPartDuration = partDuration;
    }

}
