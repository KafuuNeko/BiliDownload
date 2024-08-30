package cc.kafuu.bilidownload.common.model.bili

import cc.kafuu.bilidownload.common.network.model.BiliAccountData

data class BiliAccountModel(
    val mid: Long,
    val nickname: String,
    val profile: String,
    val sign: String
) {
    companion object {
        fun create(data: BiliAccountData) = BiliAccountModel(
            mid = data.mid,
            nickname = data.name,
            profile = data.avatarUrl,
            sign = data.sign
        )
    }
}