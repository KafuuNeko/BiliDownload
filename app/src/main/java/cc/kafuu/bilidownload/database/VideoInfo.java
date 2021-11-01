package cc.kafuu.bilidownload.database;

import org.litepal.annotation.Column;
import org.litepal.crud.LitePalSupport;

public class VideoInfo extends LitePalSupport {
    private long id;

    private final long avid;
    private final long cid;
    private final int quality;

    private String format;
    private String qualityDescription;

    private String videoPic = null;
    private String partPic = null;

    private String videoTitle = null;
    private String partTitle = null;

    private String videoDescription = null;
    private String partDescription = null;

    public VideoInfo(long avid, long cid, int quality) {
        this.avid = avid;
        this.cid = cid;
        this.quality = quality;
    }

    public long getId() {
        return id;
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

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public String getQualityDescription() {
        return qualityDescription;
    }

    public void setQualityDescription(String qualityDescription) {
        this.qualityDescription = qualityDescription;
    }

    public String getVideoPic() {
        return videoPic;
    }

    public void setVideoPic(String videoPic) {
        this.videoPic = videoPic;
    }

    public String getPartPic() {
        return partPic;
    }

    public void setPartPic(String partPic) {
        this.partPic = partPic;
    }

    public String getVideoTitle() {
        return videoTitle;
    }

    public void setVideoTitle(String videoTitle) {
        this.videoTitle = videoTitle;
    }

    public String getPartTitle() {
        return partTitle;
    }

    public void setPartTitle(String partTitle) {
        this.partTitle = partTitle;
    }

    public String getVideoDescription() {
        return videoDescription;
    }

    public void setVideoDescription(String videoDescription) {
        this.videoDescription = videoDescription;
    }

    public String getPartDescription() {
        return partDescription;
    }

    public void setPartDescription(String partDescription) {
        this.partDescription = partDescription;
    }

    public boolean saveOrUpdate() {
        return saveOrUpdate("avid=? AND cid=? AND quality=?",
                String.valueOf(avid), String.valueOf(cid), String.valueOf(quality));
    }
}
