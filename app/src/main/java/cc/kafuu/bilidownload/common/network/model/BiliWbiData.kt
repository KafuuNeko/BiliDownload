package cc.kafuu.bilidownload.common.network.model

import com.google.gson.annotations.SerializedName

data class BiliWbiData(
    @SerializedName("wbi_img") val wbiImg: BiliWbiImg
)

data class BiliWbiImg(
    @SerializedName("img_url") val imgUrl: String,
    @SerializedName("sub_url") val subUrl: String
)