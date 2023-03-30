package cc.kafuu.bilidownload.model;

import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.List;

import cc.kafuu.bilidownload.bilibili.model.BiliVideo;
import cc.kafuu.bilidownload.bilibili.model.BiliFollowType;

public class FollowViewModel extends ViewModel {
    public boolean firstLoad = true;

    public List<BiliVideo> records = new ArrayList<>();
    public BiliFollowType followType;
    public int page = 1;
    public boolean hasMore = true;

}
