package cc.kafuu.bilidownload.common.network.model

import com.google.gson.annotations.SerializedName

data class BiliWbiData(
    @SerializedName("wbi_img") val wbiImg: BiliWbiImg
)