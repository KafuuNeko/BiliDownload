package cc.kafuu.bilidownload.common.network.model

import com.google.gson.annotations.SerializedName

// 账户详情信息
data class BiliAccountData(
    // mid
    val mid: Long,
    // 男/女/保密
    val name: String,
    // 性别
    val sex: String,
    // 头像链接
    @SerializedName("face") val avatarUrl: String,
    // 是否为 NFT 头像， 0：不是 NFT 头像，1：是 NFT 头像
    @SerializedName("face_nft") val isNftAvatar: Int,
    // NFT 头像类型
    @SerializedName("face_nft_type") val nftAvatarType: Int?,
    // 签名
    val sign: String,
    // 当前等级 0-6 级
    val level: Int,
    // 封禁状态0：正常 1：被封
    @SerializedName("silence") val banStatus: Int,
    // 会员信息
    @SerializedName("vip") val vipStatus: BiliAccountVip,
    // 主页头图链接
    @SerializedName("top_photo") val topPhotoUrl: String
)

// 用户账户信息
data class MyBiliAccountData(
    // mid
    val mid: Long,
    // 用户名
    val uname: String,
    // 用户id
    val userid: String,
    // 签名
    val sign: String,
    // 生日
    val birthday: String,
    // 性别
    val sex: String,
)

data class BiliAccountVip(
    // 会员类型	0：无；1：月大会员；2：年度及以上大会员
    val type: Int,
    // 会员状态	 0：无；1：有
    val status: Int
)