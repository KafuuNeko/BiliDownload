package cc.kafuu.bilidownload.view.fragment

import androidx.recyclerview.widget.RecyclerView
import cc.kafuu.bilidownload.common.utils.CommonLibs
import cc.kafuu.bilidownload.viewmodel.fragment.HistoryViewModel

class HistoryFragment : RVFragment<HistoryViewModel>(HistoryViewModel::class.java) {

    override fun initViews() {
        super.initViews()

    }

    override fun getRVAdapter(): RecyclerView.Adapter<*>? = null

    override fun getRVLayoutManager(): RecyclerView.LayoutManager? = null

    companion object {
        @JvmStatic
        fun newInstance() = MeFragment().apply {
        }
    }
}