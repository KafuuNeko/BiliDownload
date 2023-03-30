package cc.kafuu.bilidownload.bilibili.account.callback;

import java.util.List;

import cc.kafuu.bilidownload.bilibili.model.BiliVideo;

public interface IGetSpaceVideosCallback {
    void completed(List<BiliVideo> records);

    void failure(String message);
}
