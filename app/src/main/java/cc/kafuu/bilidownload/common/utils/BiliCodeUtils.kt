package cc.kafuu.bilidownload.common.utils

import cc.kafuu.bilidownload.R

object BiliCodeUtils {
    fun getAudioIdDescribe(id: Int): String {
        return CommonLibs.getString(
            when (id) {
                30216 -> R.string.audio_description_64k
                30232 -> R.string.audio_description_132k
                30280 -> R.string.audio_description_192k
                30250 -> R.string.audio_description_dolby_atmos
                30251 -> R.string.audio_description_hi_res_lossless
                else -> R.string.audio_description_unknown
            }
        )
    }
}