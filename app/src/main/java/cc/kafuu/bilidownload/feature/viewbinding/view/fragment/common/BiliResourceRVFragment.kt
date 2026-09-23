package cc.kafuu.bilidownload.feature.viewbinding.view.fragment.common

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import cc.kafuu.bilidownload.R
import cc.kafuu.bilidownload.common.CommonLibs
import cc.kafuu.bilidownload.common.adapter.BiliResourceRVAdapter
import cc.kafuu.bilidownload.common.core.viewbinding.CoreRVAdapter
import cc.kafuu.bilidownload.common.download.BatchDownloadResolver
import cc.kafuu.bilidownload.common.download.BatchDownloadUseCase
import cc.kafuu.bilidownload.common.manager.AccountManager
import cc.kafuu.bilidownload.common.model.ResultWrapper
import cc.kafuu.bilidownload.databinding.IncludeMultiSelectActionsBinding
import cc.kafuu.bilidownload.feature.viewbinding.view.dialog.BiliPartDialog
import cc.kafuu.bilidownload.feature.viewbinding.view.dialog.ConfirmDialog
import cc.kafuu.bilidownload.feature.viewbinding.viewmodel.common.BiliResourceRVViewModel
import com.scwang.smart.refresh.layout.api.RefreshLayout
import kotlinx.coroutines.launch

/** 提供可下载资源列表的适配器及多选操作栏。 */
open class BiliResourceRVFragment<VM : BiliResourceRVViewModel>(
    vmClass: Class<VM>
) : BiliRVFragment<VM>(vmClass) {
    private var mAdapter: BiliResourceRVAdapter? = null
    private val mLoadVisibleStats = Runnable { loadVisibleStats() }
    private val mStatsScrollListener = object : RecyclerView.OnScrollListener() {
        override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
            mViewModel.pauseVideoStatsRequests()
            scheduleVisibleStats()
        }

        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            scheduleVisibleStats()
        }
    }
    private val mStatsDataObserver = object : RecyclerView.AdapterDataObserver() {
        override fun onChanged() {
            mViewModel.pauseVideoStatsRequests()
            scheduleVisibleStats()
        }
    }

    override fun initViews() {
        super.initViews()
        initMultipleSelectViews()
        observeBatchDialogRequests()
    }

    override fun getRVAdapter(): CoreRVAdapter<*> = mAdapter
        ?: BiliResourceRVAdapter(mViewModel, requireContext()).also { mAdapter = it }

    /** 统计观察与滚动监听属于当前视图，不随 Fragment 的下一次视图创建复用。 */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mAdapter?.registerAdapterDataObserver(mStatsDataObserver)
        mViewDataBinding.rvContent.addOnScrollListener(mStatsScrollListener)
        mViewModel.videoStatsLiveData.observe(viewLifecycleOwner) { stats ->
            mAdapter?.updateVideoStats(stats)
        }
        AccountManager.cookiesLiveData.observe(viewLifecycleOwner) {
            mViewModel.clearVideoStats()
            scheduleVisibleStats()
        }
    }

    override fun onResume() {
        super.onResume()
        scheduleVisibleStats()
    }

    override fun onPause() {
        mViewDataBinding.rvContent.removeCallbacks(mLoadVisibleStats)
        mViewModel.pauseVideoStatsRequests()
        super.onPause()
    }

    /** 解除视图拥有的监听和 Adapter，避免旋转后旧列表继续触发补齐。 */
    override fun onDestroyView() {
        mViewDataBinding.rvContent.apply {
            removeCallbacks(mLoadVisibleStats)
            removeOnScrollListener(mStatsScrollListener)
            adapter = null
        }
        mAdapter?.unregisterAdapterDataObserver(mStatsDataObserver)
        mAdapter = null
        mViewModel.pauseVideoStatsRequests()
        super.onDestroyView()
    }

    /** 布局或滚动稳定后再取可见范围，快速滑动时不排队请求途经的每个视频。 */
    private fun scheduleVisibleStats() {
        val recyclerView = mViewDataBinding.rvContent
        recyclerView.removeCallbacks(mLoadVisibleStats)
        if (isResumed && recyclerView.scrollState == RecyclerView.SCROLL_STATE_IDLE) {
            recyclerView.postDelayed(mLoadVisibleStats, STATS_SCROLL_DELAY_MILLIS)
        }
    }

    private fun loadVisibleStats() {
        if (!isResumed) return
        val recyclerView = mViewDataBinding.rvContent
        val manager = recyclerView.layoutManager as? LinearLayoutManager ?: return
        val first = manager.findFirstVisibleItemPosition()
        val last = manager.findLastVisibleItemPosition()
        if (first == RecyclerView.NO_POSITION || last < first) return
        val videos = (first..last).mapNotNull { mAdapter?.getVideoAt(it) }
        mViewModel.loadVisibleVideoStats(videos)
    }

    private companion object {
        const val STATS_SCROLL_DELAY_MILLIS = 200L
    }

    override fun onRefresh(refreshLayout: RefreshLayout) {
        mViewModel.cancelMultipleSelect()
        super.onRefresh(refreshLayout)
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun initMultipleSelectViews() {
        val stubProxy = mViewDataBinding.multiSelectActionsStub
        stubProxy.viewStub?.inflate()
        val actionBinding = stubProxy.binding as IncludeMultiSelectActionsBinding
        actionBinding.layoutResourceActions.visibility = View.VISIBLE
        val actionView = actionBinding.root
        val cancelView = actionBinding.tvCancelMultiSelect
        val downloadView = actionBinding.tvDownloadMultiSelect

        cancelView.setOnClickListener {
            mViewModel.cancelMultipleSelect()
        }
        downloadView.setOnClickListener {
            mViewModel.onDownloadMultipleSelectItems()
        }
        mViewModel.multipleSelectModeLiveData.observe(viewLifecycleOwner) { enabled ->
            actionView.visibility = if (enabled) View.VISIBLE else View.GONE
            mViewDataBinding.rvContent.adapter?.notifyDataSetChanged()
        }
        mViewModel.multipleSelectItemsLiveData.observe(viewLifecycleOwner) { selected ->
            downloadView.text = CommonLibs.getString(
                R.string.text_download_selected_count,
                selected.size,
            )
            mViewDataBinding.rvContent.adapter?.notifyDataSetChanged()
        }
        mViewModel.batchDownloadRunningLiveData.observe(viewLifecycleOwner) { running ->
            cancelView.isEnabled = !running
            downloadView.isEnabled = !running
            if (running) {
                downloadView.setText(R.string.text_batch_download_preparing)
            } else {
                val selectedCount = mViewModel.multipleSelectItemsLiveData.value.orEmpty().size
                downloadView.text = CommonLibs.getString(
                    R.string.text_download_selected_count,
                    selectedCount,
                )
            }
        }
    }

    private fun observeBatchDialogRequests() {
        mViewModel.batchDialogRequestLiveData.observe(viewLifecycleOwner) { request ->
            request ?: return@observe
            // View 被销毁时等待协程自动取消；新 View 会从 ViewModel 重放同一请求。
            viewLifecycleOwner.lifecycleScope.launch {
                when (request) {
                    is BiliResourceRVViewModel.BatchDialogRequest.Scope ->
                        showDownloadScopeDialog(request)

                    is BiliResourceRVViewModel.BatchDialogRequest.Streams ->
                        showDownloadStreamsDialog(request)
                }
            }
        }
    }

    private suspend fun showDownloadScopeDialog(
        dialogRequest: BiliResourceRVViewModel.BatchDialogRequest.Scope,
    ) {
        val request = dialogRequest.request
        val message = CommonLibs.getString(
            R.string.text_batch_download_scope_message,
            request.sourceCount,
            request.totalPartCount,
            request.resolveFailureCount,
        )
        val result = ConfirmDialog.buildDialog(
            title = CommonLibs.getString(R.string.text_batch_download_scope_title),
            message = message,
            leftButtonText = CommonLibs.getString(R.string.text_download_default_part),
            rightButtonText = CommonLibs.getString(R.string.text_download_all_parts),
        ).showAndWaitResult(
            lifecycleOwner = this,
            dialogTag = "BatchDownloadScopeDialog_${dialogRequest.id}",
            waitWhenInvisible = true,
        )
        val scope = (result as? ResultWrapper.Success)?.value?.let { downloadAllParts ->
            if (downloadAllParts) {
                BatchDownloadUseCase.DownloadScope.ALL_PARTS
            } else {
                BatchDownloadUseCase.DownloadScope.PREFERRED_PART
            }
        }
        mViewModel.onDownloadScopeSelected(dialogRequest.id, scope)
    }

    private suspend fun showDownloadStreamsDialog(
        dialogRequest: BiliResourceRVViewModel.BatchDialogRequest.Streams,
    ) {
        val request = dialogRequest.request
        val result = BiliPartDialog.buildDialog(
            request.partTitle
                ?: CommonLibs.getString(R.string.text_select_the_resource_to_download),
            request.dash.video,
            request.dash.getAllAudio(),
        ).showAndWaitResult(
            lifecycleOwner = this,
            dialogTag = "BatchDownloadStreamsDialog_${dialogRequest.id}",
            waitWhenInvisible = true,
        )
        val streams = (result as? ResultWrapper.Success)?.value?.let { selection ->
            BatchDownloadResolver.StreamSelection(
                selection.videoStream,
                selection.audioStream,
            )
        }
        mViewModel.onDownloadStreamsSelected(dialogRequest.id, streams)
    }
}
