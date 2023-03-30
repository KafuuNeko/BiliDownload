package cc.kafuu.bilidownload.model;

import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.List;

import cc.kafuu.bilidownload.bilibili.model.BiliVideo;
import cc.kafuu.bilidownload.bilibili.model.BiliFavourite;

public class FavoriteViewModel extends ViewModel {
    public List<BiliFavourite> favourites = null;

    public int currentFavourite = 0;

    public boolean loading = false;
    public boolean hasMore = false;
    public int nextPage = 1;

    public List<BiliVideo> records = new ArrayList<>();
}
