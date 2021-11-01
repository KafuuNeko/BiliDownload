package cc.kafuu.bilidownload.database.model;

import java.io.File;

public class VideoDownloadRecord extends VideoBase {
    private long id;
    private long downloadID;
    private String downloadTime;
    private File file;

    public VideoDownloadRecord(long id, long avid, long cid, int quality, long downloadID, String downloadTime, File file) {
        super(avid, cid, quality);

        this.id = id;
        this.downloadID = downloadID;
        this.downloadTime = downloadTime;
        this.file = file;
    }
}
