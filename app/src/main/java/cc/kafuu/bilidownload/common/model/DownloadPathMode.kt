package cc.kafuu.bilidownload.common.model

/**
 * 下载完成后的资源存储策略。
 *
 * [code] 会持久化到首选项中，因此已有取值不可随意调整。
 */
enum class DownloadPathMode(val code: Int) {
    /** 保留在应用专属目录中。 */
    INTERNAL(0),

    /** 发布到公共 Download/BVD 目录。 */
    EXTERNAL(1),

    /** 视频发布到 Movies/BVD，其他资源发布到 Download/BVD。 */
    EXTERNAL_MEDIA(2);

    companion object {
        /**
         * 将持久化的数值还原为存储策略。
         *
         * 未知值回退到内部存储，避免升级或数据损坏时意外写入公共目录。
         */
        fun fromCode(code: Int): DownloadPathMode {
            return entries.firstOrNull { it.code == code } ?: INTERNAL
        }
    }
}
