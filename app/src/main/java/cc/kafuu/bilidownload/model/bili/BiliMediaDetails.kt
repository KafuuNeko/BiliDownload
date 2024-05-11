package cc.kafuu.bilidownload.model.bili

import java.io.Serializable

class BiliMediaDetails(
    title: String,
    cover: String,
    description: String,
    author: String,
    pubDate: Long,
    val mediaType: Int,
    val mediaId: Long,
    val seasonId: Long,
): BiliResourceDetails(title, cover, description, author, pubDate)
