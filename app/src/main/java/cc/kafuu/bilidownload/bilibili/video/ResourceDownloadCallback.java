package cc.kafuu.bilidownload.bilibili.video;

import java.io.File;

public interface ResourceDownloadCallback {
    void onStatus(int current, int contentLength);
    void onComplete(File file);
    void onFailure(String message);
}