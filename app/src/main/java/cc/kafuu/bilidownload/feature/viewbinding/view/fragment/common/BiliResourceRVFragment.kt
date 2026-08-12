package cc.kafuu.bilidownload.feature.viewbinding.view.fragment.common

import android.annotation.SuppressLint
import android.view.View
import android.view.ViewStub
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import cc.kafuu.bilidownload.R
import cc.kafuu.bilidownload.common.CommonLibs
import cc.kafuu.bilidownload.common.adapter.BiliResourceRVAdapter
import cc.kafuu.bilidownload.common.core.viewbinding.CoreRVAdapter
import cc.kafuu.bilidownload.common.download.BatchDownloadResolver
import cc.kafuu.bilidownload.common.download.BatchDownloadUseCase
import cc.kafuu.bilidownload.common.model.ResultWrapper
import cc.kafuu.bilidownload.feature.viewbinding.view.dialog.BiliPartDialog
import cc.kafuu.bilidownload.feature.viewbinding.view.dialog.ConfirmDialog
import cc.kafuu.bilidownload.feature.viewbinding.viewmodel.common.BiliResourceRVViewModel
import com.scwang.smart.refresh.layout.api.RefreshLayout
import kotlinx.coroutines.launch

/** 提供可下载资源列表的适配器及多选操作栏。 */
open class BiliResourceRVFragment<VM : BiliResourceRVViewModel>(
    vmClass: Class<VM>
) : BiliRVFragment<VM>(vmClass) {
    private val mAdapter: BiliResourceRVAdapter by lazy {
        BiliResourceRVAdapter(mViewModel, requireContext())
    }

    override fun initViews() {
        super.initViews()
        initMultipleSelectViews()
        observeBatchDialogRequests()
    }

    override fun getRVAdapter(): CoreRVAdapter<*> = mAdapter

    override fun onRefresh(refreshLayout: RefreshLayout) {
        mViewModel.cancelMultipleSelect()
        super.onRefresh(refreshLayout)
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun initMultipleSelectViews() {
        val actionView = mViewDataBinding.root
            .findViewById<ViewStub>(R.id.multi_select_actions_stub)
            .inflate()
        val cancelView = actionView.findViewById<TextView>(R.id.tv_cancel_multi_select)
        val downloadView = actionView.findViewById<TextView>(R.id.tv_download_multi_select)

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
