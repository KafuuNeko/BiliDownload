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

    private data class PublicTarget(
        val directory: File,
        val relativePath: String,
        val mediaCollection: MediaCollection
    )

    private enum class MediaCollection { DOWNLOADS, VIDEO }

    suspend fun publishIfNeeded(resource: DownloadResourceEntity): DownloadResourceEntity =
        withContext(Dispatchers.IO) {
            if (AppModel.downloadPathMode == DownloadPathMode.INTERNAL) {
                return@withContext resource
            }

            val source = File(resource.file)
            if (!source.isFile) {
                throw IOException("Resource file does not exist: ${source.absolutePath}")
            }

            if (isPublicResourceFile(source)) {
                if (resource.contentUri != null) return@withContext resource
                val uri = findMediaStoreUri(source) ?: scanFile(source, resource.mimeType)
                return@withContext resource.copy(contentUri = uri?.toString())
            }

            val target = resolvePublicTarget(resource)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                publishWithMediaStore(resource, source, target)
            } else {
                publishLegacy(resource, source, target)
            }
        }

    suspend fun delete(resource: DownloadResourceEntity): Boolean = withContext(Dispatchers.IO) {
        val file = File(resource.file)
        if (!file.exists()) return@withContext true

        val storedUri = resource.contentUri
            ?.let(Uri::parse)
            ?.takeIf { it.scheme == "content" }

        if (storedUri != null && deleteMediaStoreUri(storedUri, file)) {
            return@withContext true
        }
        deleteFileInternal(file, resource.mimeType)
    }

    suspend fun deleteFile(file: File, mimeType: String? = null): Boolean =
        withContext(Dispatchers.IO) { deleteFileInternal(file, mimeType) }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun publishWithMediaStore(
        resource: DownloadResourceEntity,
        source: File,
        target: PublicTarget
    ): DownloadResourceEntity {
        val context = CommonLibs.requireContext()
        val resolver = context.contentResolver
        val displayName = findAvailableDisplayName(source.name, target.directory)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, resource.mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, target.relativePath)
            put(MediaStore.MediaColumns.DATE_ADDED, resource.creationTime / 1000L)
            put(MediaStore.MediaColumns.DATE_MODIFIED, System.currentTimeMillis() / 1000L)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(resolveMediaStoreCollection(target.mediaCollection), values)
            ?: throw IOException("Unable to create MediaStore resource for $displayName")

        return try {
            resolver.openOutputStream(uri, "w")?.use { output ->
                source.inputStream().use { input ->
                    input.copyTo(output, COPY_BUFFER_SIZE)
                }
            } ?: throw IOException("Unable to open MediaStore output stream: $uri")

            val storedSize = resolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
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

            val publishedPath = queryDataPath(uri)
                ?: File(target.directory, displayName).absolutePath
            if (!source.delete()) {
                throw IOException("Unable to remove working resource: ${source.absolutePath}")
            }

            resource.copy(
                file = publishedPath,
                contentUri = uri.toString(),
                storageSizeBytes = storedSize.takeIf { it >= 0L } ?: resource.storageSizeBytes
            )
        } catch (throwable: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            throw throwable
        }
    }

    private suspend fun publishLegacy(
        resource: DownloadResourceEntity,
        source: File,
        target: PublicTarget
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
        if (!source.delete()) {
            outputFile.delete()
            throw IOException("Unable to remove working resource: ${source.absolutePath}")
        }
        return resource.copy(
            file = outputFile.absolutePath,
            contentUri = uri?.toString(),
            storageSizeBytes = outputFile.length()
        )
    }

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

    private fun queryDataPath(uri: Uri): String? {
        return try {
            CommonLibs.requireContext().contentResolver.query(
                uri,
                arrayOf(MediaStore.MediaColumns.DATA),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to resolve filesystem path for $uri", e)
            null
        }
    }

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

    private fun findAvailableDisplayName(originalName: String, directory: File): String {
        if (!File(directory, originalName).exists()) return originalName

        val dotIndex = originalName.lastIndexOf('.')
        val baseName = if (dotIndex > 0) originalName.substring(0, dotIndex) else originalName
        val extension = if (dotIndex > 0) originalName.substring(dotIndex) else ""
        var index = 1
        while (File(directory, "$baseName($index)$extension").exists()) index++
        return "$baseName($index)$extension"
    }

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
