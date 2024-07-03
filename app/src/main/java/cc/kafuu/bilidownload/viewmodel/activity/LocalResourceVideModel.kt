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
import cc.kafuu.bilidownload.common.model.action.popmessage.ToastMessageAction
import cc.kafuu.bilidownload.common.room.dto.DownloadTaskWithVideoDetails
import cc.kafuu.bilidownload.common.room.entity.DownloadResourceEntity
import cc.kafuu.bilidownload.common.room.repository.DownloadRepository
import cc.kafuu.bilidownload.common.CommonLibs
import cc.kafuu.bilidownload.common.utils.FFMpegUtils
import cc.kafuu.bilidownload.common.utils.FileUtils
import cc.kafuu.bilidownload.common.utils.liveData
import java.io.File

class LocalResourceVideModel : CoreViewModel() {
    companion object {
        private const val TAG = "LocalResourceVideModel"
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

    // 此资源文件信息
    private val mLocalMediaDetailLiveData = MutableLiveData<LocalMediaDetail>()
    val localMediaDetailLiveData = mLocalMediaDetailLiveData.liveData()

    private val mIsExportingLiveData = MutableLiveData(false)
    val isExportingLiveData = mIsExportingLiveData.liveData()

    fun updateResourceEntity(resource: DownloadResourceEntity) {
        mResourceLiveData.value = resource
        doLoadResourceDetails(resource)
    }

    fun updateTaskDetails(details: DownloadTaskWithVideoDetails) {
        mTaskDetailLiveData.value = details
    }

    private fun doLoadResourceDetails(resource: DownloadResourceEntity) {
        object : IAsyncCallback<LocalMediaDetail, Exception> {
            override fun onSuccess(data: LocalMediaDetail) {
                Log.d(TAG, "onSuccess: $data")
                mLocalMediaDetailLiveData.postValue(data)
            }

            override fun onFailure(exception: Exception) {
                exception.printStackTrace()
                mLoadingStatusLiveData.postValue(
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
            mTaskDetailLiveData.value != null &&
            mResourceLiveData.value != null &&
            mLocalMediaDetailLiveData.value != null
        ) {
            mLoadingStatusLiveData.postValue(LoadingStatus.doneStatus())
        }
    }

    fun tryShareResource(context: Context) {
        val taskDetail = mTaskDetailLiveData.value ?: return
        val resource = mResourceLiveData.value ?: return
        FileUtils.tryShareFile(context, taskDetail.title, File(resource.file), resource.mimeType)
    }

    fun tryExportResource(createDocumentLauncher: ActivityResultLauncher<Intent>) {
        if (mIsExportingLiveData.value == true) return
        val resource = mResourceLiveData.value ?: return
        val file = File(resource.file)
        FileUtils.tryExportFile(file, resource.mimeType, createDocumentLauncher)
    }

    fun exportResource(uri: Uri) {
        val taskDetail = mTaskDetailLiveData.value ?: return
        val resource = mResourceLiveData.value ?: return
        val sourceFile = File(resource.file)
        mIsExportingLiveData.postValue(true)
        if (!FileUtils.writeFileToUri(CommonLibs.requireContext(), uri, sourceFile)) {
            popMessage(
                ToastMessageAction(
                    CommonLibs.getString(R.string.export_resource_failed_message),
                    Toast.LENGTH_SHORT
                )
            )
        } else {
            popMessage(
                ToastMessageAction(
                    CommonLibs.getString(
                        R.string.export_resource_success_message,
                        taskDetail.title
                    ),
                    Toast.LENGTH_SHORT
                )
            )
        }
        mIsExportingLiveData.postValue(false)
    }

    suspend fun deleteResource() {
        val resource = mResourceLiveData.value ?: return
        val file = File(resource.file)
        if (file.exists() && !file.delete()) {
            popMessage(
                ToastMessageAction(
                    CommonLibs.getString(R.string.delete_resource_failed_message),
                    Toast.LENGTH_SHORT
                )
            )
            return
        }
        DownloadRepository.deleteResourceById(resource.id)
        finishActivity()
    }

}