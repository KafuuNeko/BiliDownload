package cc.kafuu.bilidownload.bilibili.account.callback;

import java.util.List;

import cc.kafuu.bilidownload.bilibili.account.BiliHistoryParser;
import cc.kafuu.bilidownload.bilibili.model.BiliVideo;

public interface IGetHistoryCallback {
    void completed(List<BiliVideo> records, BiliHistoryParser.Cursor nextCursor);

    void failure(String message);
}
