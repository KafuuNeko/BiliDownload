package androidx.documentfile.provider

import android.webkit.MimeTypeMap

/**
 * 为真实文件 IO 测试提供与 SAF 一致的扩展名处理，仅打包进测试 APK。
 *
 * DocumentFile 构造器为包可见，因此夹具放在同包；RawDocumentFile 默认无条件追加扩展名。
 * 不模拟系统 URI 授权，授权流程仍需单独设备验收。
 */
class QaExportDirectory(private val directory: DocumentFile) : DocumentFile(null) {
    override fun createFile(mimeType: String, displayName: String): DocumentFile? {
        val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
        val name = if (extension != null) displayName.removeSuffix(".$extension") else displayName
        return directory.createFile(mimeType, name)
    }
    override fun createDirectory(displayName: String) = directory.createDirectory(displayName)
    override fun getUri() = directory.uri
    override fun getName() = directory.name
    override fun getType() = directory.type
    override fun isDirectory() = directory.isDirectory
    override fun isFile() = directory.isFile
    override fun isVirtual() = directory.isVirtual
    override fun lastModified() = directory.lastModified()
    override fun length() = directory.length()
    override fun canRead() = directory.canRead()
    override fun canWrite() = directory.canWrite()
    override fun delete() = directory.delete()
    override fun exists() = directory.exists()
    override fun listFiles() = directory.listFiles()
    override fun renameTo(displayName: String) = directory.renameTo(displayName)
}
