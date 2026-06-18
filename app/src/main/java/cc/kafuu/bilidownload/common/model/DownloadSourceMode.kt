package cc.kafuu.bilidownload.common.model

enum class DownloadSourceMode(val code: Int) {
    DEFAULT(0),
    AUTO_PROBE(1),
    CUSTOM_HOST(2);

    companion object {
        fun fromCode(code: Int): DownloadSourceMode {
            return entries.firstOrNull { it.code == code } ?: DEFAULT
        }
    }
}
