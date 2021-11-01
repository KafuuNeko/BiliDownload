package cc.kafuu.bilidownload.database.model;

public class VideoBase {
    private long avid;
    private long cid;
    private int quality;

    protected VideoBase(long avid, long cid, int quality) {
        this.avid = avid;
        this.cid = cid;
        this.quality = quality;
    }

    public long getAvid() {
        return avid;
    }

    public long getCid() {
        return cid;
    }

    public int getQuality() {
        return quality;
    }
}
