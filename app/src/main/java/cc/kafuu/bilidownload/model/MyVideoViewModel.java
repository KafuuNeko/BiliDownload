package cc.kafuu.bilidownload.model;

import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.List;

import cc.kafuu.bilidownload.bilibili.model.BiliVideo;

public class MyVideoViewModel extends ViewModel {
    public boolean firstLoad = true;

    public List<BiliVideo> records = new ArrayList<>();

    public int page = 1;
    public boolean hasMore = true;
}
