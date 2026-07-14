package cc.kafuu.bilidownload

import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cc.kafuu.bilidownload.common.CommonLibs
import cc.kafuu.bilidownload.common.constant.DownloadResourceType
import cc.kafuu.bilidownload.common.model.AppModel
import cc.kafuu.bilidownload.common.model.DownloadPathMode
import cc.kafuu.bilidownload.common.room.entity.DownloadResourceEntity
import cc.kafuu.bilidownload.common.storage.ResourceStorage
import cc.kafuu.bilidownload.common.utils.FileUtils
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue

import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException

/** 在 Android 设备上执行的集成测试。 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun useAppContext() {
        // 被测试应用的上下文。
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("cc.kafuu.bilidownload", appContext.packageName)
    }

    /** 验证删除公共资源后文件内容确实释放，而不是仅进入厂商隐藏回收站。 */
    @Test
    fun publicResourceDeletionReleasesFilePayload() = runBlocking {
        val originalMode = AppModel.downloadPathMode
        val workingFile = File(
            CommonLibs.requireResourceWorkingDir(),
            "storage-test-${System.currentTimeMillis()}.mp4"
        )
        var publishedResource: DownloadResourceEntity? = null

        try {
            AppModel.downloadPathMode = DownloadPathMode.EXTERNAL
            workingFile.outputStream().use { output ->
                val block = ByteArray(1024 * 1024) { 0x5A.toByte() }
                repeat(8) { output.write(block) }
            }
            val resource = DownloadResourceEntity(
                taskId = Long.MAX_VALUE,
                type = DownloadResourceType.MIXED,
                name = "MediaStore test",
                mimeType = "video/mp4",
                storageSizeBytes = workingFile.length(),
                creationTime = System.currentTimeMillis(),
                file = workingFile.absolutePath
            )

            val published = ResourceStorage.publishIfNeeded(resource) { }
            publishedResource = published
            assertNotNull(published.contentUri)
            assertFalse(workingFile.exists())
            assertTrue(File(published.file).isFile)

            assertTrue(ResourceStorage.delete(published))
            assertFalse(File(published.file).exists())
        } finally {
            publishedResource?.let { ResourceStorage.delete(it) }
            workingFile.delete()
            AppModel.downloadPathMode = originalMode
        }
    }

    /** 验证媒体模式下的视频进入 Movies/BVD 和 Video MediaStore 集合。 */
    @Test
    fun externalMediaVideoIsPublishedToMoviesVideoCollection() = runBlocking {
        val originalMode = AppModel.downloadPathMode
        val workingFile = File(
            CommonLibs.requireResourceWorkingDir(),
            "media-storage-test-${System.currentTimeMillis()}.mp4"
        )
        var publishedResource: DownloadResourceEntity? = null

        try {
            AppModel.downloadPathMode = DownloadPathMode.EXTERNAL_MEDIA
            workingFile.writeBytes(ByteArray(1024) { 0x5A.toByte() })
            val resource = DownloadResourceEntity(
                taskId = Long.MAX_VALUE,
                type = DownloadResourceType.MIXED,
                name = "Video collection test",
                mimeType = "video/mp4",
                storageSizeBytes = workingFile.length(),
                creationTime = System.currentTimeMillis(),
                file = workingFile.absolutePath
            )

            val published = ResourceStorage.publishIfNeeded(resource) { }
            publishedResource = published

            assertEquals(
                CommonLibs.getPublicVideoResourcesDir().canonicalPath,
                File(published.file).parentFile?.canonicalPath
            )
            assertNotNull(published.contentUri)
            assertNotNull(
                FileUtils.resolveReadUri(
                    CommonLibs.requireContext(),
                    File(published.file),
                    contentUri = null
                )
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val uri = Uri.parse(published.contentUri)
                assertEquals(MediaStore.Video.Media.EXTERNAL_CONTENT_URI.authority, uri.authority)
                assertTrue(uri.path.orEmpty().startsWith("/external/video/media/"))
            }

            assertTrue(ResourceStorage.delete(published))
            assertFalse(File(published.file).exists())
        } finally {
            publishedResource?.let { ResourceStorage.delete(it) }
            workingFile.delete()
            AppModel.downloadPathMode = originalMode
        }
    }

    /** 验证数据库检查点失败会保留源文件，并允许后续发布重试。 */
    @Test
    fun publishingCheckpointFailureKeepsSourceAndCanRetry() = runBlocking {
        val originalMode = AppModel.downloadPathMode
        val workingFile = File(
            CommonLibs.requireResourceWorkingDir(),
            "checkpoint-test-${System.currentTimeMillis()}.mp4"
        )
        var publishedResource: DownloadResourceEntity? = null

        try {
            AppModel.downloadPathMode = DownloadPathMode.EXTERNAL
            workingFile.writeBytes(ByteArray(4096) { 0x2A.toByte() })
            val resource = DownloadResourceEntity(
                taskId = Long.MAX_VALUE,
                type = DownloadResourceType.MIXED,
                name = "Checkpoint failure test",
                mimeType = "video/mp4",
                storageSizeBytes = workingFile.length(),
                creationTime = System.currentTimeMillis(),
                file = workingFile.absolutePath
            )

            var checkpointFailed = false
            try {
                ResourceStorage.publishIfNeeded(resource) {
                    throw IOException("Injected Room checkpoint failure")
                }
            } catch (_: IOException) {
                checkpointFailed = true
            }

            assertTrue(checkpointFailed)
            assertTrue(workingFile.isFile)
            val published = ResourceStorage.publishIfNeeded(resource) { }
            publishedResource = published
            assertFalse(workingFile.exists())
            assertTrue(File(published.file).isFile)
        } finally {
            publishedResource?.let { ResourceStorage.delete(it) }
            workingFile.delete()
            AppModel.downloadPathMode = originalMode
        }
    }

}
