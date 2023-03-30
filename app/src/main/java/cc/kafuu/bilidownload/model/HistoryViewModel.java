package cc.kafuu.bilidownload.model;

import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.List;

import cc.kafuu.bilidownload.bilibili.account.BiliHistoryParser;
import cc.kafuu.bilidownload.bilibili.model.BiliVideo;

public class HistoryViewModel extends ViewModel {
    public boolean firstLoad = true;

    public BiliHistoryParser.Cursor nextCursor;
    public List<BiliVideo> records = new ArrayList<>();
}
