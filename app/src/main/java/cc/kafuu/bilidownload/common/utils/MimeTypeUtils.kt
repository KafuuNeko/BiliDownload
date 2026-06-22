package cc.kafuu.bilidownload.common.utils

object MimeTypeUtils {
    private val mimeTypeToExtensionMap = mapOf(
        // 音频格式
        "audio/mp4" to "mp4",
        "audio/aac" to "aac",
        "audio/x-aac" to "aac",
        "audio/mpeg" to "mp3",
        "audio/ogg" to "ogg",
        "audio/opus" to "opus",
        "audio/wav" to "wav",
        "audio/webm" to "webm",
        "audio/flac" to "flac",
        "audio/x-flac" to "flac",
        "audio/ac3" to "ac3",
        "audio/eac3" to "eac3",
        "audio/x-ms-wma" to "wma",

        // 视频格式
        "video/mp4" to "mp4",
        "video/x-msvideo" to "avi",
        "video/x-matroska" to "mkv",
        "video/webm" to "webm",
        "video/ogg" to "ogv",
        "video/quicktime" to "mov",
        "video/x-flv" to "flv",
        "video/x-ms-wmv" to "wmv",

        // 图像格式
        "image/jpeg" to "jpg",
        "image/png" to "png",
        "image/gif" to "gif",
        "image/webp" to "webp",
        "image/svg+xml" to "svg",
        "image/bmp" to "bmp",

        // 文档格式
        "application/pdf" to "pdf",
        "application/msword" to "doc",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document" to "docx",
        "application/vnd.ms-excel" to "xls",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" to "xlsx",
        "application/vnd.ms-powerpoint" to "ppt",
        "application/vnd.openxmlformats-officedocument.presentationml.presentation" to "pptx",
    )

    /**
     * 根据 MIME 类型获取文件后缀名。
     * @param mimeType 文件的 MIME 类型。
     * @return 对应的文件后缀名，如果找不到匹配项，返回 null。
     */
    fun getExtensionFromMimeType(mimeType: String): String? {
        return mimeTypeToExtensionMap[mimeType.lowercase()]
    }

    fun isAudioMimeType(mimeType: String): Boolean {
        return mimeType.lowercase().startsWith("audio/")
    }

    fun isVideoMimeType(mimeType: String): Boolean {
        return mimeType.lowercase().startsWith("video/")
    }
}
