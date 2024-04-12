package cc.kafuu.bilidownload.view.fragment

import android.os.Bundle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import cc.kafuu.bilidownload.R
import cc.kafuu.bilidownload.common.adapter.HistoryRVAdapter
import cc.kafuu.bilidownload.common.utils.CommonLibs
import cc.kafuu.bilidownload.model.LoadingMessage
import cc.kafuu.bilidownload.viewmodel.fragment.HistoryViewModel
import com.arialyy.annotations.DownloadGroup
import com.arialyy.aria.core.Aria
import com.arialyy.aria.core.task.DownloadGroupTask

class HistoryFragment : RVFragment<HistoryViewModel>(HistoryViewModel::class.java) {
    companion object {
        private val MESSAGE_GONE = LoadingMessage()
        private val MESSAGE_LIST_EMPTY = LoadingMessage(
            true,
            CommonLibs.getDrawable(R.drawable.ic_list_item_empty),
            CommonLibs.getString(R.string.list_is_empty)
        )

        @JvmStatic
        fun newInstance(vararg statuses: Int) = HistoryFragment().apply {
            arguments = Bundle().apply {
                putIntArray("statuses", statuses)
            }
        }
    }

    private lateinit var mStatuses: IntArray
    private val mAdapter: HistoryRVAdapter by lazy {
        HistoryRVAdapter(
            mViewModel, requireContext()
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mStatuses = arguments?.getIntArray("statuses") ?: intArrayOf()
        Aria.download(this).register()
    }

    override fun onDestroy() {
        super.onDestroy()
        Aria.download(this).unRegister()
    }

    override fun initViews() {
        super.initViews()
        initViewMode()
        initSmartRefreshLayout()
    }

    private fun initViewMode() {
        mViewModel.initData(*mStatuses)
        mViewModel.latestDownloadTaskLiveData.observe(this) {
            mViewModel.listMutableLiveData.value = it.toMutableList()
            mViewModel.loadingMessageMutableLiveData.value = if (it.isEmpty()) MESSAGE_LIST_EMPTY else MESSAGE_GONE
        }
    }

    private fun initSmartRefreshLayout() {
        setEnableRefresh(false)
        setEnableLoadMore(false)
    }

    override fun getRVAdapter() = mAdapter

    override fun getRVLayoutManager(): RecyclerView.LayoutManager = LinearLayoutManager(context)

    /**
     * 监听下载任务执行进度，并绑定此任务是否存在于列表，若是存在则通知adapter更新*/
    @DownloadGroup.onTaskRunning
    fun handleTaskRunning(task: DownloadGroupTask) {
        // 查找
        val changeIndex = mViewModel.latestDownloadTaskLiveData.value?.indexOfFirst {
            it.downloadTask.id == task.entity.id
        }
        // 不存在在当前列表中
        if (changeIndex == null || changeIndex == -1) {
            return
        }
        // 通知更新
        mAdapter.notifyItemChanged(changeIndex)
    }

}