package cc.kafuu.bilidownload.feature.viewbinding.view.activity

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import cc.kafuu.bilidownload.BR
import cc.kafuu.bilidownload.R
import cc.kafuu.bilidownload.common.core.viewbinding.CoreActivity
import cc.kafuu.bilidownload.common.model.action.ViewAction
import cc.kafuu.bilidownload.common.room.entity.DownloadResourceEntity
import cc.kafuu.bilidownload.common.room.repository.DownloadRepository
import cc.kafuu.bilidownload.common.utils.FileUtils
import cc.kafuu.bilidownload.databinding.ActivityLocalResourceBinding
import cc.kafuu.bilidownload.feature.viewbinding.viewmodel.activity.LocalResourceVideModel
import kotlinx.coroutines.launch

class LocalResourceActivity : CoreActivity<ActivityLocalResourceBinding, LocalResourceVideModel>(
    LocalResourceVideModel::class.java,
    R.layout.activity_local_resource,
    BR.viewModel
) {
    companion object {
        private const val KEY_TASK_ENTITY_ID = "task_entity_id"
        private const val KEY_RESOURCE_ID = "resource_id"
        fun buildIntent(resource: DownloadResourceEntity) =
            buildIntent(resource.taskId, resource.id)

        fun buildIntent(taskId: Long, resourceId: Long) = Intent().apply {
            putExtra(KEY_TASK_ENTITY_ID, taskId)
            putExtra(KEY_RESOURCE_ID, resourceId)
        }
    }

    private lateinit var mCreateDocumentLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setImmersionStatusBar()
        val contracts = ActivityResultContracts.StartActivityForResult()
        mCreateDocumentLauncher = registerForActivityResult(contracts) {
            if (it.resultCode == RESULT_OK && it.data != null) {
                lifecycleScope.launch { mViewModel.exportResource(it.data?.data ?: return@launch) }
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            mViewModel.finishActivity()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun initViews() {
        initObserver()
    }

    private fun initObserver(): Boolean {
        val downloadEntityId = intent.getLongExtra(KEY_TASK_ENTITY_ID, -1)
        val resourceId = intent.getLongExtra(KEY_RESOURCE_ID, -1)

        if (resourceId == -1L || downloadEntityId == -1L) {
            mViewModel.finishActivity()
            return false
        }

        DownloadRepository.queryResourceLiveDataById(resourceId).observe(this) {
            it?.let { mViewModel.updateResourceEntity(it) }
        }

        DownloadRepository.queryDownloadTaskDetailByTaskId(downloadEntityId).observe(this) {
            it?.let { mViewModel.updateTaskDetails(it) }
        }

        mViewModel.taskDetailLiveData.observe(this) { mViewModel.checkLoaded() }
        mViewModel.resourceLiveData.observe(this) { mViewModel.checkLoaded() }
        mViewModel.localMediaDetailLiveData.observe(this) { mViewModel.checkLoaded() }

        return true
    }

    override fun onViewAction(action: ViewAction) = when (action) {
        is LocalResourceVideModel.Companion.ShareResourceAction -> onShareResource(action)
        is LocalResourceVideModel.Companion.ExportResourceAction -> onExportResource(action)
        else -> super.onViewAction(action)
    }

    private fun onExportResource(action: LocalResourceVideModel.Companion.ExportResourceAction) {
        FileUtils.tryExportFile(action.file, action.name, action.mimetype, mCreateDocumentLauncher)
    }

    private fun onShareResource(action: LocalResourceVideModel.Companion.ShareResourceAction) {
        FileUtils.tryShareFile(this, action.title, action.file, action.mimetype)
    }
}