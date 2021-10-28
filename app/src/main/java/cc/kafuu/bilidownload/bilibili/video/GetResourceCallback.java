package cc.kafuu.bilidownload.bilibili.video;

import java.io.File;
import java.util.List;

public interface GetResourceCallback {
    void onComplete(List<BiliResource> resources);
    void onFailure(String message);
}
