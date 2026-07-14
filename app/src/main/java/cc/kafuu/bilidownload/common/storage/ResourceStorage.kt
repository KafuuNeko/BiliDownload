package cc.kafuu.bilidownload.common.storage

import android.content.ContentUris
import android.content.ContentValues
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.system.Os
import android.util.Log
import androidx.annotation.RequiresApi
import cc.kafuu.bilidownload.common.CommonLibs
import cc.kafuu.bilidownload.common.model.AppModel
import cc.kafuu.bilidownload.common.model.DownloadPathMode
import cc.kafuu.bilidownload.common.room.entity.DownloadResourceEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import kotlin.coroutines.resume

/**
 * 管理应用专属工作文件与公共下载文件之间的边界。
 *
 * Android 10 及以上通过 MediaStore 创建和删除公共资源，避免厂商 FUSE 将原始
 * File.delete() 当作移动到隐藏回收站处理。
 */
object ResourceStorage {
    private const val TAG = "ResourceStorage"
    private const val COPY_BUFFER_SIZE = 1024 * 1024
    private const val PUBLIC_SUBDIRECTORY = "BVD"

    private val relativeDownloadPath =
        "${Environment.DIRECTORY_DOWNLOADS}/$PUBLIC_SUBDIRECTORY/"
    private val relativeVideoPath =
        "${Environment.DIRECTORY_MOVIES}/$PUBLIC_SUBDIRECTORY/"

    /** 公共目录、MediaStore 相对路径和集合的组合，三者必须保持一致。 */
    private data class PublicTarget(
        val directory: File,
        val relativePath: String,
        val mediaCollection: MediaCollection
    )

    /** 资源应登记到的 MediaStore 集合。 */
    private enum class MediaCollection { DOWNLOADS, VIDEO }

    /**
     * 按当前存储策略把工作文件发布到公共媒体库。
     *
     * 发布过程遵循“先创建公共记录并保存检查点，再写入和核验内容，最后保存完整记录并
     * 删除私有源文件”的顺序。这样应用或数据库写入中断后仍能重试，不会同时丢失两份文件。
     * 已经位于公共目录或仅剩可访问 [DownloadResourceEntity.contentUri] 的资源会直接恢复记录。
     *
     * @param resource 待发布或恢复的资源记录。
     * @param forcePublish 为 `true` 时忽略当前内部存储设置，用于恢复 `PUBLISHING` 或
     * `PUBLISH_FAILED` 任务。
     * @param persistCheckpoint 将中间 URI 或最终公共资源信息持久化到数据库的回调；回调失败
     * 会撤销新建的公共副本并保留源文件。
     * @return 无需发布时返回原记录，否则返回包含公共路径和 URI 的最新记录。
     * @throws IOException 当源文件和已保存的公共资源均不可用，或复制、核验失败时抛出。
     */
    suspend fun publishIfNeeded(
        resource: DownloadResourceEntity,
        forcePublish: Boolean = false,
        persistCheckpoint: suspend (DownloadResourceEntity) -> Unit
    ): DownloadResourceEntity =
        withContext(Dispatchers.IO) {
            if (!forcePublish && AppModel.downloadPathMode == DownloadPathMode.INTERNAL) {
                return@withContext resource
            }

            val source = File(resource.file)
            if (!source.isFile) {
                resolveStoredResource(resource)?.let { published ->
                    if (published != resource) persistCheckpoint(published)
                    return@withContext published
                }
                throw IOException("Resource file does not exist: ${source.absolutePath}")
            }

            if (isPublicResourceFile(source)) {
                val uri = parseAccessibleContentUri(resource.contentUri)
                    ?: findMediaStoreUri(source)
                    ?: scanFile(source, resource.mimeType)
                val published = resource.copy(contentUri = uri?.toString())
                if (published != resource) persistCheckpoint(published)
                return@withContext published
            }

            val target = resolvePublicTarget(resource)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                publishWithMediaStore(resource, source, target, persistCheckpoint)
            } else {
                publishLegacy(resource, source, target, persistCheckpoint)
            }
        }

    /**
     * 删除资源的实际文件。
     *
     * 有 MediaStore URI 时优先通过 URI 删除，以兼容 Android 10 以上的分区存储和厂商回收站；
     * URI 不可用时才根据文件路径处理。目标已经不存在视为成功。
     */
    suspend fun delete(resource: DownloadResourceEntity): Boolean = withContext(Dispatchers.IO) {
        val file = File(resource.file)
        val storedUri = resource.contentUri
            ?.let(Uri::parse)
            ?.takeIf { it.scheme == "content" }

        if (storedUri != null && deleteMediaStoreUri(storedUri, file)) {
            return@withContext true
        }
        if (!file.exists()) return@withContext true
        deleteFileInternal(file, resource.mimeType)
    }

    /** 删除没有对应数据库资源记录的文件，并在必要时解析其 MediaStore URI。 */
    suspend fun deleteFile(file: File, mimeType: String? = null): Boolean =
        withContext(Dispatchers.IO) { deleteFileInternal(file, mimeType) }

    /**
     * 在 Android 10 以上通过 MediaStore 发布资源。
     *
     * 新条目先以 `IS_PENDING=1` 创建并保存 URI 检查点；内容写入且大小核验通过后才解除
     * pending。最终记录保存成功之前绝不删除源文件。
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    private suspend fun publishWithMediaStore(
        resource: DownloadResourceEntity,
        source: File,
        target: PublicTarget,
        persistCheckpoint: suspend (DownloadResourceEntity) -> Unit
    ): DownloadResourceEntity {
        val context = CommonLibs.requireContext()
        val resolver = context.contentResolver
        val displayName = findAvailableDisplayName(source.name, target.directory)
        val storedUri = parseAccessibleContentUri(resource.contentUri)
        val uri = storedUri ?: run {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, resource.mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, target.relativePath)
                put(MediaStore.MediaColumns.DATE_ADDED, resource.creationTime / 1000L)
                put(MediaStore.MediaColumns.DATE_MODIFIED, System.currentTimeMillis() / 1000L)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            resolver.insert(resolveMediaStoreCollection(target.mediaCollection), values)
                ?: throw IOException("Unable to create MediaStore resource for $displayName")
        }

        if (storedUri == null) {
            try {
                persistCheckpoint(resource.copy(contentUri = uri.toString()))
            } catch (throwable: Throwable) {
                runCatching { resolver.delete(uri, null, null) }
                throw throwable
            }
        }

        return try {
            var entry = queryMediaStoreEntry(uri)
            if (entry?.isPending != false || entry.size != source.length()) {
                resolver.openOutputStream(uri, "w")?.use { output ->
                    source.inputStream().use { input ->
                        input.copyTo(output, COPY_BUFFER_SIZE)
                    }
                } ?: throw IOException("Unable to open MediaStore output stream: $uri")

                val storedSize = queryMediaStoreEntry(uri)?.size ?: -1L
                if (storedSize >= 0L && storedSize != source.length()) {
                    throw IOException(
                        "Published resource size mismatch: expected=${source.length()}, actual=$storedSize"
                    )
                }

                val pendingValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                    put(MediaStore.MediaColumns.DATE_MODIFIED, System.currentTimeMillis() / 1000L)
                }
                if (resolver.update(uri, pendingValues, null, null) <= 0) {
                    throw IOException("Unable to finish MediaStore resource: $uri")
                }
                entry = queryMediaStoreEntry(uri)
            }

            val storedSize = entry?.size ?: source.length()
            val publishedPath = entry?.path
                ?: File(target.directory, entry?.displayName ?: displayName).absolutePath
            val published = resource.copy(
                file = publishedPath,
                contentUri = uri.toString(),
                storageSizeBytes = storedSize.takeIf { it >= 0L } ?: resource.storageSizeBytes
            )
            persistCheckpoint(published)
            if (!source.delete()) {
                Log.w(TAG, "Unable to remove published working resource: ${source.absolutePath}")
            }
            published
        } catch (throwable: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            throw throwable
        }
    }

    /**
     * 在 Android 9 及以下复制到传统公共目录并触发媒体扫描。
     *
     * 复制大小和数据库检查点均确认成功后才删除源文件。
     */
    private suspend fun publishLegacy(
        resource: DownloadResourceEntity,
        source: File,
        target: PublicTarget,
        persistCheckpoint: suspend (DownloadResourceEntity) -> Unit
    ): DownloadResourceEntity {
        val outputFile = File(
            target.directory.apply {
                if (!isDirectory && !mkdirs() && !isDirectory) {
                    throw IOException("Unable to create public directory: $this")
                }
            },
            findAvailableDisplayName(source.name, target.directory)
        )
        source.copyTo(outputFile, overwrite = false)
        if (outputFile.length() != source.length()) {
            outputFile.delete()
            throw IOException("Published resource size mismatch: ${outputFile.absolutePath}")
        }
        val uri = scanFile(outputFile, resource.mimeType)
        val published = resource.copy(
            file = outputFile.absolutePath,
            contentUri = uri?.toString(),
            storageSizeBytes = outputFile.length()
        )
        try {
            persistCheckpoint(published)
        } catch (throwable: Throwable) {
            outputFile.delete()
            throw throwable
        }
        if (!source.delete()) {
            Log.w(TAG, "Unable to remove published working resource: ${source.absolutePath}")
        }
        return published
    }

    /** 根据系统版本和文件位置选择直接删除或 MediaStore 删除。 */
    private suspend fun deleteFileInternal(file: File, mimeType: String?): Boolean {
        if (!file.exists()) return true
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || !isPublicResourceFile(file)) {
            return file.delete()
        }

        // 旧版本只保存原始路径，需先查询或创建对应的 MediaStore 记录；Android 10 及以上
        // 不应回退到公共文件的 File.delete()。
        val uri = findMediaStoreUri(file) ?: scanFile(file, mimeType)
        if (uri == null) {
            Log.e(TAG, "Unable to resolve MediaStore URI for ${file.absolutePath}")
            return false
        }
        return deleteMediaStoreUri(uri, file)
    }

    /** 先释放文件内容，再删除 MediaStore 条目，规避部分系统的隐藏回收站占用空间。 */
    private fun deleteMediaStoreUri(uri: Uri, file: File): Boolean {
        val resolver = CommonLibs.requireContext().contentResolver
        if (!truncateMediaStoreFile(uri, file)) return false

        return try {
            resolver.delete(uri, null, null)
            // 部分厂商的 Provider 仍会在原始路径留下零字节条目。
            if (file.exists()) file.delete()
            !file.exists()
        } catch (e: Exception) {
            Log.e(TAG, "Unable to delete MediaStore resource: $uri", e)
            // 文件内容已被截断。即使系统将剩余空目录项移入回收站，也不会继续占用原始空间。
            if (file.exists()) file.delete()
            !file.exists()
        }
    }

    /**
     * ColorOS 可能将 ContentResolver.delete() 同样实现为移动至 DCIM/.mediaTrash。
     * 因此先截断应用拥有的文件，确保厂商保留条目前已释放其数据块。
     */
    private fun truncateMediaStoreFile(uri: Uri, file: File): Boolean {
        return try {
            val descriptor = CommonLibs.requireContext().contentResolver
                .openFileDescriptor(uri, "rw") ?: return false
            descriptor.use { Os.ftruncate(it.fileDescriptor, 0L) }
            !file.exists() || file.length() == 0L
        } catch (e: Exception) {
            Log.e(TAG, "Unable to truncate MediaStore resource: $uri", e)
            false
        }
    }

    /**
     * 根据绝对路径或“相对目录 + 文件名”查找已有 MediaStore 条目。
     *
     * 同时查询 Downloads 与 Video 集合，以兼容旧版本和存储模式切换产生的记录。
     */
    private fun findMediaStoreUri(file: File): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val resolver = CommonLibs.requireContext().contentResolver
        val projection = arrayOf(MediaStore.MediaColumns._ID)

        fun query(collection: Uri, selection: String, args: Array<String>): Uri? = try {
            resolver.query(collection, projection, selection, args, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                ContentUris.withAppendedId(collection, cursor.getLong(0))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to query MediaStore for ${file.absolutePath}", e)
            null
        }

        val isVideoDirectory = isFileInside(file, CommonLibs.getPublicVideoResourcesDir())
        val relativePath = if (isVideoDirectory) relativeVideoPath else relativeDownloadPath
        val collections = if (isVideoDirectory) {
            listOf(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                MediaStore.Downloads.EXTERNAL_CONTENT_URI
            )
        } else {
            listOf(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            )
        }

        collections.forEach { collection ->
            query(
                collection,
                "${MediaStore.MediaColumns.DATA} = ?",
                arrayOf(file.absolutePath)
            )?.let { return it }
            query(
                collection,
                "${MediaStore.MediaColumns.RELATIVE_PATH} = ? AND " +
                    "${MediaStore.MediaColumns.DISPLAY_NAME} = ?",
                arrayOf(relativePath, file.name)
            )?.let { return it }
        }
        return null
    }

    /** 恢复发布流程所需的最小 MediaStore 元数据。 */
    private data class MediaStoreEntry(
        val path: String?,
        val displayName: String?,
        val size: Long,
        val isPending: Boolean
    )

    /** 查询指定 URI 的路径、文件名、大小和 pending 状态。 */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun queryMediaStoreEntry(uri: Uri): MediaStoreEntry? {
        return try {
            CommonLibs.requireContext().contentResolver.query(
                uri,
                arrayOf(
                    MediaStore.MediaColumns.DATA,
                    MediaStore.MediaColumns.DISPLAY_NAME,
                    MediaStore.MediaColumns.SIZE,
                    MediaStore.MediaColumns.IS_PENDING
                ),
                null,
                null,
                null
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                MediaStoreEntry(
                    path = cursor.getString(0),
                    displayName = cursor.getString(1),
                    size = if (cursor.isNull(2)) -1L else cursor.getLong(2),
                    isPending = cursor.getInt(3) != 0
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to read MediaStore resource $uri", e)
            null
        }
    }

    /** 解析并实际打开 URI；已失效或无权限的 URI 返回 `null`。 */
    private fun parseAccessibleContentUri(value: String?): Uri? {
        val uri = value?.let(Uri::parse)?.takeIf { it.scheme == "content" } ?: return null
        return try {
            CommonLibs.requireContext().contentResolver.openAssetFileDescriptor(uri, "r")
                ?.use { uri }
        } catch (e: Exception) {
            Log.w(TAG, "Stored content URI is no longer accessible: $uri", e)
            null
        }
    }

    /**
     * 当私有源文件缺失时，尝试从已保存的公共 URI 恢复完整资源记录。
     *
     * pending 条目不能视为发布完成，因为其中的数据可能尚未写完。
     */
    private suspend fun resolveStoredResource(
        resource: DownloadResourceEntity
    ): DownloadResourceEntity? {
        val uri = parseAccessibleContentUri(resource.contentUri) ?: return null
        val entry = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            queryMediaStoreEntry(uri)
        } else {
            null
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && (entry == null || entry.isPending)) {
            return null
        }
        return resource.copy(
            file = entry?.path ?: resource.file,
            contentUri = uri.toString(),
            storageSizeBytes = entry?.size?.takeIf { it >= 0L } ?: resource.storageSizeBytes
        )
    }

    /** 请求系统扫描传统公共文件，并返回生成的媒体库 URI。 */
    private suspend fun scanFile(file: File, mimeType: String?): Uri? =
        suspendCancellableCoroutine { continuation ->
            try {
                MediaScannerConnection.scanFile(
                    CommonLibs.requireContext(),
                    arrayOf(file.absolutePath),
                    arrayOf(mimeType)
                ) { _, uri ->
                    if (continuation.isActive) continuation.resume(uri)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Unable to scan ${file.absolutePath}", e)
                if (continuation.isActive) continuation.resume(null)
            }
        }

    private fun isPublicResourceFile(file: File): Boolean {
        return isFileInside(file, CommonLibs.getPublicResourcesDir()) ||
            isFileInside(file, CommonLibs.getPublicVideoResourcesDir())
    }

    private fun isFileInside(file: File, directory: File): Boolean {
        return try {
            val directoryPath = directory.canonicalPath + File.separator
            file.canonicalPath.startsWith(directoryPath)
        } catch (_: IOException) {
            val directoryPath = directory.absolutePath + File.separator
            file.absolutePath.startsWith(directoryPath)
        }
    }

    /** 生成不会覆盖同目录现有文件的显示名称。 */
    private fun findAvailableDisplayName(originalName: String, directory: File): String {
        if (!File(directory, originalName).exists()) return originalName

        val dotIndex = originalName.lastIndexOf('.')
        val baseName = if (dotIndex > 0) originalName.substring(0, dotIndex) else originalName
        val extension = if (dotIndex > 0) originalName.substring(dotIndex) else ""
        var index = 1
        while (File(directory, "$baseName($index)$extension").exists()) index++
        return "$baseName($index)$extension"
    }

    /**
     * 根据存储模式和 MIME 类型选择公共目录及 MediaStore 集合。
     *
     * `EXTERNAL_MEDIA` 只把视频放入 Movies，其余模式和资源仍使用 Downloads。
     */
    private fun resolvePublicTarget(resource: DownloadResourceEntity): PublicTarget {
        val isVideo = resource.mimeType.startsWith("video/", ignoreCase = true)
        return if (AppModel.downloadPathMode == DownloadPathMode.EXTERNAL_MEDIA && isVideo) {
            PublicTarget(
                directory = CommonLibs.getPublicVideoResourcesDir(),
                relativePath = relativeVideoPath,
                mediaCollection = MediaCollection.VIDEO
            )
        } else {
            PublicTarget(
                directory = CommonLibs.getPublicResourcesDir(),
                relativePath = relativeDownloadPath,
                mediaCollection = MediaCollection.DOWNLOADS
            )
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun resolveMediaStoreCollection(collection: MediaCollection): Uri {
        return when (collection) {
            MediaCollection.DOWNLOADS -> MediaStore.Downloads.EXTERNAL_CONTENT_URI
            MediaCollection.VIDEO -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }
    }
}
