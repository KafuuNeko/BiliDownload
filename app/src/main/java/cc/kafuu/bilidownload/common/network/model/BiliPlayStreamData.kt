package cc.kafuu.bilidownload.common.network.model

import com.google.gson.annotations.SerializedName

// Bili取视频流响应
data class BiliPlayStreamData(
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
    val dash: BiliPlayStreamDash
)

// 外层 Dash 对象
data class BiliPlayStreamDash(
    val duration: Int,
    @SerializedName("minBufferTime")
    val minBufferTime: Double,
    val video: List<BiliPlayStreamResource>,
    val audio: List<BiliPlayStreamResource>,
    @SerializedName("support_formats")
    val supportFormats: List<BiliPlayStreamSupportFormat>,
    val dolby: BiliPlayDolby?,
    val flac: BiliPlayFlac?
) {
    fun getAllAudio() = mutableListOf<BiliPlayStreamResource>().apply {
        addAll(audio)
        dolby?.audio?.let { addAll(it) }
        flac?.audio?.let { add(it) }
    }
}

// 视频信息
data class BiliPlayStreamResource(
    val id: Long,
    @SerializedName("baseUrl")
    val baseUrl: String?,
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
    val segmentBase: BiliPlayStreamSegmentBase,
    @SerializedName("codecid")
    val codecId: Long
) {
    fun getStreamUrl(): String {
        baseUrl?.let { return it }
        if (!backupUrl.isNullOrEmpty()) {
            return backupUrl[0]
        }
        throw IllegalArgumentException("No stream url")
    }
}

// SegmentBase 信息
data class BiliPlayStreamSegmentBase(
    @SerializedName("Initialization")
    val initialization: String,
    @SerializedName("indexRange")
    val indexRange: String
)

// 支持的格式
data class BiliPlayStreamSupportFormat(
    val quality: Int,
    val format: String,
    @SerializedName("new_description")
    val newDescription: String,
    @SerializedName("display_desc")
    val displayDesc: String,
    val superscript: String,
    val codecs: List<String>
)

data class BiliPlayFlac(
    val display: Boolean,
    val audio: BiliPlayStreamResource?
)

data class BiliPlayDolby(
    val type: Int,
    val audio: List<BiliPlayStreamResource>?,
)