package cc.kafuu.bilidownload.bilibili.video.callback;

import cc.kafuu.bilidownload.bilibili.video.BiliVideoParser;

public interface IVideoParsingCallback {
    void completed(BiliVideoParser biliVideosParser);

    void failure(String message);
}
