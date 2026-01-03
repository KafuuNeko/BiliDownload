package cc.kafuu.bilidownload.feature.viewbinding.view.activity

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import cc.kafuu.bilidownload.BR
import cc.kafuu.bilidownload.R
import cc.kafuu.bilidownload.common.CommonLibs
import cc.kafuu.bilidownload.common.adapter.LocalResourceRVAdapter
import cc.kafuu.bilidownload.common.core.viewbinding.CoreActivity
import cc.kafuu.bilidownload.common.model.ResultWrapper
import cc.kafuu.bilidownload.common.model.action.ViewAction
import cc.kafuu.bilidownload.common.model.event.DownloadStatusChangeEvent
import cc.kafuu.bilidownload.common.room.repository.DownloadRepository
import cc.kafuu.bilidownload.common.utils.FileUtils
import cc.kafuu.bilidownload.databinding.ActivityHistoryDetailsBinding
import cc.kafuu.bilidownload.feature.viewbinding.view.dialog.ConfirmDialog
import cc.kafuu.bilidownload.feature.viewbinding.viewmodel.activity.HistoryDetailsViewModel
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.io.File

class HistoryDetailsActivity : CoreActivity<ActivityHistoryDetailsBinding, HistoryDetailsViewModel>(
    HistoryDetailsViewModel::class.java,
    R.layout.activity_history_details,
    BR.viewModel
) {
    companion object {
        private const val TAG = "HistoryDetailsActivity"

        private const val KEY_ENTITY_ID = "entity_id"

        fun buildIntent(entityId: Long) = Intent().apply {
            putExtra(KEY_ENTITY_ID, entityId)
        }
    }

    private lateinit var mCreateDocumentLauncher: ActivityResultLauncher<Intent>
    private var mPendingCoverUrl: String? = null
    private var mPendingFileName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EventBus.getDefault().register(this)
        val contracts = ActivityResultContracts.StartActivityForResult()
        mCreateDocumentLauncher = registerForActivityResult(contracts) {
            if (it.resultCode == RESULT_OK && it.data != null) {
                val uri = it.data?.data ?: return@registerForActivityResult
                lifecycleScope.launch {
                    saveCoverToUri(uri)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        EventBus.getDefault().unregister(this)
    }

    override fun initViews() {
        setImmersionStatusBar()
        mViewDataBinding.initView()
        initData()
    }

    private fun ActivityHistoryDetailsBinding.initView() {
        rvResources.apply {
            layoutManager = LinearLayoutManager(this@HistoryDetailsActivity)
            adapter = LocalResourceRVAdapter(mViewModel, this@HistoryDetailsActivity)
        }
        tvFailedDelete.setOnClickListener {
            onDelete()
        }
        tvHistoryDelete.setOnClickListener {
            onDelete()
        }
    }

    private fun initData() {
        val entityId = intent.getLongExtra(KEY_ENTITY_ID, -1).also {
            Log.d(TAG, "initData: $it")
            if (it == -1L) {
                mViewModel.finishActivity()
                return
            }
        }
        DownloadRepository.queryDownloadTaskDetailByTaskId(entityId).observe(this) {
            mViewModel.updateVideoDetails(it)
        }
        DownloadRepository.queryResourcesLiveDataByTaskId(entityId).observe(this) {
            mViewModel.updateDownloadResources(it)
        }
    }

    private fun onDelete() = lifecycleScope.launch {
        val result = ConfirmDialog.buildDialog(
            CommonLibs.getString(R.string.text_delete_confirm),
            CommonLibs.getString(R.string.delete_task_message),
            CommonLibs.getString(R.string.text_cancel),
            CommonLibs.getString(R.string.text_delete),
            rightButtonStyle = ConfirmDialog.Companion.ButtonStyle.Delete
        ).showAndWaitResult(this@HistoryDetailsActivity)
        if (result is ResultWrapper.Success && result.value) {
            mViewModel.deleteDownloadTask()
        }
    }

    @Subscribe(threadMode = ThreadMode.POSTING)
    fun onDownloadStatusChangeEvent(event: DownloadStatusChangeEvent) {
        if (event.task.id != mViewModel.downloadDetailsLiveData.value?.downloadTask?.id) return
        mViewModel.updateDownloadStatus()
    }

    override fun onViewAction(action: ViewAction) {
        when (action) {
            is HistoryDetailsViewModel.Companion.SaveCoverAction -> onSaveCover(action)
            is HistoryDetailsViewModel.Companion.ShowSaveCoverConfirmAction -> onShowSaveCoverConfirm(action)
            else -> super.onViewAction(action)
        }
    }
    
    private fun onShowSaveCoverConfirm(action: HistoryDetailsViewModel.Companion.ShowSaveCoverConfirmAction) {
        lifecycleScope.launch {
            val result = ConfirmDialog.buildDialog(
                CommonLibs.getString(R.string.text_save_cover),
                CommonLibs.getString(R.string.save_cover_confirm_message),
                CommonLibs.getString(R.string.text_cancel),
                CommonLibs.getString(R.string.text_confirm)
            ).showAndWaitResult(this@HistoryDetailsActivity)
            if (result is ResultWrapper.Success && result.value) {
                mViewModel.confirmSaveCover(action.coverUrl, action.bvid)
            }
        }
    }

    private fun onSaveCover(action: HistoryDetailsViewModel.Companion.SaveCoverAction) {
        mPendingCoverUrl = action.coverUrl
        mPendingFileName = action.fileName
        
        // 从文件名中提取扩展名，确定MIME类型
        val extension = action.fileName.substringAfterLast('.', "jpg")
        val mimeType = when (extension.lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            else -> "image/jpeg"
        }
        
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = mimeType
            putExtra(Intent.EXTRA_TITLE, action.fileName)
        }
        mCreateDocumentLauncher.launch(intent)
    }

    private suspend fun saveCoverToUri(uri: Uri) {
        val coverUrl = mPendingCoverUrl ?: return
        val fileName = mPendingFileName ?: return
        
        // 创建临时文件
        val tempFile = File(CommonLibs.requireContext().cacheDir, "cover_${System.currentTimeMillis()}.tmp")
        
        try {
            // 下载图片到临时文件
            val success = FileUtils.downloadImageToFile(coverUrl, tempFile)
            if (!success) {
                mViewModel.popMessage(
                    cc.kafuu.bilidownload.common.model.action.popmessage.ToastMessageAction(
                        CommonLibs.getString(R.string.save_cover_failed_message)
                    )
                )
                return
            }
            
            // 将临时文件写入到用户选择的URI
            val writeSuccess = FileUtils.writeFileToUri(CommonLibs.requireContext(), uri, tempFile)
            if (writeSuccess) {
                mViewModel.popMessage(
                    cc.kafuu.bilidownload.common.model.action.popmessage.ToastMessageAction(
                        CommonLibs.getString(R.string.save_cover_success_message)
                    )
                )
            } else {
                mViewModel.popMessage(
                    cc.kafuu.bilidownload.common.model.action.popmessage.ToastMessageAction(
                        CommonLibs.getString(R.string.save_cover_failed_message)
                    )
                )
            }
        } finally {
            // 清理临时文件
            tempFile.delete()
            mPendingCoverUrl = null
            mPendingFileName = null
        }
    }

}