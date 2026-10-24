package cc.kafuu.bilidownload.common.utils

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import android.system.StructStat
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import cc.kafuu.bilidownload.common.manager.AccountManager
import cc.kafuu.bilidownload.common.network.NetworkConfig
import cc.kafuu.bilidownload.common.network.manager.NetworkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.Locale

/**
 * 连接本地文件、ContentResolver 与系统分享/导出入口，兼容路径和公共 URI 两类来源。
 *
 * 描述符和流由打开它们的方法或返回流的持有者关闭；文件删除与资源记录更新由业务用例负责。
 */
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
        return String.format(Locale.ROOT, "%.1f %sB", sizeInUnit, " KMGTPE"[z])
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
     * 启动系统创建文档页面，仅选择目标位置，不执行文件复制。
     *
     * 此处缺少 Context，无法验证 URI 的当前读取权限，调用方必须先核验来源可读性。
     * 系统返回目标 URI 后再调用 [writeFileToUri]；取消选择由页面清理待导出状态。
     *
     * @param file 要导出的文件
     * @param name 建议的目标显示名称，进入系统选择器前统一清理非法字符。
     * @param mimetype 文件的MIME类型
     * @param createDocumentLauncher ActivityResultLauncher，用于启动创建文档的Intent
     * @param contentUri 路径失效时仍可使用的读取 URI，由调用方验证可读性。
     */
    fun tryExportFile(
        file: File,
        name: String,
        mimetype: String,
        createDocumentLauncher: ActivityResultLauncher<Intent>,
        contentUri: String? = null,
    ) {
        if (!file.isFile && contentUri.isNullOrBlank()) return
        // 调用方可能直接使用标题，系统创建文档前统一清理，避免非法字符导致创建失败。
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = mimetype
            putExtra(Intent.EXTRA_TITLE, DownloadFileNameUtils.sanitizeFileName(name))
        }
        createDocumentLauncher.launch(intent)
    }

    /**
     * 将路径或内容 URI 的数据复制到目标文档，方法自身不删除源文件或修改数据库。
     *
     * - 同步执行文件及提供器 IO，调用方须安排到后台线程。
     * - 普通复制先检查可读目标是否为源文件别名，再以截断模式写入，兼容只支持写入的提供器。
     * - 核验复制要求目标支持非截断的读写描述符和回读，在写入前再次核对文件身份，写完后比较摘要。
     * - 写入失败可能留下部分目标文件；返回 false 时业务层必须保留源文件，不能据此执行移动清理。
     *
     * @param context 上下文对象
     * @param uri 目标URI
     * @param sourceFile 源文件
     * @param verifyContents 是否以 SHA-256 回读核验；业务层计划删除源文件时必须开启。
     * @param sourceContentUri 首选来源 URI，失效时回退到源文件
     * @return 流关闭且本次要求的检查通过时为 true；普通复制成功不包含内容回读保证。
     */
    fun writeFileToUri(
        context: Context,
        uri: Uri,
        sourceFile: File,
        verifyContents: Boolean = false,
        sourceContentUri: String? = null,
    ): Boolean {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            openSourceInput(context, sourceFile, sourceContentUri).use { input ->
                val sourceStat = Os.fstat(input.fd)
                // 普通复制也先识别可读目标的文件身份，避免选择同一资源的另一个 URI 时清空源。
                if (isSameSource(context, uri, sourceStat)) return false
                if (!verifyContents) {
                    // 明确要求截断旧目标，避免覆盖较长文件时残留尾部；只写提供器仍可走此分支。
                    val output = context.contentResolver.openOutputStream(uri, "wt") ?: return false
                    output.use { copyStream(input, it) }
                    return true
                }

                // 先以不截断的方式打开并比较文件标识，防止不同 URI 实际指向同一个源文件。
                val descriptor = context.contentResolver.openFileDescriptor(uri, "rw") ?: return false
                ParcelFileDescriptor.AutoCloseOutputStream(descriptor).use { output ->
                    val targetStat = Os.fstat(descriptor.fileDescriptor)
                    if (sourceStat.st_dev == targetStat.st_dev && sourceStat.st_ino == targetStat.st_ino) {
                        return false
                    }
                    output.channel.truncate(0)
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var copiedBytes = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        digest.update(buffer, 0, count)
                        copiedBytes += count
                    }
                    // 普通文件核对复制前后长度；管道来源不能依赖 st_size 或随机访问能力。
                    if (OsConstants.S_ISREG(sourceStat.st_mode) &&
                        (copiedBytes != sourceStat.st_size || input.channel.size() != copiedBytes)) {
                        return false
                    }
                }
            }
            // 输出描述符关闭后重新读取目标，不能只相信提供器报告的长度或写入成功返回值。
            verifyUriContents(context, uri, digest.digest())
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 优先打开 content URI，缺失、撤权或打开失败时回退到本地路径。
     *
     * 返回流接管 URI 描述符的所有权，调用方必须使用 use 关闭；不要求旧路径与 URI 同时有效。
     * URI 提供器可能返回管道，因此后续复制不能对所有来源都假设支持 seek 或具有可靠长度。
     */
    private fun openSourceInput(context: Context, file: File, contentUri: String?): FileInputStream {
        val uri = contentUri?.let(Uri::parse)?.takeIf { it.scheme == "content" }
        val descriptor = uri?.let {
            runCatching { context.contentResolver.openFileDescriptor(it, "r") }.getOrNull()
        }
        return descriptor?.let { ParcelFileDescriptor.AutoCloseInputStream(it) } ?: FileInputStream(file)
    }

    /**
     * 在任何可能截断文件的操作前，通过设备号与 inode 识别目标是否为源文件的别名。
     *
     * 返回 false 也可能表示提供器不支持读取，而非已经证明两者不同；普通复制保留只写兼容性，
     * 核验复制还必须在非截断的读写描述符上再次检查，不能省略第二道保护。
     */
    private fun isSameSource(context: Context, uri: Uri, source: StructStat): Boolean = runCatching {
        context.contentResolver.openFileDescriptor(uri, "r")?.use {
            val target = Os.fstat(it.fileDescriptor)
            source.st_dev == target.st_dev && source.st_ino == target.st_ino
        } ?: false
    }.getOrDefault(false)

    /** 回读整个目标并比较摘要，不依赖提供器的长度元数据；无法核验时上层须保留源文件。 */
    private fun verifyUriContents(context: Context, uri: Uri, expectedDigest: ByteArray): Boolean {
        val digest = MessageDigest.getInstance("SHA-256")
        val input = context.contentResolver.openInputStream(uri) ?: return false
        input.use {
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = it.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return MessageDigest.isEqual(expectedDigest, digest.digest())
    }

    /** 返回目标目录中尚未被占用的显示文件名；写入文档提供方前再次清理并保留原扩展名。 */
    fun resolveUniqueDocumentName(parent: DocumentFile, desiredName: String): String {
        return DownloadFileNameUtils.resolveUniqueFileName(
            DownloadFileNameUtils.sanitizeFileName(desiredName),
        ) {
            parent.findFile(it) != null
        }
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
