package cc.kafuu.bilidownload.common.network

object NetworkConfig {
    const val BILI_URL = "https://m.bilibili.com"
    const val BILI_API_URL = "https://api.bilibili.com"
    const val BILI_PASSPORT_URL = "https://passport.bilibili.com"
    const val LOGIN_URL = "${BILI_PASSPORT_URL}/h5-app/passport/login"

    const val SOURCE_REPOSITORY_URL = "https://github.com/KafuuNeko/BiliDownload"
    const val OPEN_SOURCE_LICENSES_URL = "https://github.com/KafuuNeko/BiliDownload/blob/master/LICENSE"
    const val FEEDBACK_URL = "https://github.com/KafuuNeko/BiliDownload/issues"
    const val GOOGLE_PLAY_URL = "https://play.google.com/store/apps/details?id=cc.kafuu.bilidownload"


    val GENERAL_HEADERS = HashMap<String, String>().apply {
        put(
            "User-Agent",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36 Edg/124.0.0.0"
        )
        put("Accept", "application/json, text/plain, */*")
        put("Accept-Language", "zh-CN,zh-Hans;q=0.9")
        put("Origin", BILI_URL)
        put("Referer", BILI_URL)
    }

    val DOWNLOAD_HEADERS = HashMap<String, String>(GENERAL_HEADERS).apply {
        put("Accept", "*/*")
        put("Accept-Language", "gzip, deflate, br")
        put("Connection", "keep-alive")
    }

    /**
     * 构建完整的 URL。
     *
     * @param path API 的路径。
     * @param params 查询参数，应已被编码并以适当的格式提供。
     * @return 构建的完整 URL。
     */
    fun buildFullUrl(path: String, params: String?): String {
        // 检查 params 是否为空或空字符串，如果是，则不添加查询参数
        val queryParams = if (!params.isNullOrEmpty()) "?$params" else ""
        return "$BILI_API_URL$path$queryParams"
    }
}