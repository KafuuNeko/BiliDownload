package cc.kafuu.bilidownload.model;

import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.List;

import cc.kafuu.bilidownload.adapter.VideoListAdapter;
import cc.kafuu.bilidownload.bilibili.account.BiliFollow;

public class MyVideoViewModel extends ViewModel {
    public boolean firstLoad = true;

    public List<VideoListAdapter.VideoRecord> records = new ArrayList<>();

    public int page = 1;
    public boolean hasMore = true;
}
