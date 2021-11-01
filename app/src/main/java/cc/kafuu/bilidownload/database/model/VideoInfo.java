package cc.kafuu.bilidownload.database.model;

public class VideoInfo extends VideoBase {
    private String videoPic;
    private String partPic;

    private String videoTitle;
    private String partTitle;

    private String videoDescription;
    private String partDescription;

    public VideoInfo(long avid, long cid, int quality, String videoPic, String partPic, String videoTitle, String partTitle, String videoDescription, String partDescription) {
        super(avid, cid, quality);
        this.videoPic = videoPic;
        this.partPic = partPic;
        this.videoTitle = videoTitle;
        this.partTitle = partTitle;
        this.videoDescription = videoDescription;
        this.partDescription = partDescription;
    }

    public String getVideoPic() {
        return videoPic;
    }

    public String getPartPic() {
        return partPic;
    }

    public String getVideoTitle() {
        return videoTitle;
    }

    public String getPartTitle() {
        return partTitle;
    }

    public String getVideoDescription() {
        return videoDescription;
    }

    public String getPartDescription() {
        return partDescription;
    }
}
