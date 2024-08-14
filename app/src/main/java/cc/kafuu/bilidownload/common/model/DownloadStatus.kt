package cc.kafuu.bilidownload.common.model

enum class DownloadStatus(val code: Int, val isEndStatus: Boolean) {
    FAILURE(0, true),
    COMPLETED(1, true),
    STOPPED(2, false),
    WAITING(3, false),
    EXECUTING(4, false),
    PREPROCESSING(5, false),
    PREPROCESSING_COMPLETED(6, false),
    CANCELLED(7, true);

    companion object {
        fun fromCode(code: Int): DownloadStatus = entries.find { it.code == code }
            ?: throw IllegalArgumentException("Invalid code")
    }
}