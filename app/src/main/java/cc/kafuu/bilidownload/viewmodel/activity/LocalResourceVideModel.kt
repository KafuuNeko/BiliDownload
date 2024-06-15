package cc.kafuu.bilidownload.viewmodel.activity

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.lifecycle.MutableLiveData
import cc.kafuu.bilidownload.R
import cc.kafuu.bilidownload.common.core.CoreViewModel
import cc.kafuu.bilidownload.common.model.IAsyncCallback
import cc.kafuu.bilidownload.common.model.LoadingStatus
import cc.kafuu.bilidownload.common.model.LocalMediaDetail
import cc.kafuu.bilidownload.common.model.popmessage.ToastMessage
import cc.kafuu.bilidownload.common.room.dto.DownloadTaskWithVideoDetails
import cc.kafuu.bilidownload.common.room.entity.DownloadResourceEntity
import cc.kafuu.bilidownload.common.room.repository.DownloadRepository
import cc.kafuu.bilidownload.common.utils.CommonLibs
import cc.kafuu.bilidownload.common.utils.FFMpegUtils
import cc.kafuu.bilidownload.common.utils.FileUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

class LocalResourceVideModel : CoreViewModel() {
    companion object {
        private const val TAG = "LocalResourceVideModel"
    }

    private val mScope = CoroutineScope(Dispatchers.Default + SupervisorJob())


    // 此页面加载状态，loading状态将显示加载动画（默认开启）
    val loadingStatusLiveData = MutableLiveData(LoadingStatus.loadingStatus())

    // 此资源隶属的任务详情
    val taskDetailLiveData = MutableLiveData<DownloadTaskWithVideoDetails>()

    // 下载的资源实体
    val resourceLiveData = MutableLiveData<DownloadResourceEntity>()

    // 此资源文件信息
    val localMediaDetailLiveData = MutableLiveData<LocalMediaDetail>()

    val isExportingLiveData = MutableLiveData(false)

    fun updateResourceEntity(resource: DownloadResourceEntity) {
        resourceLiveData.value = resource
        doLoadResourceDetails(resource)
    }

    fun updateTaskDetails(details: DownloadTaskWithVideoDetails) {
        taskDetailLiveData.value = details
    }

    private fun doLoadResourceDetails(resource: DownloadResourceEntity) {
        object : IAsyncCallback<LocalMediaDetail, Exception> {
            override fun onSuccess(data: LocalMediaDetail) {
                Log.d(TAG, "onSuccess: $data")
                localMediaDetailLiveData.postValue(data)
            }

            override fun onFailure(exception: Exception) {
                exception.printStackTrace()
                loadingStatusLiveData.postValue(
                    LoadingStatus.errorStatus(message = exception.message ?: "Unknown exception")
                )
            }
        }.also { FFMpegUtils.getMediaInfo(resource.file, it) }
    }

    /**
     * 校验数据是否已经全部加载完成，若已全部加载完成则设置加载状态为完成状态
     */
    fun checkLoaded() {
        if (
            taskDetailLiveData.value != null &&
            resourceLiveData.value != null &&
            localMediaDetailLiveData.value != null
        ) {
            loadingStatusLiveData.postValue(LoadingStatus.doneStatus())
        }
    }

    fun tryShareResource(context: Context) {
        val taskDetail = taskDetailLiveData.value ?: return
        val resource = resourceLiveData.value ?: return
        FileUtils.tryShareFile(context, taskDetail.title, File(resource.file), resource.mimeType)
    }

    fun tryExportResource(createDocumentLauncher: ActivityResultLauncher<Intent>) {
        if (isExportingLiveData.value == true) return
        val resource = resourceLiveData.value ?: return
        val file = File(resource.file)
        FileUtils.tryExportFile(file, resource.mimeType, createDocumentLauncher)
    }

    fun exportResource(uri: Uri) {
        val taskDetail = taskDetailLiveData.value ?: return
        val resource = resourceLiveData.value ?: return
        val sourceFile = File(resource.file)
        isExportingLiveData.value = true
        mScope.launch {
            if (!FileUtils.writeFileToUri(CommonLibs.requireContext(), uri, sourceFile)) {
                popMessage(
                    ToastMessage(
                        CommonLibs.getString(R.string.export_resource_failed_message),
                        Toast.LENGTH_SHORT
                    )
                )
            } else {
                popMessage(
                    ToastMessage(
                        CommonLibs.getString(
                            R.string.export_resource_success_message,
                            taskDetail.title
                        ),
                        Toast.LENGTH_SHORT
                    )
                )
            }
            isExportingLiveData.postValue(false)
        }
    }

    fun deleteResource() {
        val resource = resourceLiveData.value ?: return
        val file = File(resource.file)
        if (file.exists() && !file.delete()) {
            popMessage(
                ToastMessage(
                    CommonLibs.getString(R.string.delete_resource_failed_message),
                    Toast.LENGTH_SHORT
                )
            )
            return
        }
        mScope.launch {
            DownloadRepository.deleteResourceById(resource.id)
            finishActivity()
        }
    }

}