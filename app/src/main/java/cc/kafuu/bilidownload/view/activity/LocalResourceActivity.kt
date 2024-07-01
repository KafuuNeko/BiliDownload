package cc.kafuu.bilidownload.view.activity

import android.content.Intent
import android.os.Bundle
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.DialogFragment
import cc.kafuu.bilidownload.BR
import cc.kafuu.bilidownload.R
import cc.kafuu.bilidownload.common.core.CoreActivity
import cc.kafuu.bilidownload.common.room.entity.DownloadResourceEntity
import cc.kafuu.bilidownload.common.room.repository.DownloadRepository
import cc.kafuu.bilidownload.common.CommonLibs
import cc.kafuu.bilidownload.databinding.ActivityLocalResourceBinding
import cc.kafuu.bilidownload.view.dialog.ConfirmDialog
import cc.kafuu.bilidownload.viewmodel.activity.LocalResourceVideModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class LocalResourceActivity : CoreActivity<ActivityLocalResourceBinding, LocalResourceVideModel>(
    LocalResourceVideModel::class.java,
    R.layout.activity_local_resource,
    BR.viewModel
) {
    companion object {
        private const val KEY_TASK_ENTITY_ID = "taskEntityId"
        private const val KEY_RESOURCE_ID = "resourceId"
        fun buildIntent(resource: DownloadResourceEntity) = Intent().apply {
            putExtra(KEY_TASK_ENTITY_ID, resource.taskEntityId)
            putExtra(KEY_RESOURCE_ID, resource.id)
        }
    }

    private var mCurrentDialog: DialogFragment? = null

    private lateinit var mCreateDocumentLauncher: ActivityResultLauncher<Intent>

    private val mCoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val contracts = ActivityResultContracts.StartActivityForResult()
        mCreateDocumentLauncher = registerForActivityResult(contracts) {
            if (it.resultCode == RESULT_OK && it.data != null) {
                mCoroutineScope.launch { mViewModel.exportResource(it.data?.data ?: return@launch) }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mCoroutineScope.cancel()
    }

    override fun initViews() {
        if (!initObserver()) return
        mViewDataBinding.initView()
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

        DownloadRepository.queryDownloadTask(downloadEntityId).observe(this) {
            it?.let { mViewModel.updateTaskDetails(it) }
        }

        mViewModel.taskDetailLiveData.observe(this) { mViewModel.checkLoaded() }
        mViewModel.resourceLiveData.observe(this) { mViewModel.checkLoaded() }
        mViewModel.localMediaDetailLiveData.observe(this) { mViewModel.checkLoaded() }

        return true
    }

    private fun ActivityLocalResourceBinding.initView() {
        tvResourceOpen.setOnClickListener { onResourceOpen() }
        tvResourceExport.setOnClickListener { onResourceExport() }
        tvResourceDelete.setOnClickListener { onResourceDelete() }
    }

    private fun onResourceOpen() {
        mViewModel.tryShareResource(this)
    }

    private fun onResourceExport() {
        mViewModel.tryExportResource(mCreateDocumentLauncher)
    }

    private fun onResourceDelete() {
        if (mCurrentDialog?.isAdded == true) {
            mCurrentDialog?.dismiss()
        }

        mCurrentDialog = ConfirmDialog.buildDialog(
            CommonLibs.getString(R.string.text_delete_confirm),
            CommonLibs.getString(R.string.delete_resource_message),
            CommonLibs.getString(R.string.text_cancel),
            CommonLibs.getString(R.string.text_delete),
        ) {
            mCoroutineScope.launch { mViewModel.deleteResource() }
            true
        }.apply {
            rightButtonTextColor = CommonLibs.getColor(R.color.white)
            rightButtonBackground = CommonLibs.getDrawable(R.drawable.shape_button_delete)
        }
        mCurrentDialog?.show(supportFragmentManager, null)
    }

}