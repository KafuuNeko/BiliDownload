package cc.kafuu.bilidownload.common.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.FileProvider
import java.io.File
import java.io.InputStream
import java.io.OutputStream

object FileUtils {
    /**
     * 格式化文件大小
     *
     * @param sizeBytes 文件大小，单位为字节
     * @return 格式化后的文件大小字符串
     */
    fun formatFileSize(sizeBytes: Long): String {
        if (sizeBytes < 1024) return "$sizeBytes B"
        val z = (63 - sizeBytes.countLeadingZeroBits()) / 10
        val sizeInUnit = sizeBytes.toDouble() / (1L shl (z * 10))
        return String.format("%.1f %sB", sizeInUnit, " KMGTPE"[z])
    }

    /**
     * 尝试分享文件
     *
     * @param context 上下文对象
     * @param title 分享对话框的标题
     * @param file 要分享的文件
     * @param mimetype 文件的MIME类型
     */
    fun tryShareFile(context: Context, title: String, file: File, mimetype: String) {
        if (!file.exists()) return
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimetype
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(
            Intent.createChooser(intent, title)
        )
    }

    /**
     * 尝试导出文件
     *
     * @param file 要导出的文件
     * @param mimetype 文件的MIME类型
     * @param createDocumentLauncher ActivityResultLauncher，用于启动创建文档的Intent
     */
    fun tryExportFile(
        file: File,
        mimetype: String,
        createDocumentLauncher: ActivityResultLauncher<Intent>
    ) {
        if (!file.exists()) return
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = mimetype
            putExtra(Intent.EXTRA_TITLE, file.name)
        }
        createDocumentLauncher.launch(intent)
    }

    /**
     * 将文件写入到指定的URI
     *
     * @param context 上下文对象
     * @param uri 目标URI
     * @param sourceFile 源文件
     * @return 成功返回true，失败返回false
     */
    fun writeFileToUri(context: Context, uri: Uri, sourceFile: File) = try {
        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
            sourceFile.inputStream().use { inputStream ->
                copyStream(inputStream, outputStream)
            }
        }
        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }

    private fun copyStream(inputStream: InputStream, outputStream: OutputStream) {
        val buffer = ByteArray(2048)
        var length: Int
        while (inputStream.read(buffer).also { length = it } > 0) {
            outputStream.write(buffer, 0, length)
        }
    }
}