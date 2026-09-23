package cc.kafuu.bilidownload.common.download

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import cc.kafuu.bilidownload.common.CommonLibs
import cc.kafuu.bilidownload.common.constant.DownloadResourceType
import cc.kafuu.bilidownload.common.model.AppModel
import cc.kafuu.bilidownload.common.room.entity.DownloadResourceEntity
import cc.kafuu.bilidownload.common.room.repository.DownloadRepository
import cc.kafuu.bilidownload.common.utils.DownloadFileNameUtils
import cc.kafuu.bilidownload.common.utils.ExportFileNameUtils
import cc.kafuu.bilidownload.common.utils.FileUtils
import cc.kafuu.bilidownload.common.utils.MimeTypeUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** 将选中任务的可读资源复制到用户授权目录，兼容文件路径和已发布的 content URI。 */
class BatchExportUseCase(
    private val contextProvider: () -> Context = CommonLibs::requireContext,
    private val queryResources: suspend (Long) -> List<DownloadResourceEntity> =
        DownloadRepository::queryResourcesForExport,
    private val templatesProvider: () -> ExportFileNameUtils.Templates = AppModel::getExportFileNameTemplates,
    private val directoryProvider: (Context, Uri) -> DocumentFile? = DocumentFile::fromTreeUri,
) {
    /** 开始选择目录前固定的导出目标与文件名参数。 */
    data class Source(val taskId: Long, val fileNameContext: DownloadFileNameUtils.TemplateContext)

    /** 已处理的任务数，包括成功、源缺失和复制失败的条目。 */
    data class Progress(val current: Int, val total: Int)

    /** 导出结果中的总数以用户提交的去重任务快照为准，缺失源计入跳过数。 */
    sealed interface Result {
        data object NoExportableResources : Result
        data object InvalidDestination : Result
        data class Completed(val successCount: Int, val total: Int, val skippedCount: Int = 0) : Result {
            val failedCount: Int get() = total - successCount - skippedCount
        }
    }

    private data class ExportItem(val fileName: String, val mimeType: String, val sourceUri: Uri)

    private enum class ExportOutcome { SUCCESS, SKIPPED, FAILED }

    /**
     * 在 IO 线程逐项导出；失败或取消时清理本次未完成的目标文件，不删除原资源。
     * [onProgress] 可挂起，调用方可切回主线程发布状态，避免完成后收到迟到进度。
     */
    suspend fun execute(
        treeUri: Uri,
        sources: List<Source>,
        onProgress: suspend (Progress) -> Unit,
    ): Result = withContext(Dispatchers.IO) {
        val context = contextProvider()
        val directory = try {
            directoryProvider(context, treeUri)?.takeIf { it.isDirectory && it.canWrite() }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        } ?: return@withContext Result.InvalidDestination
        val targets = sources.distinctBy { it.taskId }
        val templates = templatesProvider()
        var successCount = 0
        var skippedCount = 0
        onProgress(Progress(0, targets.size))

        // 每项在处理时重新校验可读来源；其他任务的删除或发布不会使整批提前退出。
        targets.forEachIndexed { index, source ->
            currentCoroutineContext().ensureActive()
            when (exportSource(context, directory, source, templates)) {
                ExportOutcome.SUCCESS -> successCount++
                ExportOutcome.SKIPPED -> skippedCount++
                ExportOutcome.FAILED -> Unit
            }
            onProgress(Progress(index + 1, targets.size))
        }
        if (skippedCount == targets.size) Result.NoExportableResources
        else Result.Completed(successCount, targets.size, skippedCount)
    }

    /** 单项查询或复制失败记入失败数，后续任务仍可继续，取消则向上传播。 */
    private suspend fun exportSource(
        context: Context,
        directory: DocumentFile,
        source: Source,
        templates: ExportFileNameUtils.Templates,
    ): ExportOutcome = try {
        val item = buildExportItem(context, source, templates)
        when {
            item == null -> ExportOutcome.SKIPPED
            copyItem(context, directory, item) -> ExportOutcome.SUCCESS
            else -> ExportOutcome.FAILED
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        ExportOutcome.FAILED
    }

    /** 优先导出合成资源；首选源不可读时继续检查视频、音频等仍有效的候选。 */
    private suspend fun buildExportItem(
        context: Context,
        source: Source,
        templates: ExportFileNameUtils.Templates,
    ): ExportItem? {
        val resources = queryResources(source.taskId).sortedBy { resource ->
            when (resource.type) {
                DownloadResourceType.MIXED -> 0
                DownloadResourceType.VIDEO -> 1
                else -> 2
            }
        }
        // 路径失效不代表公共资源丢失，读取入口同时检查持久化的 MediaStore URI。
        for (resource in resources) {
            val file = File(resource.file)
            val uri = FileUtils.resolveReadUri(context, file, resource.contentUri) ?: continue
            val extension = file.extension.ifBlank {
                MimeTypeUtils.getExtensionFromMimeType(resource.mimeType).orEmpty()
            }
            return ExportItem(
                ExportFileNameUtils.buildFileName(resource.type, source.fileNameContext, extension, templates),
                resource.mimeType, uri,
            )
        }
        return null
    }

    /** 输出句柄关闭成功后才计为完成；失败与取消只清理本次新建的副本。 */
    private suspend fun copyItem(context: Context, directory: DocumentFile, item: ExportItem): Boolean {
        var target: DocumentFile? = null
        var completed = false
        try {
            val name = FileUtils.resolveUniqueDocumentName(directory, item.fileName)
            target = directory.createFile(item.mimeType, name) ?: return false
            val resolver = context.contentResolver
            resolver.openInputStream(item.sourceUri)?.use { input ->
                val output = resolver.openOutputStream(target.uri, "w") ?: throw IOException("无法打开目标")
                output.use {
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        it.write(buffer, 0, count)
                    }
                }
            } ?: throw IOException("无法打开源资源")
            completed = true
            return true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return false
        } finally {
            // 清理仅作用于本次创建的目标，不触碰已有同名文件和源资源。
            if (!completed) runCatching { target?.delete() }
        }
    }
}
