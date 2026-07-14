package cc.kafuu.bilidownload.common.utils

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.FileProvider
import cc.kafuu.bilidownload.common.manager.AccountManager
import cc.kafuu.bilidownload.common.network.NetworkConfig
import cc.kafuu.bilidownload.common.network.manager.NetworkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
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
     * @param contentUri 已登记到媒体库的读取 URI；可访问时优先于文件路径
     */
    fun tryShareFile(
        context: Context,
        title: String,
        file: File,
        mimetype: String,
        contentUri: String? = null
    ) {
        val uri = resolveReadUri(context, file, contentUri) ?: return

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimetype
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newUri(context.contentResolver, file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(
            Intent.createChooser(intent, title)
        )
    }

    /**
     * 尝试使用其他应用打开文件
     *
     * @param context 上下文对象
     * @param title 应用选择器标题
     * @param file 要打开的文件
     * @param mimetype 文件的MIME类型
     * @param contentUri 已登记到媒体库的读取 URI；可访问时优先于文件路径
     * @return 是否成功启动系统应用选择器
     */
    fun tryOpenFileWithOtherApp(
        context: Context,
        title: String,
        file: File,
        mimetype: String,
        contentUri: String? = null
    ): Boolean {
        return try {
            val uri = resolveReadUri(context, file, contentUri) ?: return false

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimetype)
                clipData = ClipData.newUri(context.contentResolver, file.name, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(Intent.createChooser(intent, title))
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    /**
     * 解析可授予其他组件读取权限的 URI。
     *
     * 优先验证并使用 MediaStore 的 `content://` URI；URI 缺失或失效时，如果文件仍存在，
     * 再通过 FileProvider 生成回退 URI。两种方式都不可用时返回 `null`。
     *
     * @param context 用于访问 ContentResolver 和 FileProvider 的上下文。
     * @param file URI 回退所使用的本地文件。
     * @param contentUri 数据库中保存的媒体库 URI。
     */
    fun resolveReadUri(context: Context, file: File, contentUri: String? = null): Uri? {
        contentUri?.let(Uri::parse)
            ?.takeIf { it.scheme == "content" && canReadUri(context, it) }
            ?.let { return it }

        if (!file.isFile) return null
        return try {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: SecurityException) {
            null
        }
    }

    /** 通过实际打开文件描述符验证当前进程仍可读取 URI。 */
    private fun canReadUri(context: Context, uri: Uri): Boolean {
        return try {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { true } ?: false
        } catch (_: Exception) {
            false
        }
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
        name: String,
        mimetype: String,
        createDocumentLauncher: ActivityResultLauncher<Intent>
    ) {
        if (!file.exists()) return
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = mimetype
            putExtra(Intent.EXTRA_TITLE, name)
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

    /**
     * 从URL下载图片到临时文件
     *
     * @param url 图片URL
     * @param tempFile 临时文件
     * @return 成功返回true，失败返回false
     */
    suspend fun downloadImageToFile(url: String, tempFile: File): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().apply {
                url(url)
                get()
                NetworkConfig.GENERAL_HEADERS.forEach { (key, value) -> addHeader(key, value) }
                AccountManager.cookiesLiveData.value?.let { addHeader("Cookie", it) }
            }.build()

            val response = NetworkManager.okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext false
            }

            response.body()?.let { body ->
                tempFile.outputStream().use { outputStream ->
                    body.byteStream().use { inputStream ->
                        copyStream(inputStream, outputStream)
                    }
                }
                true
            } ?: false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
