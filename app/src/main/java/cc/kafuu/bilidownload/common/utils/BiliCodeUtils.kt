package cc.kafuu.bilidownload.common.utils

import cc.kafuu.bilidownload.R
import cc.kafuu.bilidownload.common.CommonLibs

object BiliCodeUtils {
    fun getAudioQualityDescribe(id: Long): String {
        return CommonLibs.getString(
            when (id) {
                30216L -> R.string.audio_description_64k
                30232L -> R.string.audio_description_132k
                30280L -> R.string.audio_description_192k
                30250L -> R.string.audio_description_dolby_atmos
                30251L -> R.string.audio_description_hi_res_lossless
                else -> R.string.audio_description_unknown
            }
        )
    }

    fun getVideoQualityDescription(id: Long): String {
        return CommonLibs.getString(
            when (id) {
                6L -> R.string.video_description_240p
                16L -> R.string.video_description_360p
                32L -> R.string.video_description_480p
                64L -> R.string.video_description_720p
                74L -> R.string.video_description_720p60
                80L -> R.string.video_description_1080p
                112L -> R.string.video_description_1080p_plus
                116L -> R.string.video_description_1080p60
                120L -> R.string.video_description_4k
                125L -> R.string.video_description_hdr
                126L -> R.string.video_description_dolby_vision
                127L -> R.string.video_description_8k
                else -> R.string.video_description_unknown
            }
        )
    }

}