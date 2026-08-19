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
        data class Completed(val successCount: Int, val total: Int) : Result
    }

    private data class ExportItem(
        val fileName: String,
        val mimeType: String,
        val sourceFile: File,
    )

    suspend fun execute(
        treeUri: Uri,
        sources: List<Source>,
        onProgress: (Progress) -> Unit,
    ): Result = withContext(Dispatchers.IO) {
        val context = contextProvider()
        val targetDirectory = DocumentFile.fromTreeUri(context, treeUri)
            ?: return@withContext Result.InvalidDestination
        val exportItems = buildExportItems(sources)
        if (exportItems.isEmpty()) return@withContext Result.NoExportableResources

        onProgress(Progress(0, exportItems.size))
        var successCount = 0
        exportItems.forEachIndexed { index, item ->
            onProgress(Progress(index + 1, exportItems.size))
            val exported = runCatching {
                val fileName = FileUtils.resolveUniqueDocumentName(
                    targetDirectory,
                    item.fileName,
                )
                val target = targetDirectory.createFile(item.mimeType, fileName)
                    ?: return@runCatching false
                FileUtils.writeFileToUri(context, target.uri, item.sourceFile)
            }.getOrDefault(false)
            if (exported) successCount++
        }
        Result.Completed(successCount, exportItems.size)
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
