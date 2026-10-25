package cc.kafuu.bilidownload.feature.viewbinding.viewmodel.activity

import android.net.Uri
import android.util.Log
import androidx.activity.result.ActivityResult
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import cc.kafuu.bilidownload.R
import cc.kafuu.bilidownload.common.CommonLibs
import cc.kafuu.bilidownload.common.core.viewbinding.CoreViewModel
import cc.kafuu.bilidownload.common.download.ResourceExportUseCase
import cc.kafuu.bilidownload.common.ext.liveData
import cc.kafuu.bilidownload.common.model.AppModel
import cc.kafuu.bilidownload.common.model.LoadingStatus
import cc.kafuu.bilidownload.common.model.TaskStatus
import cc.kafuu.bilidownload.common.model.LocalMediaDetail
import cc.kafuu.bilidownload.common.model.action.ViewAction
import cc.kafuu.bilidownload.common.model.action.popmessage.ToastMessageAction
import cc.kafuu.bilidownload.common.room.dto.DownloadTaskWithVideoDetails
import cc.kafuu.bilidownload.common.room.entity.DownloadResourceEntity
import cc.kafuu.bilidownload.common.room.repository.DownloadRepository
import cc.kafuu.bilidownload.common.utils.DownloadFileNameUtils
import cc.kafuu.bilidownload.common.utils.ExportFileNameUtils
import cc.kafuu.bilidownload.common.utils.FFMpegUtils
import cc.kafuu.bilidownload.common.utils.FileUtils
import cc.kafuu.bilidownload.common.storage.ResourcePublishResult
import cc.kafuu.bilidownload.feature.viewbinding.view.activity.LocalResourceActivity
import cc.kafuu.bilidownload.feature.viewbinding.view.dialog.ConfirmDialog
import cc.kafuu.bilidownload.feature.viewbinding.view.dialog.ConvertDialog
import cc.kafuu.bilidownload.feature.viewbinding.view.dialog.ExportOptionsDialog
import com.arthenica.ffmpegkit.FFmpegSession
import com.arthenica.ffmpegkit.SessionState
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 管理单个本地资源的详情加载、播放请求、导出快照及媒体转换状态。
 *
 * - 资源同时保留路径和内容 URI，不能只凭旧路径不存在就判定公共资源丢失。
 * - 元数据探测使用可替换的 viewModelScope 任务，阻止旧资源结果覆盖新页面数据。
 * - 导出选择与目标 URI 分两次返回，使用固定快照关联；实际复制和删除顺序交给用例维护。
 * - Activity 负责系统选择器与跳转，转换会话由本模型持有以支持用户请求停止。
 */
class LocalResourceVideModel : CoreViewModel() {
    companion object {
        private const val TAG = "LocalResourceVideModel"

        class OpenResourceAction(
            val title: String,
            val file: File,
            val mimetype: String,
            val contentUri: String?
        ) : ViewAction()

        /** 请求宿主打开目标选择器；路径与 URI 一起传递，实际来源仍须在复制前重新打开。 */
        class ExportResourceAction(
            val file: File,
            val name: String,
            val mimetype: String,
            val contentUri: String?,
        ) : ViewAction()

        /** 把移动成功提示和页面关闭合并为一次动作，避免连续 LiveData 事件被合并丢失。 */
        class ResourceMovedAction(val message: String) : ViewAction()

        class PlayResourceAction(
            val filePath: String,
            val title: String,
            val mimetype: String,
            val contentUri: String?
        ) : ViewAction()
    }

    // 此页面加载状态，loading状态将显示加载动画（默认开启）
    private val mLoadingStatusLiveData = MutableLiveData(LoadingStatus.loadingStatus())
    val loadingStatusLiveData = mLoadingStatusLiveData.liveData()

    // 此资源隶属的任务详情
    private val mTaskDetailLiveData = MutableLiveData<DownloadTaskWithVideoDetails>()
    val taskDetailLiveData = mTaskDetailLiveData.liveData()

    // 下载的资源实体
    private val mResourceLiveData = MutableLiveData<DownloadResourceEntity>()
    val resourceLiveData = mResourceLiveData.liveData()

    // null 表示当前资源尚无可用详情；加载/失败的区别由 loadingStatusLiveData 表达。
    private val mLocalMediaDetailLiveData = MutableLiveData<LocalMediaDetail?>()
    val localMediaDetailLiveData = mLocalMediaDetailLiveData.liveData()

    private val mResourceExportUseCase = ResourceExportUseCase()

    // 固定本次导出的资源和选择，避免系统文件选择器返回时使用已变化的页面数据。
    private data class PendingExport(
        val resource: DownloadResourceEntity,
        val title: String,
        val deleteSourceAfterExport: Boolean,
    )

    private var mPendingExport: PendingExport? = null

    // 是否正在导出
    private val mIsExportingLiveData = MutableLiveData(false)
    val isExportingLiveData = mIsExportingLiveData.liveData()

    // 是否正在转换
    private val mIsConvertingLiveData = MutableLiveData(false)
    val isConvertingLiveData = mIsConvertingLiveData.liveData()

    // 音视频转换进度
    private val mConvertProgressLiveData = MutableLiveData<String>()
    val convertProgressLiveData = mConvertProgressLiveData.liveData()

    // FFmpeg 会话用于请求取消，不代表由 ViewModel 创建或管理的一条独立线程。
    private var mConvertSession: FFmpegSession? = null

    // 新资源到达时取消旧任务，ViewModel 清理时由 viewModelScope 一并取消。
    private var mLoadResourceJob: Job? = null

    /** 从主线程接收最新资源记录，并立即重启与该记录绑定的元数据探测。 */
    fun updateResourceEntity(resource: DownloadResourceEntity) {
        mResourceLiveData.value = resource
        doLoadResourceDetails(resource = resource)
    }

    /** 从主线程更新所属任务信息，后续由加载检查与媒体详情共同决定页面是否就绪。 */
    fun updateTaskDetails(details: DownloadTaskWithVideoDetails) {
        mTaskDetailLiveData.value = details
    }

    /**
     * 在 IO 线程从路径或公共 URI 探测媒体，资源更新后取消旧结果的发布资格。
     *
     * 先清空旧详情并进入加载态，防止新记录与旧元数据短暂组合；探测失败只更新错误状态。
     * 原生同步调用未必立刻停止，withContext 返回时的取消检查负责阻止迟到结果回写界面。
     */
    private fun doLoadResourceDetails(resource: DownloadResourceEntity) {
        // 先撤销旧任务和旧详情，再安排新任务，页面操作不能使用上一条资源的编码信息。
        mLoadResourceJob?.cancel()
        mLocalMediaDetailLiveData.value = null
        mLoadingStatusLiveData.value = LoadingStatus.loadingStatus()
        mLoadResourceJob = viewModelScope.launch {
            // URI 权限检查和原生探测可能阻塞；仅将最终页面状态写回主线程。
            val details = withContext(Dispatchers.IO) {
                FFMpegUtils.getMediaInfo(filePath = resource.file, contentUri = resource.contentUri)
            }
            if (details == null) {
                mLoadingStatusLiveData.value = LoadingStatus.errorStatus(
                    message = CommonLibs.getString(id = R.string.error_unable_to_retrieve_content),
                )
            } else {
                mLocalMediaDetailLiveData.value = details
                checkLoaded()
            }
        }
    }

    /**
     * 仅在任务、资源和当前媒体详情全部存在时解除加载态，缺失项保留原来的加载或错误状态。
     */
    fun checkLoaded() {
        if (
            mTaskDetailLiveData.value != null &&
            mResourceLiveData.value != null &&
            mLocalMediaDetailLiveData.value != null
        ) {
            mLoadingStatusLiveData.postValue(LoadingStatus.doneStatus())
        }
    }

    /**
     * 将当前路径和首选 URI 一并交给宿主，由宿主验证可读性并授予外部应用临时读取权限。
     */
    fun tryOpenResource() {
        val taskDetail = mTaskDetailLiveData.value ?: return
        val resource = mResourceLiveData.value ?: return
        sendViewAction(
            action = OpenResourceAction(
                title = taskDetail.title,
                file = File(resource.file),
                mimetype = resource.mimeType,
                contentUri = resource.contentUri
            )
        )
    }

    /**
     * 请求宿主打开当前资源，保留 URI 优先读取和旧路径回退所需的完整参数。
     */
    fun tryPlayResource() {
        val taskDetail = mTaskDetailLiveData.value ?: return
        val resource = mResourceLiveData.value ?: return
        sendViewAction(
            action = PlayResourceAction(
                filePath = resource.file,
                title = "${taskDetail.title} - ${resource.name}",
                mimetype = resource.mimeType,
                contentUri = resource.contentUri
            )
        )
    }

    /**
     * 固定导出名称及来源，取得是否删源的选择后再请求系统目标位置。
     *
     * 对话框返回时重新检查资源可读性和操作互斥状态；只有检查通过才建立待导出快照。
     * 之后页面数据可能刷新，实际复制必须使用快照而不是重新读取可变页面状态。
     */
    fun tryExportResource() {
        if (isTaskProgressing()) return
        val resource = mResourceLiveData.value ?: return
        val taskDetail = mTaskDetailLiveData.value ?: return
        val file = File(resource.file)
        // 命名模板在本次操作开始时取值，避免系统选择器打开期间的偏好变化影响本次导出。
        val fileName = ExportFileNameUtils.buildFileName(
            resourceType = resource.type,
            context = DownloadFileNameUtils.TemplateContext(
                videoName = taskDetail.title.ifBlank { taskDetail.downloadTask.biliBvid },
                partName = taskDetail.partTitle.ifBlank {
                    taskDetail.downloadTask.biliCid.toString()
                },
            ),
            extension = file.extension,
            templates = AppModel.getExportFileNameTemplates(),
        )
        // 保留自定义命名模板，并固定本次导出的资源与清理选项。
        popDialog(dialog = ExportOptionsDialog(), success = {
            val options = it as? ExportOptionsDialog.Result ?: return@popDialog
            val readable = withContext(Dispatchers.IO) {
                FileUtils.resolveReadUri(
                    context = CommonLibs.requireContext(),
                    file = file,
                    contentUri = resource.contentUri
                ) != null
            }
            if (isTaskProgressing() || !readable) return@popDialog
            mPendingExport =
                PendingExport(
                    resource = resource,
                    title = taskDetail.title,
                    deleteSourceAfterExport = options.deleteSourceAfterExport
                )
            sendViewAction(
                action = ExportResourceAction(
                    file = file,
                    name = fileName,
                    mimetype = resource.mimeType,
                    contentUri = resource.contentUri,
                )
            )
        })
    }

    /**
     * 展示转换选项；已有转换会话时改为确认停止，防止同时对同一资源执行多个操作。
     */
    fun tryCovertResource() {
        if (mPendingExport != null || mIsExportingLiveData.value == true) return
        val details = mLocalMediaDetailLiveData.value ?: return
        val resource = resourceLiveData.value ?: return
        val format = details.getAVFormatOrNull() ?: return

        if (mConvertSession?.state == SessionState.RUNNING) {
            popDialog(
                dialog = ConfirmDialog.buildDialog(
                    title = CommonLibs.getString(id = R.string.text_convert),
                    message = CommonLibs.getString(id = R.string.convert_stop_message),
                    leftButtonText = CommonLibs.getString(id = R.string.text_cancel),
                    rightButtonText = CommonLibs.getString(id = R.string.text_stop),
                    rightButtonStyle = ConfirmDialog.Companion.ButtonStyle.Stop
                ),
                success = {
                    if (it is Boolean && it) mConvertSession?.cancel()
                }
            )
            return
        }

        val audioCodec = details.getAudioAVCodecOrNull()
        val videoCodec = details.getVideoAVCodecOrNull()
        popDialog(
            dialog = ConvertDialog.buildDialog(
                title = resource.name,
                currentFormat = format,
                currentAudioCodec = audioCodec,
                currentVideoCodec = videoCodec
            ),
            success = {
                if (isTaskProgressing()) return@popDialog
                mIsConvertingLiveData.value = true
                mConvertSession = doCovertResource(result = it as ConvertDialog.Companion.Result)
                if (mConvertSession == null) onConvertFailed()
            }
        )
    }

    /**
     * 确认后删除当前资源；选择导出目标、实际导出或转换期间禁止删除来源。
     */
    fun tryDeleteResource() {
        if (isTaskProgressing()) {
            // 待导出快照和实际任务都依赖当前来源，操作结束前不能删除。
            popMessage(
                message = ToastMessageAction(content = CommonLibs.getString(id = R.string.resource_have_mission_progress))
            )
            return
        }
        val dialog = ConfirmDialog.buildDialog(
            title = CommonLibs.getString(id = R.string.text_delete_confirm),
            message = CommonLibs.getString(id = R.string.delete_resource_message),
            leftButtonText = CommonLibs.getString(id = R.string.text_cancel),
            rightButtonText = CommonLibs.getString(id = R.string.text_delete),
            rightButtonStyle = ConfirmDialog.Companion.ButtonStyle.Delete
        )
        popDialog(dialog = dialog, success = {
            if (it is Boolean && it) doDeleteResource()
        })
    }

    /**
     * 消费系统选择器返回的目标 URI，按待导出快照执行复制及可选的源清理。
     *
     * 快照先取出并清空，重复结果不能再次导出；取消选择只释放等待状态。
     * 删除源文件的请求必须启用内容回读核验，只有用例确认清理成功后才通知宿主关闭页面。
     *
     * @param uri 系统返回的写入目标；为 null 表示用户取消或来源预检查失败。
     */
    fun exportResource(uri: Uri?) {
        // 选择器返回时不再从页面取来源，避免异步刷新把目标错误关联到另一条资源。
        val request = mPendingExport ?: return
        mPendingExport = null
        uri ?: return
        mIsExportingLiveData.value = true
        viewModelScope.launch {
            // 用例协调 IO 与删源次序；无论成功、失败还是取消，都必须释放页面忙碌状态。
            val result = try {
                mResourceExportUseCase.execute(
                    resource = request.resource,
                    deleteSourceAfterExport = request.deleteSourceAfterExport
                ) {
                    FileUtils.writeFileToUri(
                        context = CommonLibs.requireContext(),
                        uri = uri,
                        sourceFile = File(request.resource.file),
                        verifyContents = request.deleteSourceAfterExport,
                        sourceContentUri = request.resource.contentUri,
                    )
                }
            } finally {
                mIsExportingLiveData.value = false
            }
            // 普通导出、移动成功及源清理失败分别提示，不能把保留双副本误报成移动完成。
            val message = when (result) {
                ResourceExportUseCase.Result.EXPORT_FAILED ->
                    CommonLibs.getString(id = R.string.export_resource_failed_message)

                ResourceExportUseCase.Result.EXPORTED ->
                    CommonLibs.getString(
                        id = R.string.export_resource_success_message,
                        args = arrayOf(request.title)
                    )

                ResourceExportUseCase.Result.SOURCE_DELETED ->
                    CommonLibs.getString(
                        id = R.string.export_source_deleted_message,
                        args = arrayOf(request.title)
                    )

                ResourceExportUseCase.Result.SOURCE_DELETE_FAILED ->
                    CommonLibs.getString(id = R.string.export_source_delete_failed_message)
            }
            if (result == ResourceExportUseCase.Result.SOURCE_DELETED) {
                // 提示和关闭页面通过同一个动作派发，避免 LiveData.postValue 合并连续动作。
                sendViewAction(action = ResourceMovedAction(message = message))
            } else {
                popMessage(message = ToastMessageAction(content = message))
            }
        }
    }

    /**
     * 删除实际载荷及资源记录，存储层未确认删除成功时保留页面并展示错误。
     */
    private suspend fun doDeleteResource() {
        val resource = mResourceLiveData.value ?: return
        if (!DownloadRepository.deleteResource(resource = resource)) {
            popMessage(
                message = ToastMessageAction(content = CommonLibs.getString(id = R.string.delete_resource_failed_message))
            )
            return
        }
        finishActivity()
    }

    /**
     * 以独立临时文件启动转换，来源路径和 URI 共同传给原生输入解析入口。
     *
     * FFmpegKit 完成回调负责登记成功输出并清理临时路径；输入资源始终保留。
     * 进度回调以毫秒接收处理时间，除以媒体的秒级时长换算值后用于百分比展示。
     *
     * @return 已启动的 FFmpeg 会话；缺少元数据、来源或所需编码选项时返回 null。
     */
    private fun doCovertResource(result: ConvertDialog.Companion.Result): FFmpegSession? {
        val details = mLocalMediaDetailLiveData.value ?: return null
        val resource = resourceLiveData.value ?: return null

        val sourceAudioCodec = details.getAudioAVCodecOrNull()
        val sourceVideoCodec = details.getVideoAVCodecOrNull()

        // 使用独立转换目录，原生处理完成前不能将半成品当作正式资源暴露。
        val targetName =
            "convert-${resource.taskId}-${System.currentTimeMillis()}.${result.format.suffix}"
        val convertCacheFile = File(CommonLibs.requireConvertTemporaryDir(), targetName)

        // 回调运行在 FFmpegKit 线程；成功后先迁移/登记，失败时清理本次临时输出并释放忙碌状态。
        val completeCallback = { isSuccess: Boolean ->
            if (isSuccess) {
                onConvertSuccess(
                    targetName = targetName,
                    convertCacheFile = convertCacheFile,
                    targetResult = result
                )
            } else {
                onConvertFailed()
            }
            convertCacheFile.delete()
            mIsConvertingLiveData.postValue(false)
        }

        mConvertProgressLiveData.value =
            CommonLibs.getString(id = R.string.convert_progress, args = arrayOf(0.0f))

        return FFMpegUtils.convertMediaAsync(
            sourceFile = resource.file,
            targetFile = convertCacheFile.absolutePath,
            videoCodec = sourceVideoCodec?.let { it to (result.videoCodec ?: return null) },
            audioCodec = sourceAudioCodec?.let { it to (result.audioCodec ?: return null) },
            onProgress = {
                val durationMillis = details.duration * 1000
                val progress = if (details.duration == 0.0) 0.0 else (it / durationMillis) * 100
                mConvertProgressLiveData.postValue(
                    CommonLibs.getString(id = R.string.convert_progress, args = arrayOf(progress))
                )
                Log.d(TAG, "doCovertResource: times: $it/${details.duration}, progress: $progress")
            },
            onComplete = completeCallback,
            contentUri = resource.contentUri,
        )
    }

    /**
     * 将已完成的转换输出移入工作目录并登记，再按存储偏好尝试发布到公共位置。
     *
     * 发布失败时保留已登记的工作资源并标记任务发布失败，不能把唯一有效成品当临时缓存删除。
     */
    private fun onConvertSuccess(
        targetName: String,
        convertCacheFile: File,
        targetResult: ConvertDialog.Companion.Result
    ) {
        val resource = mResourceLiveData.value ?: return
        val targetFile = File(CommonLibs.requireResourceWorkingDir(), targetName)
        // 将临时文件移动到资源目录
        if (!convertCacheFile.renameTo(targetFile)) {
            onConvertFailed()
            return
        }
        val audioCodecName = targetResult.audioCodec?.let { ",${it.name}" } ?: ""
        val videoCodecName = targetResult.videoCodec?.let { ",${it.name}" } ?: ""
        // 登记资源
        runBlocking {
            val registeredResource = DownloadRepository.registerResource(
                downloadTaskId = resource.taskId,
                resourceName = "Convert(${targetResult.format.name}$audioCodecName$videoCodecName)",
                downloadResourceType = resource.type,
                resourceFile = targetFile.absoluteFile,
                mimeType = targetResult.format.mimeType
            )
            val storedResource = when (
                val publishResult =
                    DownloadRepository.publishResourceIfNeeded(entity = registeredResource)
            ) {
                is ResourcePublishResult.Success -> publishResult.resource
                is ResourcePublishResult.Failure -> {
                    DownloadRepository.getDownloadTaskByTaskId(id = resource.taskId)?.let { task ->
                        DownloadRepository.update(downloadTask = task.apply {
                            status = TaskStatus.PUBLISH_FAILED.code
                        })
                    }
                    Log.e(
                        TAG,
                        "Unable to publish converted resource: ${publishResult.reason}",
                        publishResult.cause
                    )
                    popMessage(
                        message = ToastMessageAction(
                            content = CommonLibs.getString(id = R.string.convert_resource_publish_failed_message)
                        )
                    )
                    registeredResource
                }
            }
            startActivity(
                targetClass = LocalResourceActivity::class.java,
                targetIntent = LocalResourceActivity.buildIntent(
                    taskId = resource.taskId,
                    resourceId = storedResource.id
                )
            )
        }
    }

    /**
     * 通知转换失败，实际临时输出清理由转换完成回调负责。
     */
    private fun onConvertFailed() {
        popMessage(
            message = ToastMessageAction(content = CommonLibs.getString(id = R.string.convert_resource_failed_message))
        )
    }

    /**
     * 导出选择或实际文件任务结束前拦截页面退出，避免来源和临时输出失去操作入口。
     */
    override fun finishActivity(activityResult: ActivityResult?) {
        if (isTaskProgressing()) {
            popMessage(
                message = ToastMessageAction(content = CommonLibs.getString(id = R.string.resource_have_mission_progress))
            )
            return
        }
        super.finishActivity(activityResult = activityResult)
    }

    /**
     * 将等待系统选择器也视为资源占用状态，用于统一阻止并发操作、删除及退出。
     */
    private fun isTaskProgressing(): Boolean {
        return mPendingExport != null ||
            mIsExportingLiveData.value == true || mIsConvertingLiveData.value == true
    }

}
