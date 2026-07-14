package cc.kafuu.bilidownload.common.model

enum class DownloadPathMode(val code: Int) {
    INTERNAL(0),
    EXTERNAL(1),
    EXTERNAL_MEDIA(2);

    companion object {
        fun fromCode(code: Int): DownloadPathMode {
            return entries.firstOrNull { it.code == code } ?: INTERNAL
        }
    }
}
