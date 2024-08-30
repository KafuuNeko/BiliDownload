package cc.kafuu.bilidownload.common.model.bili

import cc.kafuu.bilidownload.common.network.model.BiliFavoriteData

data class BiliFavoriteModel(
    // 收藏夹mlid（完整id), 收藏夹原始id+创建者mid尾号2位
    val id: Long,
    // 收藏夹原始id
    val fid: Long,
    // 创建者mid
    val mid: Long,
    // 收藏夹标题
    val title: String,
    // 收藏夹内容数量
    val mediaCount: Int,
    // 收藏夹封面图片url
    val cover: String?,
) {
    companion object {
        fun create(data: BiliFavoriteData, cover: String?) = BiliFavoriteModel(
            id = data.id,
            fid = data.fid,
            mid = data.mid,
            title = data.title,
            mediaCount = data.mediaCount,
            cover = cover
        )
    }
}
