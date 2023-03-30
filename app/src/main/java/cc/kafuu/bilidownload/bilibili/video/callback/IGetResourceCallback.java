package cc.kafuu.bilidownload.bilibili.video.callback;

import java.util.List;

import cc.kafuu.bilidownload.bilibili.video.BiliVideoResourceParser;

public interface IGetResourceCallback {
    void completed(List<BiliVideoResourceParser> resources);

    void failure(String message);
}
