package cc.kafuu.bilidownload.feature.viewbinding.view.fragment

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import cc.kafuu.bilidownload.R
import cc.kafuu.bilidownload.common.CommonLibs
import cc.kafuu.bilidownload.common.adapter.DownloadHistoryRVAdapter
import cc.kafuu.bilidownload.common.core.viewbinding.CoreFragmentBuilder
import cc.kafuu.bilidownload.common.ext.getSerializableByClass
import cc.kafuu.bilidownload.common.model.TaskStatus
import cc.kafuu.bilidownload.common.model.action.ViewAction
import cc.kafuu.bilidownload.common.room.dto.DownloadTaskWithVideoDetails
import cc.kafuu.bilidownload.common.utils.DebounceQueue
import cc.kafuu.bilidownload.feature.viewbinding.view.dialog.ConfirmDialog
import cc.kafuu.bilidownload.feature.viewbinding.view.fragment.common.RVFragment
import cc.kafuu.bilidownload.feature.viewbinding.viewmodel.fragment.HistoryViewModel
import com.arialyy.annotations.DownloadGroup
import com.arialyy.aria.core.Aria
import com.arialyy.aria.core.task.DownloadGroupTask
import kotlinx.coroutines.launch

class DownloadHistoryFragment : RVFragment<HistoryViewModel>(HistoryViewModel::class.java) {
    companion object {
        private const val KEY_STATES = "states"

        class Builder(private val states: List<TaskStatus>) :
            CoreFragmentBuilder<DownloadHistoryFragment>() {
            override fun onMallocFragment() = DownloadHistoryFragment()
            override fun onPreparationArguments() {
                putArgument(KEY_STATES, states.toTypedArray())
            }
        }

        @JvmStatic
        fun builder(vararg states: TaskStatus) = Builder(states.toList())
    }

    // 下载列表状态更新任务队列（限制每2秒允许更新一次，避免列表因为更新导致频繁闪烁与性能问题）
    private val mListUpdateTask = DebounceQueue<List<DownloadTaskWithVideoDetails>>(
        scope = lifecycleScope,
        delayMillis = 2000
    ) { tasks ->
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                mViewModel.updateList(tasks.toMutableList())
            }
        }
    }

    private val mAdapter: DownloadHistoryRVAdapter by lazy {
        DownloadHistoryRVAdapter(
            mViewModel, requireContext()
        )
    }

    private var mActionMode: ActionMode? = null

    private var mExportProgressDialog: AlertDialog? = null

    private val mExportDirLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri ?: return@registerForActivityResult
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        requireContext().contentResolver.takePersistableUriPermission(uri, flags)
        lifecycleScope.launch { mViewModel.executeBatchExport(uri) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Aria.download(this).register()
    }

    override fun onDestroy() {
        super.onDestroy()
        Aria.download(this).unRegister()
    }

    override fun initViews() {
        super.initViews()

        val states = arguments?.getSerializableByClass<Array<TaskStatus>>(
            KEY_STATES
        ) ?: arrayOf()

        mViewModel.init(states)
        initSmartRefreshLayout()
        observeMultiSelectState()
    }

    private fun HistoryViewModel.init(states: Array<TaskStatus>) {
        initData(states.toList())
        latestDownloadTaskLiveData.observe(this@DownloadHistoryFragment) {
            mListUpdateTask.schedule(it)
        }
    }

    private fun initSmartRefreshLayout() {
        setEnableRefresh(false)
        setEnableLoadMore(false)
    }

    private fun observeMultiSelectState() {
        mViewModel.multiSelectModeLiveData.observe(viewLifecycleOwner) { isMultiSelect ->
            if (isMultiSelect) {
                startActionMode()
            } else {
                mActionMode?.finish()
                mActionMode = null
            }
            mAdapter.updateMultiSelectState(
                isMultiSelect,
                mViewModel.selectedIdsLiveData.value ?: emptySet()
            )
        }

        mViewModel.selectedIdsLiveData.observe(viewLifecycleOwner) { selectedIds ->
            mActionMode?.title = CommonLibs.getString(
                R.string.multi_select_count, selectedIds.size
            )
            mAdapter.updateMultiSelectState(
                mViewModel.multiSelectModeLiveData.value == true,
                selectedIds
            )
        }

        mViewModel.batchExportProgressLiveData.observe(viewLifecycleOwner) { progress ->
            if (progress != null) {
                showExportProgressDialog(progress.current, progress.total)
            } else {
                dismissExportProgressDialog()
            }
        }
    }

    override fun onViewAction(action: ViewAction) {
        when (action) {
            is HistoryViewModel.Companion.RequestExportDirAction -> {
                mExportDirLauncher.launch(null)
            }
            else -> super.onViewAction(action)
        }
    }

    override fun getRVAdapter() = mAdapter

    @DownloadGroup.onTaskRunning
    fun handleTaskRunning(task: DownloadGroupTask) {
        val changeIndex = mViewModel.latestDownloadTaskLiveData.value?.indexOfFirst {
            it.downloadTask.groupId == task.entity.id
        }
        if (changeIndex == null || changeIndex == -1) return
        mAdapter.notifyItemChanged(changeIndex)
    }

    private fun startActionMode() {
        if (mActionMode != null) return
        mActionMode = requireActivity().startActionMode(object : ActionMode.Callback {
            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                mode.menuInflater.inflate(R.menu.menu_multi_select, menu)
                return true
            }

            override fun onPrepareActionMode(mode: ActionMode, menu: Menu) = false

            override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                return when (item.itemId) {
                    R.id.action_select_all -> {
                        mViewModel.selectAll()
                        true
                    }
                    R.id.action_export -> {
                        mViewModel.tryBatchExport()
                        true
                    }
                    R.id.action_delete -> {
                        showBatchDeleteConfirmDialog()
                        true
                    }
                    else -> false
                }
            }

            override fun onDestroyActionMode(mode: ActionMode) {
                mActionMode = null
                mViewModel.exitMultiSelectMode()
            }
        })
    }

    private fun showBatchDeleteConfirmDialog() {
        val count = mViewModel.getSelectedCount()
        if (count == 0) return
        mViewModel.popDialog(
            ConfirmDialog.buildDialog(
                CommonLibs.getString(R.string.text_delete_confirm),
                CommonLibs.getString(R.string.batch_delete_confirm_message, count),
                CommonLibs.getString(R.string.text_cancel),
                CommonLibs.getString(R.string.text_delete),
                rightButtonStyle = ConfirmDialog.Companion.ButtonStyle.Delete
            ),
            success = {
                if (it is Boolean && it) {
                    lifecycleScope.launch {
                        mViewModel.deleteSelectedTasks()
                    }
                }
            }
        )
    }

    private fun showExportProgressDialog(current: Int, total: Int) {
        val message = CommonLibs.getString(R.string.batch_export_progress_message, current, total)
        if (mExportProgressDialog == null) {
            mExportProgressDialog = AlertDialog.Builder(requireContext())
                .setTitle(CommonLibs.getString(R.string.batch_export_progress_title))
                .setMessage(message)
                .setCancelable(false)
                .create()
            mExportProgressDialog?.show()
        } else {
            mExportProgressDialog?.setMessage(message)
        }
    }

    private fun dismissExportProgressDialog() {
        mExportProgressDialog?.dismiss()
        mExportProgressDialog = null
    }
}