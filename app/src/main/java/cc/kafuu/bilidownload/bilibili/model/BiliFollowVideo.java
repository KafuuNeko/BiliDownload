package cc.kafuu.bilidownload.bilibili.model;

public class BiliFollowVideo extends BiliVideo{
    private long mSeasonId;
    private long mMediaId;

    public long getSeasonId() {
        return mSeasonId;
    }

    public void setSeasonId(long seasonId) {
        this.mSeasonId = seasonId;
    }

    public long getMediaId() {
        return mMediaId;
    }

    public void setMediaId(long mediaId) {
        this.mMediaId = mediaId;
    }
}
