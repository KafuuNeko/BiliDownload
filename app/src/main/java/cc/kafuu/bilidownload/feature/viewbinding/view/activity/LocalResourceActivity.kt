package cc.kafuu.bilidownload.feature.viewbinding.view.activity

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import cc.kafuu.bilidownload.BR
import cc.kafuu.bilidownload.R
import cc.kafuu.bilidownload.common.core.viewbinding.CoreActivity
import cc.kafuu.bilidownload.common.model.action.ViewAction
import cc.kafuu.bilidownload.common.room.entity.DownloadResourceEntity
import cc.kafuu.bilidownload.common.room.repository.DownloadRepository
import cc.kafuu.bilidownload.common.utils.FileUtils
import cc.kafuu.bilidownload.common.utils.MimeTypeUtils
import cc.kafuu.bilidownload.databinding.ActivityLocalResourceBinding
import cc.kafuu.bilidownload.feature.compose.activity.MediaPlayerActivity
import cc.kafuu.bilidownload.feature.compose.activity.MusicPlayerActivity
import cc.kafuu.bilidownload.feature.viewbinding.viewmodel.activity.LocalResourceVideModel
import java.io.File

/**
 * 展示已登记资源并承载系统文件选择、播放器与外部应用跳转。
 *
 * ViewModel 固定导出来源和清理选择，宿主只负责取得目标 URI；不能因旧路径失效而忽略有效公共 URI。
 */
class LocalResourceActivity : CoreActivity<ActivityLocalResourceBinding, LocalResourceVideModel>(
    vmClass = LocalResourceVideModel::class.java,
    layoutId = R.layout.activity_local_resource,
    viewModelId = BR.viewModel
) {
    companion object {
        private const val KEY_TASK_ENTITY_ID = "task_entity_id"
        private const val KEY_RESOURCE_ID = "resource_id"

        /** 仅传递持久化身份，进入页面后通过观察数据库取得最新路径和 URI。 */
        fun buildIntent(resource: DownloadResourceEntity) =
            buildIntent(taskId = resource.taskId, resourceId = resource.id)

        /** 构造指定任务下资源的页面参数；无效的缺省身份在页面初始化时拒绝。 */
        fun buildIntent(taskId: Long, resourceId: Long) = Intent().apply {
            putExtra(KEY_TASK_ENTITY_ID, taskId)
            putExtra(KEY_RESOURCE_ID, resourceId)
        }
    }

    private lateinit var mCreateDocumentLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(this) {
            mViewModel.finishActivity()
        }
        setImmersionStatusBar()
        val contracts = ActivityResultContracts.StartActivityForResult()
        mCreateDocumentLauncher = registerForActivityResult(contracts) { result ->
            val uri = if (result.resultCode == RESULT_OK) result.data?.data else null
            mViewModel.exportResource(uri = uri)
        }
    }

    override fun initViews() {
        initObserver()
    }

    /** 校验入口身份并观察资源、任务和元数据；三者就绪前保留加载或错误界面。 */
    private fun initObserver(): Boolean {
        val downloadEntityId = intent.getLongExtra(KEY_TASK_ENTITY_ID, -1)
        val resourceId = intent.getLongExtra(KEY_RESOURCE_ID, -1)

        if (resourceId == -1L || downloadEntityId == -1L) {
            mViewModel.finishActivity()
            return false
        }

        // 以数据库记录为来源，公共发布或转换导致地址变化后重新探测，不沿用旧路径快照。
        DownloadRepository.queryResourceLiveDataById(resourceId = resourceId).observe(this) {
            it?.let { mViewModel.updateResourceEntity(resource = it) }
        }

        DownloadRepository.queryDownloadTaskDetailByTaskId(entityId = downloadEntityId)
            .observe(this) {
                it?.let { mViewModel.updateTaskDetails(details = it) }
            }

        // 各数据独立返回，任何一项变化都重新判断页面是否已具备可操作的完整详情。
        mViewModel.taskDetailLiveData.observe(this) { mViewModel.checkLoaded() }
        mViewModel.resourceLiveData.observe(this) { mViewModel.checkLoaded() }
        mViewModel.localMediaDetailLiveData.observe(this) { mViewModel.checkLoaded() }

        return true
    }

    override fun onViewAction(action: ViewAction) = when (action) {
        is LocalResourceVideModel.Companion.OpenResourceAction -> onOpenResource(action = action)
        is LocalResourceVideModel.Companion.ExportResourceAction -> onExportResource(action = action)
        is LocalResourceVideModel.Companion.PlayResourceAction -> onPlayResource(action = action)
        is LocalResourceVideModel.Companion.ResourceMovedAction -> {
            Toast.makeText(this, action.message, Toast.LENGTH_LONG).show()
            finish()
        }

        else -> super.onViewAction(action = action)
    }

    /**
     * 打开目标选择器前再次核验路径/URI，来源失效时向模型回传取消结果以释放待导出状态。
     *
     * 即使此处成功，权限和资源仍可能在选择器打开期间改变，实际复制时必须重新打开来源。
     */
    private fun onExportResource(action: LocalResourceVideModel.Companion.ExportResourceAction) {
        if (FileUtils.resolveReadUri(
                context = this,
                file = action.file,
                contentUri = action.contentUri
            ) == null
        ) {
            mViewModel.exportResource(uri = null)
            return
        }
        FileUtils.tryExportFile(
            file = action.file,
            name = action.name,
            mimetype = action.mimetype,
            createDocumentLauncher = mCreateDocumentLauncher,
            contentUri = action.contentUri,
        )
    }

    private fun onOpenResource(action: LocalResourceVideModel.Companion.OpenResourceAction) {
        val opened = FileUtils.tryOpenFileWithOtherApp(
            context = this,
            title = action.title,
            file = action.file,
            mimetype = action.mimetype,
            contentUri = action.contentUri
        )
        if (!opened) {
            Toast.makeText(this, R.string.no_external_player_message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun onPlayResource(action: LocalResourceVideModel.Companion.PlayResourceAction) {
        val playbackUri = FileUtils.resolveReadUri(
            context = this,
            file = File(action.filePath),
            contentUri = action.contentUri
        )?.toString()
        if (playbackUri == null) {
            Toast.makeText(this, R.string.no_external_player_message, Toast.LENGTH_SHORT).show()
            return
        }
        val intent = if (MimeTypeUtils.isAudioMimeType(mimeType = action.mimetype)) {
            MusicPlayerActivity.buildIntent(
                filePath = action.filePath,
                title = action.title,
                mimeType = action.mimetype,
                contentUri = playbackUri
            ).apply {
                setClass(this@LocalResourceActivity, MusicPlayerActivity::class.java)
            }
        } else {
            MediaPlayerActivity.buildIntent(
                filePath = action.filePath,
                title = action.title,
                mimeType = action.mimetype,
                contentUri = playbackUri
            ).apply {
                setClass(this@LocalResourceActivity, MediaPlayerActivity::class.java)
            }
        }
        startActivity(intent)
    }
}
