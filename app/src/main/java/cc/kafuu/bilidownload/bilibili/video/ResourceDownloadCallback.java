package cc.kafuu.bilidownload.bilibili.video;

import java.io.File;

public interface ResourceDownloadCallback {
    void onStatus(long current, long contentLength);
    void onStop();
    void onCompleted(File file);
    void onFailure(String message);
}
