package cc.kafuu.bilidownload.bilibili.resource;

public class BiliVideoResource extends BiliResource {

    private final String mFormat;
    //编解码器
    private final String mCodecs;

    public BiliVideoResource(int id, final String videoUrl, final String url, final String description, final String format, final String codecs) {
        super(id, videoUrl, url, description);
        this.mFormat = format;
        this.mCodecs = codecs;
    }

    public String getFormat() {
        return mFormat;
    }

    public String getCodecs() {
        return mCodecs;
    }
}
