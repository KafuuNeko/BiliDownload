package cc.kafuu.bilidownload.model.bili

data class BiliVideoPart(
    val bvid: String,
    val cid: Long,
    val name: String,
    val duration: String?
)