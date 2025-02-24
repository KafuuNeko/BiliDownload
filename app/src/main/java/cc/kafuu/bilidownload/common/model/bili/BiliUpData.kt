package cc.kafuu.bilidownload.common.model.bili

import cc.kafuu.bilidownload.common.network.model.BiliVideoOwner

data class BiliUpData(
    val mid: Long,
    val name: String,
    val face: String
) {
    companion object {
        fun from(owner: BiliVideoOwner) = BiliUpData(
            mid = owner.mid,
            name = owner.name,
            face = owner.face
        )
    }
}