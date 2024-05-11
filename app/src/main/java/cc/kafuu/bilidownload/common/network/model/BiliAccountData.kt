package cc.kafuu.bilidownload.common.network.model

import com.google.gson.annotations.SerializedName

data class BiliAccountData(
    val mid: Long,
    val name: String, //男/女/保密
    val sex: String,
    @SerializedName("face") val avatarUrl: String,
    @SerializedName("face_nft") val isNftAvatar: Int,
    @SerializedName("face_nft_type") val nftAvatarType: Int?,
    val sign: String,
    val rank: Int,
    val level: Int, // 0-6 级
    @SerializedName("jointime") val joinTime: Int,
    @SerializedName("moral") val moralValue: Int, // 节操 默认70
    @SerializedName("silence") val banStatus: Int, // 封禁状态0：正常 1：被封
    @SerializedName("vip") val vipStatus: BiliAccountVip,
    @SerializedName("top_photo") val topPhotoUrl: String
)

data class MyBiliAccountData(
    val mid: Long,
    val uname: String,
    val userid: String,
    val sign: String,
    val birthday: String,
    val sex: String,
    val rank: String
)

data class BiliAccountVip(
    val type: Int, // 会员类型	0：无；1：月大会员；2：年度及以上大会员
    val status: Int // 会员状态	 0：无；1：有
)