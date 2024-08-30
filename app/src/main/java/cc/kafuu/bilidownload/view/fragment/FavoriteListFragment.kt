package cc.kafuu.bilidownload.view.fragment

import androidx.recyclerview.widget.LinearLayoutManager
import cc.kafuu.bilidownload.common.adapter.BiliFavoriteRVAdapter
import cc.kafuu.bilidownload.common.core.CoreFragmentBuilder
import cc.kafuu.bilidownload.common.model.LoadingStatus
import cc.kafuu.bilidownload.viewmodel.fragment.FavoriteListViewModel
import com.scwang.smart.refresh.layout.api.RefreshLayout
import com.scwang.smart.refresh.layout.listener.OnRefreshListener

class FavoriteListFragment : RVFragment<FavoriteListViewModel>(FavoriteListViewModel::class.java),
    OnRefreshListener {
    companion object {
        const val KEY_MID = "mid"

        class Builder(val mid: Long) : CoreFragmentBuilder<FavoriteListFragment>() {
            override fun onMallocFragment() = FavoriteListFragment()
            override fun onPreparationArguments() {
                putArgument(KEY_MID, mid)
            }
        }

        @JvmStatic
        fun builder(mid: Long) = Builder(mid)
    }

    private val mAdapter: BiliFavoriteRVAdapter by lazy {
        BiliFavoriteRVAdapter(mViewModel, requireContext())
    }

    override fun getRVAdapter() = mAdapter

    override fun getRVLayoutManager() = LinearLayoutManager(context)

    override fun initViews() {
        super.initViews()

        setEnableRefresh(true)
        setEnableLoadMore(false)
        setOnRefreshListener(this)

        mViewModel.init(arguments?.getLong(KEY_MID) ?: 0)
    }

    private fun FavoriteListViewModel.init(mid: Long) {
        initData(mid)
        loadData(loadingStatus = LoadingStatus.loadingStatus())
    }

    override fun onRefresh(refreshLayout: RefreshLayout) {
        val loadingStatus = LoadingStatus.loadingStatus(false)
        mViewModel.loadData(loadingStatus = loadingStatus,
            onSucceeded = { refreshLayout.finishRefresh(true) },
            onFailed = { refreshLayout.finishRefresh(false) }
        )
    }

}