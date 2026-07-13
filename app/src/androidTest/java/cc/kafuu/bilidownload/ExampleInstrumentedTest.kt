package cc.kafuu.bilidownload

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cc.kafuu.bilidownload.common.CommonLibs
import cc.kafuu.bilidownload.common.constant.DownloadResourceType
import cc.kafuu.bilidownload.common.model.AppModel
import cc.kafuu.bilidownload.common.model.DownloadPathMode
import cc.kafuu.bilidownload.common.room.entity.DownloadResourceEntity
import cc.kafuu.bilidownload.common.storage.ResourceStorage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue

import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** 在 Android 设备上执行的集成测试。 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun useAppContext() {
        // 被测试应用的上下文。
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("cc.kafuu.bilidownload", appContext.packageName)
    }

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

            val published = ResourceStorage.publishIfNeeded(resource)
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

    @Test
    fun databaseMigrationAddsMediaStoreUriColumn() {
        val database = CommonLibs.requireAppDatabase().openHelper.writableDatabase
        database.query("PRAGMA table_info(DownloadResource)").use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            var found = false
            while (cursor.moveToNext()) {
                if (cursor.getString(nameIndex) == "contentUri") {
                    found = true
                    break
                }
            }
            assertTrue(found)
        }
    }
}
