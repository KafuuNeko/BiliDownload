package cc.kafuu.bilidownload.common.download

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.Properties

/**
 * 与 .part 同目录保存续传身份，不持久化带鉴权参数的完整地址。
 *
 * 跨地址追加要求强 ETag 一致；旧缓存没有检查点时安全重下，已完成的资源不受影响。
 */
internal class DownloadCheckpoint(private val part: File) {
    private val metadata = File(part.path + ".resume")

    /** 已接收响应的源指纹、强校验标识和总字节数；-1 表示长度未知。 */
    data class Identity(val sourceKey: String, val etag: String?, val total: Long)

    /** 读取可恢复的检查点；不完整的元数据不能作为追加依据。 */
    fun read(): Identity? = storage {
        if (!metadata.isFile) return@storage null
        val values = try {
            Properties().apply { metadata.inputStream().use { load(it) } }
        } catch (_: IllegalArgumentException) {
            return@storage null
        }
        val key = values.getProperty("source") ?: return@storage null
        val total = values.getProperty("total")?.toLongOrNull() ?: return@storage null
        if (total < -1 || !key.matches(Regex("[0-9a-f]{64}"))) return@storage null
        Identity(key, strongEtag(values.getProperty("etag")), total)
    }

    /** 正文写入前记录身份；元数据写入失败时不允许继续消费资源。 */
    fun write(identity: Identity) = storage {
        val values = Properties().apply {
            setProperty("source", identity.sourceKey)
            setProperty("total", identity.total.toString())
            identity.etag?.let { setProperty("etag", it) }
        }
        metadata.outputStream().use { values.store(it, null) }
    }

    /** 仅清空尚未完成的缓存，下一请求必须从头获取完整资源。 */
    fun reset() = storage {
        if (part.exists()) RandomAccessFile(part, "rw").use { it.setLength(0L) }
        if (metadata.exists() && !metadata.delete()) throw IOException("Checkpoint removal failed")
    }

    companion object {
        /** 对地址计算不可逆指纹，避免将签名查询参数写入检查点。 */
        fun sourceKey(url: String): String = MessageDigest.getInstance("SHA-256")
            .digest(url.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

        /** 只接受可用于跨源字节一致性校验的强 ETag。 */
        fun strongEtag(value: String?): String? = value?.takeIf {
            it.length >= 2 && it.startsWith('"') && it.endsWith('"')
        }

        /** 文件操作与网络 IO 分开分类，磁盘错误不得触发网络重试。 */
        inline fun <T> storage(block: () -> T): T = try {
            block()
        } catch (error: IOException) {
            if (error is ResourceDownloadException) throw error
            throw ResourceDownloadException(DownloadFailure(DownloadFailure.Kind.STORAGE))
        } catch (error: SecurityException) {
            throw ResourceDownloadException(DownloadFailure(DownloadFailure.Kind.STORAGE))
        }
    }
}
