package cc.kafuu.bilidownload.common.model.bili

data class BiliVideoPartModel(
    val bvid: String,
    val cid: Long,
    val name: String,
    val remark: String?
)