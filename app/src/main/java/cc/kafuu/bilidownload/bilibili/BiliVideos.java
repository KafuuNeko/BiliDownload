package cc.kafuu.bilidownload.bilibili;

import java.util.List;

import cc.kafuu.bilidownload.bilibili.resource.BiliResource;
import okhttp3.Response;

public class BiliVideos {
    private Response mResponse;
    private List<BiliResource> mResources;

    public BiliVideos(Response response, List<BiliResource> resources) {
        this.mResponse = response;
        this.mResources = resources;
    }

    public List<BiliResource> getResources() {
        return mResources;
    }
}
