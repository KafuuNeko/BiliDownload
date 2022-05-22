package cc.kafuu.bilidownload.model;

import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.List;

import cc.kafuu.bilidownload.adapter.VideoListAdapter;
import cc.kafuu.bilidownload.bilibili.account.BiliFavourite;

public class FavoriteViewModel extends ViewModel {
    public List<BiliFavourite.Favourite> favourites = null;

    public int currentFavourite = 0;

    public boolean loading = false;
    public boolean hasMore = false;
    public int nextPage = 1;

    public List<VideoListAdapter.Record> records = new ArrayList<>();
}
