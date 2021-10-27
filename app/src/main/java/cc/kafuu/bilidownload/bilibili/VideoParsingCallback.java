package cc.kafuu.bilidownload.bilibili;

public interface VideoParsingCallback {
    void onComplete(BiliVideos biliVideos);
    void onFailure(String message);
}
