package cc.kafuu.bilidownload.common.ext

fun String.limit(maxLength: Int): String {
    return if (length > maxLength) take(maxLength - 3) + "..." else this
}