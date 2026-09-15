package cc.kafuu.bilidownload.common.download

import cc.kafuu.bilidownload.common.room.entity.DownloadResourceEntity
import cc.kafuu.bilidownload.common.room.repository.DownloadRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** 单个和批量导出共用的清理策略：复制完成并核验成功后，才允许删除该资源及其记录。 */
class ResourceExportUseCase(
    private val deleteResource: suspend (DownloadResourceEntity) -> Boolean =
        DownloadRepository::deleteResource,
) {
    enum class Result {
        EXPORT_FAILED,
        EXPORTED,
        SOURCE_DELETED,
        SOURCE_DELETE_FAILED,
    }

    /**
     * [copyToDestination] 必须在目标文件关闭且核验成功后返回 true。
     * 删除失败不撤销导出副本，也不能被报告成导出失败，避免用户误以为目标未保存。
     */
    suspend fun execute(
        resource: DownloadResourceEntity,
        deleteSourceAfterExport: Boolean = false,
        copyToDestination: suspend () -> Boolean,
    ): Result = withContext(Dispatchers.IO) {
        currentCoroutineContext().ensureActive()
        val exported = try {
            copyToDestination()
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            false
        }
        if (!exported) return@withContext Result.EXPORT_FAILED
        if (!deleteSourceAfterExport) return@withContext Result.EXPORTED

        currentCoroutineContext().ensureActive()
        val deleted = try {
            deleteResource(resource)
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            false
        }
        if (deleted) Result.SOURCE_DELETED else Result.SOURCE_DELETE_FAILED
    }
}
