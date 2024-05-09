package cc.kafuu.bilidownload.model.bili

import java.io.Serializable

data class BiliMedia(
    val mediaId: Long,
    val seasonId: Long,
    val title: String,
    val cover: String,
    val mediaType: Int,
    val desc: String,
): Serializable
