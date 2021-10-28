package cc.kafuu.bilidownload.bilibili.video;

import java.util.List;

public interface GetResourceCallback {
    void onComplete(List<BiliVideoResource> resources);
    void onFailure(String message);
}
