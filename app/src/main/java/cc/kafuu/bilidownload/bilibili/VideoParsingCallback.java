package cc.kafuu.bilidownload.bilibili;

import cc.kafuu.bilidownload.bilibili.video.BiliVideo;

public interface VideoParsingCallback {
    void onComplete(BiliVideo biliVideos);
    void onFailure(String message);
}
