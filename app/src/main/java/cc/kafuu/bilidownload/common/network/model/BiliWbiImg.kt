package cc.kafuu.bilidownload.common.network.model

import com.google.gson.annotations.SerializedName

data class BiliWbiImg(
    @SerializedName("img_url") val imgUrl: String,
    @SerializedName("sub_url") val subUrl: String
)