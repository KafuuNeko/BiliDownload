package cc.kafuu.bilidownload.common.download

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import cc.kafuu.bilidownload.common.CommonLibs
import cc.kafuu.bilidownload.common.constant.DownloadResourceType
import cc.kafuu.bilidownload.common.room.entity.DownloadResourceEntity
import cc.kafuu.bilidownload.common.room.repository.DownloadRepository
import cc.kafuu.bilidownload.common.utils.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** 批量导出下载资源的应用用例，不依赖具体页面和对话框。 */
class BatchExportUseCase(
    private val contextProvider: () -> Context = CommonLibs::requireContext,
    private val queryResources: suspend (Long) -> List<DownloadResourceEntity> =
        DownloadRepository::queryResourcesForExport,
    private val resourceExportUseCase: ResourceExportUseCase = ResourceExportUseCase(),
) {
    data class Source(
        val taskId: Long,
        val displayName: String,
    )

    data class Progress(
        val current: Int,
        val total: Int,
    )

    sealed interface Result {
        data object NoExportableResources : Result
        data object InvalidDestination : Result
        data class Completed(
            val successCount: Int,
            val total: Int,
            val deletedSourceCount: Int,
            val sourceDeleteFailureCount: Int,
        ) : Result
    }

    private data class ExportItem(
        val fileName: String,
        val mimeType: String,
        val sourceFile: File,
        val resource: DownloadResourceEntity,
    )

    suspend fun execute(
        treeUri: Uri,
        sources: List<Source>,
        deleteSourceAfterExport: Boolean = false,
        onProgress: (Progress) -> Unit,
    ): Result = withContext(Dispatchers.IO) {
        val context = contextProvider()
        val targetDirectory = DocumentFile.fromTreeUri(context, treeUri)
            ?: return@withContext Result.InvalidDestination
        val exportItems = buildExportItems(sources)
        if (exportItems.isEmpty()) return@withContext Result.NoExportableResources

        onProgress(Progress(0, exportItems.size))
        var successCount = 0
        var deletedSourceCount = 0
        var sourceDeleteFailureCount = 0
        exportItems.forEachIndexed { index, item ->
            onProgress(Progress(index + 1, exportItems.size))
            val result = resourceExportUseCase.execute(item.resource, deleteSourceAfterExport) copy@{
                val fileName = FileUtils.resolveUniqueDocumentName(
                    targetDirectory,
                    item.fileName,
                )
                val target = targetDirectory.createFile(item.mimeType, fileName)
                    ?: return@copy false
                val copied = FileUtils.writeFileToUri(
                    context, target.uri, item.sourceFile,
                    verifyContents = deleteSourceAfterExport,
                )
                // 仅清理由本次批量导出创建的不完整目标，源文件始终留给用户重试。
                if (!copied) runCatching { target.delete() }
                copied
            }
            if (result != ResourceExportUseCase.Result.EXPORT_FAILED) successCount++
            when (result) {
                ResourceExportUseCase.Result.SOURCE_DELETED -> deletedSourceCount++
                ResourceExportUseCase.Result.SOURCE_DELETE_FAILED -> sourceDeleteFailureCount++
                else -> Unit
            }
        }
        Result.Completed(successCount, exportItems.size, deletedSourceCount, sourceDeleteFailureCount)
    }

    private suspend fun buildExportItems(sources: List<Source>): List<ExportItem> = buildList {
        sources.forEach { source ->
            val resource = pickBestResource(queryResources(source.taskId)) ?: return@forEach
            val sourceFile = File(resource.file).takeIf(File::isFile) ?: return@forEach
            val extension = sourceFile.extension.takeIf(String::isNotEmpty)?.let { ".$it" }.orEmpty()
            add(
                ExportItem(
                    fileName = "${source.displayName}$extension",
                    mimeType = resource.mimeType,
                    sourceFile = sourceFile,
                    resource = resource,
                )
            )
        }
    }

    private fun pickBestResource(
        resources: List<DownloadResourceEntity>
    ): DownloadResourceEntity? = resources.find { it.type == DownloadResourceType.MIXED }
        ?: resources.find { it.type == DownloadResourceType.VIDEO }
        ?: resources.firstOrNull()
}
