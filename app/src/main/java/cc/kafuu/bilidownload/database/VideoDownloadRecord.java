package cc.kafuu.bilidownload.database;

import org.litepal.annotation.Column;
import org.litepal.crud.LitePalSupport;

import java.util.Date;

public class VideoDownloadRecord extends LitePalSupport {
    private long id;

    private final long avid;
    private final long cid;
    private final int quality;

    @Column(unique = true)
    private long downloadId;
    @Column(nullable = false)
    private final Date startTime;
    @Column(nullable = false)
    private String saveTo;

    @Column(defaultValue = "NULL")
    private String converting = null;

    public VideoDownloadRecord(long downloadId, long avid, long cid, int quality, String saveTo) {
        this.downloadId = downloadId;

        this.avid = avid;
        this.cid = cid;
        this.quality = quality;

        this.saveTo = saveTo;
        this.startTime = new Date();
    }

    public long getId() {
        return id;
    }

    public long getDownloadId() {
        return downloadId;
    }

    public void setDownloadId(long downloadId) {
        this.downloadId = downloadId;
    }

    public Date getStartTime() {
        return startTime;
    }

    public int getQuality() {
        return quality;
    }

    public long getAvid() {
        return avid;
    }

    public long getCid() {
        return cid;
    }

    public String getSaveTo() {
        return saveTo;
    }

    public void setSaveTo(String saveTo) {
        this.saveTo = saveTo;
    }

    public String getConverting() {
        if (converting == null || converting.equals("NULL")) {
            return null;
        }
        return converting;
    }

    public void setConverting(String converting) {
        if (converting == null) {
            this.converting = "NULL";
        } else {
            this.converting = converting;
        }
    }
}
