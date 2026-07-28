package cc.kafuu.bilidownload.feature.viewbinding.view.fragment.common

import android.annotation.SuppressLint
import android.view.View
import cc.kafuu.bilidownload.R
import cc.kafuu.bilidownload.common.adapter.BiliResourceRVAdapter
import cc.kafuu.bilidownload.common.CommonLibs
import cc.kafuu.bilidownload.common.core.viewbinding.CoreRVAdapter
import cc.kafuu.bilidownload.feature.viewbinding.viewmodel.common.BiliRVViewModel
import com.scwang.smart.refresh.layout.api.RefreshLayout
import com.scwang.smart.refresh.layout.listener.OnRefreshListener
import com.scwang.smart.refresh.layout.listener.OnRefreshLoadMoreListener

open class BiliRVFragment<VM : BiliRVViewModel>(
    vmClass: Class<VM>
) : RVFragment<VM>(vmClass), OnRefreshListener, OnRefreshLoadMoreListener {
    private val mAdapter: BiliResourceRVAdapter by lazy {
        BiliResourceRVAdapter(mViewModel, requireContext())
    }

    override fun initViews() {
        super.initViews()
        setOnRefreshListener(this)
        setOnRefreshLoadMoreListener(this)
        initMultipleSelectViews()
    }

    override fun getRVAdapter(): CoreRVAdapter<*>? = mAdapter

    override fun onRefresh(refreshLayout: RefreshLayout) {
        mViewModel.cancelMultipleSelect()
        mViewModel.onRefreshData(
            onSucceeded = { refreshLayout.finishRefresh(true) },
            onFailed = { refreshLayout.finishRefresh(false) }
        )
    }

    override fun onLoadMore(refreshLayout: RefreshLayout) {
        mViewModel.onLoadMoreData(
            onSucceeded = { refreshLayout.finishLoadMore(true) },
            onFailed = { refreshLayout.finishLoadMore(false) }
        )
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun initMultipleSelectViews() {
        mViewDataBinding.tvCancelMultiSelect.setOnClickListener {
            mViewModel.cancelMultipleSelect()
        }
        mViewDataBinding.tvDownloadMultiSelect.setOnClickListener {
            mViewModel.onDownloadMultipleSelectItems()
        }
        mViewModel.multipleSelectModeLiveData.observe(viewLifecycleOwner) { enabled ->
            mViewDataBinding.llMultiSelectActions.visibility =
                if (enabled) View.VISIBLE else View.GONE
            mViewDataBinding.rvContent.adapter?.notifyDataSetChanged()
        }
        mViewModel.multipleSelectItemsLiveData.observe(viewLifecycleOwner) { selected ->
            mViewDataBinding.tvDownloadMultiSelect.text = CommonLibs.getString(
                R.string.text_download_selected_count,
                selected.size
            )
            mViewDataBinding.rvContent.adapter?.notifyDataSetChanged()
        }
        mViewModel.batchDownloadRunningLiveData.observe(viewLifecycleOwner) { running ->
            mViewDataBinding.tvCancelMultiSelect.isEnabled = !running
            mViewDataBinding.tvDownloadMultiSelect.isEnabled = !running
            if (running) {
                mViewDataBinding.tvDownloadMultiSelect.setText(
                    R.string.text_batch_download_preparing
                )
            } else {
                val selectedCount = mViewModel.multipleSelectItemsLiveData.value.orEmpty().size
                mViewDataBinding.tvDownloadMultiSelect.text = CommonLibs.getString(
                    R.string.text_download_selected_count,
                    selectedCount
                )
            }
        }
    }
}
