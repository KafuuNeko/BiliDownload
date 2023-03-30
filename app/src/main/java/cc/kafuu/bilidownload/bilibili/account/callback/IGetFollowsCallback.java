package cc.kafuu.bilidownload.bilibili.account.callback;

import java.util.List;

import cc.kafuu.bilidownload.bilibili.model.BiliFollowVideo;

public interface IGetFollowsCallback {
    void completed(List<BiliFollowVideo> records);

    void failure(String message);
}
