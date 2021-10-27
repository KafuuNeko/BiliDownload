package cc.kafuu.bilidownload.bilibili.resource;

public class BiliAudioResource extends BiliResource {
    //编解码器
    private final String mCodecs;

    public BiliAudioResource(int id, final String videoUrl, final String url, final String description, final String codecs) {
        super(id, videoUrl, url, description);
        this.mCodecs = codecs;
    }

    public String getCodecs() {
        return mCodecs;
    }
}
