package cc.kafuu.bilidownload.feature.viewbinding.view.fragment

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import cc.kafuu.bilidownload.R
import cc.kafuu.bilidownload.common.CommonLibs
import cc.kafuu.bilidownload.common.adapter.DownloadHistoryRVAdapter
import cc.kafuu.bilidownload.common.core.viewbinding.CoreFragmentBuilder
import cc.kafuu.bilidownload.common.ext.getSerializableByClass
import cc.kafuu.bilidownload.common.model.TaskStatus
import cc.kafuu.bilidownload.common.model.action.ViewAction
import cc.kafuu.bilidownload.common.model.event.DownloadStatusChangeEvent
import cc.kafuu.bilidownload.common.room.dto.DownloadTaskWithVideoDetails
import cc.kafuu.bilidownload.common.utils.DebounceQueue
import cc.kafuu.bilidownload.databinding.IncludeMultiSelectActionsBinding
import cc.kafuu.bilidownload.feature.viewbinding.view.dialog.ConfirmDialog
import cc.kafuu.bilidownload.feature.viewbinding.view.fragment.common.RVFragment
import cc.kafuu.bilidownload.feature.viewbinding.viewmodel.fragment.HistoryMultiSelectUiState
import cc.kafuu.bilidownload.feature.viewbinding.viewmodel.fragment.HistoryViewModel
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class DownloadHistoryFragment : RVFragment<HistoryViewModel>(HistoryViewModel::class.java) {
    companion object {
        private const val KEY_STATES = "states"
        private const val MULTI_SELECT_SHOW_DURATION_MS = 220L
        private const val MULTI_SELECT_HIDE_DURATION_MS = 180L

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
        mViewModel.updateHistoryList(tasks)
    }

    private var mAdapter: DownloadHistoryRVAdapter? = null
    private var mMultiSelectActionsBinding: IncludeMultiSelectActionsBinding? = null
    private var mShouldShowMultiSelectActions = false
    private var mExportProgressDialog: AlertDialog? = null

    private val mBackPressedCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            mViewModel.exitMultiSelectMode()
        }
    }

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
        EventBus.getDefault().register(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        EventBus.getDefault().unregister(this)
    }

    override fun onDestroyView() {
        mListUpdateTask.cancel()
        mShouldShowMultiSelectActions = false
        mBackPressedCallback.isEnabled = false
        mMultiSelectActionsBinding?.root?.animate()?.cancel()
        mMultiSelectActionsBinding = null
        mViewDataBinding.rvContent.adapter = null
        mAdapter = null
        dismissExportProgressDialog()
        super.onDestroyView()
    }

    override fun initViews() {
        super.initViews()

        val states = arguments?.getSerializableByClass<Array<TaskStatus>>(
            KEY_STATES
        ) ?: arrayOf()

        mViewModel.init(states)
        initSmartRefreshLayout()
        initMultiSelectViews()
        initBackPressHandler()
        observeMultiSelectState()
    }

    private fun initBackPressHandler() {
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            mBackPressedCallback
        )
    }

    private fun HistoryViewModel.init(states: Array<TaskStatus>) {
        initData(states.toList())
        latestDownloadTaskLiveData.observe(viewLifecycleOwner) {
            mListUpdateTask.schedule(it)
        }
    }

    private fun initSmartRefreshLayout() {
        setEnableRefresh(false)
        setEnableLoadMore(false)
    }

    private fun initMultiSelectViews() {
        val stubProxy = mViewDataBinding.multiSelectActionsStub
        stubProxy.viewStub?.inflate()
        val binding = (stubProxy.binding as IncludeMultiSelectActionsBinding).also {
            mMultiSelectActionsBinding = it
        }
        binding.layoutHistoryActions.visibility = View.VISIBLE

        binding.btnHistorySelectAll.setOnClickListener {
            view?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            mViewModel.toggleSelectAll()
        }

        binding.btnHistoryExport.setOnClickListener {
            view?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            mViewModel.tryBatchExport()
        }

        binding.btnHistoryDelete.setOnClickListener {
            view?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            showBatchDeleteConfirmDialog()
        }

        binding.btnHistoryClose.setOnClickListener {
            view?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            mViewModel.exitMultiSelectMode()
        }
    }

    private fun observeMultiSelectState() {
        mViewModel.multiSelectUiStateLiveData.observe(viewLifecycleOwner) { state ->
            val binding = mMultiSelectActionsBinding ?: return@observe
            mBackPressedCallback.isEnabled = state.isEnabled
            binding.tvHistorySelectedCount.text = CommonLibs.getString(
                R.string.multi_select_count,
                state.selectedIds.size,
            )
            binding.btnHistorySelectAll.text = if (state.isAllSelected) {
                CommonLibs.getString(R.string.text_deselect_all)
            } else {
                CommonLibs.getString(R.string.text_select_all)
            }
            binding.btnHistoryDelete.isEnabled = state.hasSelection
            binding.btnHistoryExport.isEnabled = state.hasSelection
            binding.btnHistoryDelete.alpha = if (state.hasSelection) 1f else 0.5f
            binding.btnHistoryExport.alpha = if (state.hasSelection) 1f else 0.5f
            renderMultiSelectActions(state)
            mAdapter?.updateMultiSelectState(
                state.isEnabled,
                state.selectedIds,
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

    private fun renderMultiSelectActions(state: HistoryMultiSelectUiState) {
        val actionView = mMultiSelectActionsBinding?.root ?: return
        val show = state.isEnabled
        mShouldShowMultiSelectActions = show
        actionView.animate().cancel()
        if (show) {
            if (actionView.visibility != View.VISIBLE) {
                actionView.alpha = 0f
                actionView.translationY = resources.getDimension(
                    R.dimen.multi_select_action_animation_offset
                )
                actionView.visibility = View.VISIBLE
            }
            actionView.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(MULTI_SELECT_SHOW_DURATION_MS)
                .start()
            return
        }

        if (actionView.visibility != View.VISIBLE) return
        actionView.animate()
            .alpha(0f)
            .translationY(resources.getDimension(R.dimen.multi_select_action_animation_offset))
            .setDuration(MULTI_SELECT_HIDE_DURATION_MS)
            .withEndAction {
                if (!mShouldShowMultiSelectActions &&
                    mMultiSelectActionsBinding?.root === actionView
                ) {
                    actionView.visibility = View.GONE
                }
            }
            .start()
    }

    override fun onViewAction(action: ViewAction) {
        when (action) {
            is HistoryViewModel.Companion.RequestExportDirAction -> {
                mExportDirLauncher.launch(null)
            }
            else -> super.onViewAction(action)
        }
    }

    override fun getRVAdapter() = mAdapter ?: DownloadHistoryRVAdapter(
        mViewModel,
        requireContext(),
    ).also { mAdapter = it }

    @Subscribe(threadMode = ThreadMode.POSTING)
    fun handleTaskRunning(event: DownloadStatusChangeEvent) {
        val changeIndex = mViewModel.latestDownloadTaskLiveData.value?.indexOfFirst {
            it.downloadTask.groupId == event.group.id
        }
        if (changeIndex == null || changeIndex == -1) return
        lifecycleScope.launch { mAdapter?.notifyItemChanged(changeIndex) }
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
