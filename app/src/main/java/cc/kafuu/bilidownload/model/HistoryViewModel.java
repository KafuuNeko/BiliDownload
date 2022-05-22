package cc.kafuu.bilidownload.model;

import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.List;

import cc.kafuu.bilidownload.adapter.VideoListAdapter;
import cc.kafuu.bilidownload.bilibili.account.BiliHistory;

public class HistoryViewModel extends ViewModel {
    public boolean firstLoad = true;

    public BiliHistory.Cursor nextCursor;
    public List<VideoListAdapter.Record> records = new ArrayList<>();
}
