package cc.kafuu.bilidownload.bilibili.account.callback;

import java.util.List;

import cc.kafuu.bilidownload.bilibili.model.BiliFavourite;
import cc.kafuu.bilidownload.bilibili.model.BiliVideo;

public interface IGetFavouriteCallback<T> {
    void completed(List<T> list, boolean hasMore);

    void failure(String message);
}
