package cc.kafuu.bilidownload

import cc.kafuu.bilidownload.common.constant.DownloadResourceType
import cc.kafuu.bilidownload.common.download.ResourceExportUseCase
import cc.kafuu.bilidownload.common.room.entity.DownloadResourceEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/** 验证导出和清理的结果边界，失败或取消不得删除仍需重试的源资源。 */
class ResourceExportUseCaseTest {
    private val resource = DownloadResourceEntity(
        taskId = 1, type = DownloadResourceType.MIXED, name = "source", mimeType = "video/mp4",
        storageSizeBytes = 5, creationTime = 0, file = "source.mp4",
    )

    /** 普通导出即使复制成功，也不进入源资源清理。 */
    @Test fun export_preservesSourceByDefault() = runBlocking {
        val useCase = ResourceExportUseCase { error("Source must be retained") }
        assertEquals(ResourceExportUseCase.Result.EXPORTED, useCase.execute(resource) { true })
    }

    /** 复制失败和写入异常都保留源资源，并返回导出失败。 */
    @Test fun move_preservesSourceWhenCopyFails() = runBlocking {
        val useCase = ResourceExportUseCase { error("Source must be retained") }
        assertEquals(ResourceExportUseCase.Result.EXPORT_FAILED, useCase.execute(resource, true) { false })
        assertEquals(ResourceExportUseCase.Result.EXPORT_FAILED,
            useCase.execute(resource, true) { throw IOException("Destination unavailable") })
    }

    /** 只在副本完整可用后删除同一资源记录。 */
    @Test fun move_deletesSourceAfterCopyCompletes() = runBlocking {
        var copied = false
        var deleted: DownloadResourceEntity? = null
        val useCase = ResourceExportUseCase {
            assertTrue(copied)
            deleted = it
            true
        }
        assertEquals(ResourceExportUseCase.Result.SOURCE_DELETED,
            useCase.execute(resource, true) { copied = true; true })
        assertEquals(resource, deleted)
    }

    /** 清理失败不应把已保存的副本误报为导出失败。 */
    @Test fun move_reportsCleanupFailureSeparately() = runBlocking {
        assertEquals(ResourceExportUseCase.Result.SOURCE_DELETE_FAILED,
            ResourceExportUseCase { false }.execute(resource, true) { true })
        assertEquals(ResourceExportUseCase.Result.SOURCE_DELETE_FAILED,
            ResourceExportUseCase { throw IOException("Source unavailable") }.execute(resource, true) { true })
    }

    /** 用户取消复制时保留取消语义，不能继续删除源文件。 */
    @Test(expected = CancellationException::class)
    fun move_propagatesCopyCancellation(): Unit = runBlocking {
        ResourceExportUseCase { error("Source must be retained") }
            .execute(resource, true) { throw CancellationException("Cancelled") }
        Unit
    }
}
