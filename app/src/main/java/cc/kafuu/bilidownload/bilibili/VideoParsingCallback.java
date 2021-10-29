package cc.kafuu.bilidownload.bilibili;

import cc.kafuu.bilidownload.bilibili.video.BiliVideo;

public interface VideoParsingCallback {
    void onCompleted(BiliVideo biliVideos);
    void onFailure(String message);
}
