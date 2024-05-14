package cc.kafuu.bilidownload.common.model.bili

class BiliMediaModel(
    title: String,
    cover: String,
    description: String,
    pubDate: Long,
    val mediaType: Int,
    val mediaId: Long,
    val seasonId: Long,
): BiliResourceModel(title, cover, description, pubDate)
