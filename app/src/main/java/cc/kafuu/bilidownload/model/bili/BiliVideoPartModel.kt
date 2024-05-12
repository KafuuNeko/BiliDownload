package cc.kafuu.bilidownload.model.bili

data class BiliVideoPartModel(
    val bvid: String,
    val cid: Long,
    val name: String,
    val remark: String?
)