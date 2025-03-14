package cc.kafuu.bilidownload.feature.viewbinding.view.fragment

import android.os.Bundle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import cc.kafuu.bilidownload.common.adapter.DownloadHistoryRVAdapter
import cc.kafuu.bilidownload.common.core.viewbinding.CoreFragmentBuilder
import cc.kafuu.bilidownload.common.ext.getSerializableByClass
import cc.kafuu.bilidownload.common.model.TaskStatus
import cc.kafuu.bilidownload.common.room.dto.DownloadTaskWithVideoDetails
import cc.kafuu.bilidownload.common.utils.DebounceQueue
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

    override fun getRVAdapter() = mAdapter

    /**
     * 监听下载任务执行进度，并绑定此任务是否存在于列表，若是存在则通知adapter更新*/
    @DownloadGroup.onTaskRunning
    fun handleTaskRunning(task: DownloadGroupTask) {
        // 查找
        val changeIndex = mViewModel.latestDownloadTaskLiveData.value?.indexOfFirst {
            it.downloadTask.groupId == task.entity.id
        }
        // 不存在在当前列表中
        if (changeIndex == null || changeIndex == -1) {
            return
        }
        // 通知更新
        mAdapter.notifyItemChanged(changeIndex)
    }

}