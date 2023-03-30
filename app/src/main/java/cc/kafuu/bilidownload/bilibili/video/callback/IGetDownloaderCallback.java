package cc.kafuu.bilidownload.bilibili.video.callback;

import cc.kafuu.bilidownload.bilibili.video.BiliVideoDownloader;

public interface IGetDownloaderCallback {
    void completed(BiliVideoDownloader downloader);

    void failure(String message);
}
