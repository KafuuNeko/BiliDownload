package cc.kafuu.bilidownload.common.ext

import java.io.File

fun File.getSplitExtension(): String {
    val name = name
    return if (name.contains(".")) {
        ".${name.substringAfterLast('.', "")}"
    } else ""
}