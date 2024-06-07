package cc.kafuu.bilidownload.common.utils

object FileUtils {
    fun formatFileSize(sizeBytes: Long): String {
        if (sizeBytes < 1024) return "$sizeBytes B"
        val z = (63 - sizeBytes.countLeadingZeroBits()) / 10
        val sizeInUnit = sizeBytes.toDouble() / (1L shl (z * 10))
        return String.format("%.1f %sB", sizeInUnit, " KMGTPE"[z])
    }
}