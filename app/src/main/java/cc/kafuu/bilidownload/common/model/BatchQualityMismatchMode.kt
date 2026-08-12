package cc.kafuu.bilidownload.common.model

/**
 * 多选下载时，其他分 P 无法精确匹配首次选择资源的处理策略。
 *
 * [code] 会持久化到首选项中，因此已有取值不可随意调整。
 */
enum class BatchQualityMismatchMode(val code: Int) {
    /** 自动选择不高于首次选择画质的最佳可用资源。 */
    AUTO_FALLBACK(0),

    /** 无法精确匹配时再次询问用户。 */
    ASK(1),

    /** 跳过无法精确匹配的分 P。 */
    SKIP(2);

    companion object {
        fun fromCode(code: Int): BatchQualityMismatchMode {
            return entries.firstOrNull { it.code == code } ?: AUTO_FALLBACK
        }
    }
}
