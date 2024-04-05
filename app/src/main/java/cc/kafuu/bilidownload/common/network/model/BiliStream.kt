package cc.kafuu.bilidownload.common.network.model

import com.google.gson.annotations.SerializedName

// Bili取视频流响应
data class BiliStreamData(
    val quality: Int,
    val format: String,
    @SerializedName("timelength")
    val timeLength: Long,
    @SerializedName("accept_format")
    val acceptFormat: String,
    @SerializedName("accept_description")
    val acceptDescription: List<String>,
    @SerializedName("accept_quality")
    val acceptQuality: List<Int>,
    val dash: BiliStreamDash
)

// 外层 Dash 对象
data class BiliStreamDash(
    val duration: Int,
    @SerializedName("minBufferTime")
    val minBufferTime: Double,
    val video: List<BiliStreamResource>,
    val audio: List<BiliStreamResource>,
    @SerializedName("support_formats")
    val supportFormats: List<BiliStreamSupportFormat>
)

// 视频信息
data class BiliStreamResource(
    val id: Int,
    @SerializedName("baseUrl")
    val baseUrl: String,
    @SerializedName("backupUrl")
    val backupUrl: List<String>?,
    val bandwidth: Long,
    @SerializedName("mimeType")
    val mimeType: String,
    val codecs: String,
    val width: Int,
    val height: Int,
    @SerializedName("frameRate")
    val frameRate: String,
    val sar: String,
    @SerializedName("startWithSap")
    val startWithSap: Int,
    @SerializedName("SegmentBase")
    val segmentBase: BiliStreamSegmentBase,
    @SerializedName("codecid")
    val codecId: Int
)

// SegmentBase 信息
data class BiliStreamSegmentBase(
    @SerializedName("Initialization")
    val initialization: String,
    @SerializedName("indexRange")
    val indexRange: String
)

// 支持的格式
data class BiliStreamSupportFormat(
    val quality: Int,
    val format: String,
    @SerializedName("new_description")
    val newDescription: String,
    @SerializedName("display_desc")
    val displayDesc: String,
    val superscript: String,
    val codecs: List<String>
)
