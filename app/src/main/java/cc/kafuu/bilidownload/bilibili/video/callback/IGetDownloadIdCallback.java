package cc.kafuu.bilidownload.bilibili.video.callback;

public interface IGetDownloadIdCallback {
    void failure(String message);

    void completed(long id);
}
