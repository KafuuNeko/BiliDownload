package cc.kafuu.bilidownload.feature.viewbinding.view.fragment.common

import android.annotation.SuppressLint
import android.view.View
import android.view.ViewStub
import android.widget.TextView
import cc.kafuu.bilidownload.R
import cc.kafuu.bilidownload.common.CommonLibs
import cc.kafuu.bilidownload.common.adapter.BiliResourceRVAdapter
import cc.kafuu.bilidownload.common.core.viewbinding.CoreRVAdapter
import cc.kafuu.bilidownload.feature.viewbinding.viewmodel.common.BiliResourceRVViewModel
import com.scwang.smart.refresh.layout.api.RefreshLayout

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
}
