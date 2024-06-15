package cc.kafuu.bilidownload.view.activity

import android.content.Intent
import cc.kafuu.bilidownload.BR
import cc.kafuu.bilidownload.R
import cc.kafuu.bilidownload.common.core.CoreActivity
import cc.kafuu.bilidownload.common.room.entity.DownloadResourceEntity
import cc.kafuu.bilidownload.common.room.repository.DownloadRepository
import cc.kafuu.bilidownload.databinding.ActivityLocalResourceBinding
import cc.kafuu.bilidownload.viewmodel.activity.LocalResourceVideModel

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

    override fun initViews() {
        if (initObserver()) return
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

}