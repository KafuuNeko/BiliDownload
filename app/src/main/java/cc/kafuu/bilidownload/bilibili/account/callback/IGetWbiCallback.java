package cc.kafuu.bilidownload.bilibili.account.callback;



public interface IGetWbiCallback {
    void completed(String imgUrl, String subUrl);

    void failure(String message);
}
